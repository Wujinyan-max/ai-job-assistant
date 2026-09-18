package com.jobassistant.service.impl;

import com.jobassistant.vo.ResumeStyleVO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 从用户导入的 PDF 简历里提取版式与配色，供「原版复刻」模板还原外观。
 *
 * <p>刻意不用 PDF 字符流里的颜色：那份颜色经过色彩空间转换后并不可靠——实测一份红色
 * （#e60000）主题的简历，字符流读到的是 #e6e6e6，色相直接丢了。真正靠谱的是把页
 * 渲染成位图再统计像素，代价只是一点 CPU，而且不需要任何新依赖。</p>
 *
 * <p>所有提取都做了容错：取不到就返回 null，让前端退回模板默认值，绝不因为样式识别失败
 * 而让整个导入流程报错。</p>
 */
public final class ResumeStyleExtractor {

    /**
     * 渲染精度。
     * <p>不能取 72：小字号文字在低精度下笔画只有一两个像素宽，整条笔画几乎全是
     * 「本色 + 白底」的过渡色，取色会偏（实测 #e60000 会被取成 #e60404）。
     * 提到 150 之后笔画内部有足够的纯色像素，取色才准。</p>
     */
    private static final float RENDER_DPI = 150f;
    /** 最多分析前五页，覆盖常见简历并避免异常大文件拖慢导入。 */
    private static final int ANALYZE_PAGES = 5;
    /** 饱和度下限：低于它的算灰阶，不参与强调色统计 */
    private static final int MIN_SATURATION = 40;
    /** 深色判定阈值 */
    private static final int DARK_THRESHOLD = 100;
    /** 头像最小边长（pt），太小的是图标或二维码 */
    private static final float MIN_AVATAR_PT = 40f;
    private static final float MAX_AVATAR_PT = 160f;

    private ResumeStyleExtractor() {
    }

    /** 提取结果：版式 + 头像（data URL），头像可能为 null */
    public record Extracted(ResumeStyleVO style, String avatarDataUrl) {
    }

    /**
     * 解析 PDF 字节流，得到版式描述与头像。
     * 任何一步失败都不抛异常，只降级返回已有结果。
     */
    public static Extracted extract(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return new Extracted(ResumeStyleVO.empty(), null);
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                return new Extracted(ResumeStyleVO.empty(), null);
            }
            PDPage firstPage = document.getPage(0);
            float pageWidth = firstPage.getMediaBox().getWidth();
            float pageHeight = firstPage.getMediaBox().getHeight();

            List<BufferedImage> images = renderPages(document);
            if (images.isEmpty()) {
                return new Extracted(ResumeStyleVO.empty(), null);
            }

            BufferedImage first = images.get(0);
            TextFacts text = collectText(document, pageWidth, pageHeight);
            ImageBox avatar = findAvatar(document, text);

            ResumeStyleVO style = buildStyle(images, text, avatar, pageWidth, pageHeight);
            String avatarDataUrl = avatar == null ? null : toDataUrl(document, avatar);
            return new Extracted(style, avatarDataUrl);
        } catch (Exception e) {
            // 样式只是锦上添花，识别失败不该影响内容导入
            return new Extracted(ResumeStyleVO.empty(), null);
        }
    }

    // ------------------------------------------------------------------ 渲染

    private static List<BufferedImage> renderPages(PDDocument document) {
        List<BufferedImage> images = new ArrayList<>();
        PDFRenderer renderer = new PDFRenderer(document);
        int pages = Math.min(ANALYZE_PAGES, document.getNumberOfPages());
        for (int i = 0; i < pages; i++) {
            try {
                images.add(renderer.renderImageWithDPI(i, RENDER_DPI));
            } catch (IOException ignored) {
                // 单页渲染失败就跳过这一页
            }
        }
        return images;
    }

    // ------------------------------------------------------------ 文本线条信息

    /** 从字符流里收集「位置 + 字号」，颜色不从这里取 */
    private record TextSpan(int page, float x0, float x1, float top, float bottom, float size, String text) {
    }

    private record TextFacts(List<TextSpan> spans, float maxFontSize, float bodyFontSize,
                             float pageWidth, float pageHeight) {

        /** 正文区域的左边界，用于推算页边距 */
        float marginLeftPt() {
            float left = Float.MAX_VALUE;
            for (TextSpan span : spans) {
                // 只在正文区取样，避开页眉里居中排布的联系方式
                if (span.size() <= bodyFontSize + 1.5f) {
                    left = Math.min(left, span.x0);
                }
            }
            return left == Float.MAX_VALUE ? 0f : left;
        }

        /** 页面里最大的文本块的垂直范围（姓名），用于判断页眉高度 */
        float nameBottom() {
            for (TextSpan span : spans) {
                if (span.size() >= maxFontSize - 0.6f) {
                    return span.bottom();
                }
            }
            return 0f;
        }
    }

    private static TextFacts collectText(PDDocument document, float pageWidth, float pageHeight) {
        Collector collector;
        try {
            collector = new Collector(pageHeight);
        } catch (IOException e) {
            // 连提取器都建不起来时按「没有文本信息」处理
            return new TextFacts(List.of(), 0f, 0f, pageWidth, pageHeight);
        }
        try {
            collector.setStartPage(1);
            collector.setEndPage(Math.min(ANALYZE_PAGES, document.getNumberOfPages()));
            collector.setSortByPosition(true);
            collector.getText(document);
        } catch (IOException ignored) {
            // 拿不到文本信息时后面的推算会走默认值
        }
        List<TextSpan> spans = collector.spans;
        float maxFont = 0f;
        Map<Float, Integer> sizeHistogram = new HashMap<>();
        for (TextSpan span : spans) {
            maxFont = Math.max(maxFont, span.size());
            // 按 0.5pt 量化，避免浮点误差把同一字号拆成好几档
            float bucket = Math.round(span.size() * 2f) / 2f;
            sizeHistogram.merge(bucket, span.text().length(), Integer::sum);
        }
        // 正文字号 = 出现字符数最多的那一档
        float bodyFont = sizeHistogram.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
        return new TextFacts(spans, maxFont, bodyFont, pageWidth, pageHeight);
    }

    /** PDF 坐标原点在左下角，这里统一翻成「从上往下」的好用坐标系 */
    private static final class Collector extends PDFTextStripper {

        private final List<TextSpan> spans = new ArrayList<>();
        private final float pageHeight;

        Collector(float pageHeight) throws IOException {
            this.pageHeight = pageHeight;
        }

        /**
         * 取「视觉字号」，即这个字在页面上最终显示多大。
         *
         * <p>不能用 {@code getFontSizeInPt()}：它返回的是 Tf 操作符里的「名义字号」，没算上内容流缩放。
         * 实测一份简历 Tf 值是 13pt，但带 0.75 的缩放，屏幕上和打印出来只有 9.75pt；
         * 采信 13pt 会让复刻出来的正文比原文大一圈。</p>
         *
         * <p>文本矩阵里已经含了「Tf × 缩放」的乘积（合成 PDF：Tf=12 → 矩阵缩放 12；
         * 真实简历：Tf=13、缩放 0.75 → 矩阵缩放 9.75），所以矩阵缩放值本身就是视觉字号，
         * 不要再乘一次 {@code getFontSize()}，否则会算出 126.75pt 这种离谱结果。</p>
         *
         * <p>PDF 用户单位就是 1/72 英寸，所以矩阵缩放值可直接当 pt 用。</p>
         */
        private static float visualFontSize(TextPosition position) {
            Matrix matrix = position.getTextMatrix();
            double scale = Math.hypot(matrix.getScaleX(), matrix.getShearY());
            if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
                return position.getFontSizeInPt();
            }
            return (float) scale;
        }

        @Override
        protected void processTextPosition(TextPosition position) {
            super.processTextPosition(position);
            String text = position.getUnicode();
            if (text == null || text.isBlank()) {
                return;
            }
            float x0 = position.getXDirAdj();
            float x1 = x0 + position.getWidthDirAdj();
            // getYDirAdj 已经是「从页面顶部往下」的距离
            float top = position.getYDirAdj() - position.getHeightDir();
            float bottom = position.getYDirAdj();
            spans.add(new TextSpan(getCurrentPageNo() - 1, x0, x1, top, bottom,
                    visualFontSize(position), text.trim()));
        }
    }

    // ------------------------------------------------------------------ 头像

    // ------------------------------------------------------------ 颜色与版式

    /** 从渲染图里统计出的颜色分布 */
    private record Palette(String accent, String heading, String body, String meta,
                           String bandColor, String bandText) {
    }

    /** 左侧分区轨道：offsetMm 是轨道中心相对正文左边界向左的距离。 */
    private record Rail(boolean present, double offsetMm) {
        static Rail none() {
            return new Rail(false, 0d);
        }
    }

    private static ResumeStyleVO buildStyle(List<BufferedImage> images, TextFacts text, ImageBox avatar,
                                            float pageWidth, float pageHeight) {
        BufferedImage first = images.get(0);
        Palette palette = analyzeColors(first, avatar);

        // 页眉通栏：从顶部往下扫，多数像素是深色的连续行就算通栏
        int bandBottom = headerBandBottom(first);
        boolean headerBand = bandBottom > 0 && text.maxFontSize() > 0
                && bandBottom >= text.nameBottom();
        double headerRatio = headerBand ? (double) bandBottom / first.getHeight() : 0d;

        // 头像位置：水平居中偏上，还是靠左
        String avatarPosition = ResumeStyleVO.POS_NONE;
        double avatarSizeMm = 0d;
        if (avatar != null) {
            float center = avatar.left() + avatar.widthPt() / 2f;
            boolean centered = Math.abs(center - pageWidth / 2f) < pageWidth * 0.08f;
            avatarPosition = centered ? ResumeStyleVO.POS_CENTER_TOP : ResumeStyleVO.POS_LEFT_TOP;
            avatarSizeMm = round1(avatar.widthPt() / 72d * 25.4d);
        }

        // 页边距：正文左边界换算成毫米
        double marginMm = text.marginLeftPt() > 0 ? round1(text.marginLeftPt() / 72d * 25.4d) : 0d;
        Rail rail = detectSectionRail(first, text, bandBottom);
        List<String> accentTerms = extractAccentTerms(images, text, palette.accent());

        return new ResumeStyleVO(
                palette.accent(),
                palette.heading(),
                palette.body(),
                palette.meta(),
                headerBand,
                palette.bandColor(),
                palette.bandText(),
                round3(headerRatio),
                avatarPosition,
                avatarSizeMm,
                ResumeStyleVO.SHAPE_SQUARE,
                rail.present(),
                rail.present(),
                rail.offsetMm(),
                rail.present() ? 1 : 2,
                accentTerms,
                marginMm,
                round1(text.bodyFontSize()),
                null);
    }

    /**
     * 检测正文左侧是否存在贯穿多段内容的深色竖线。文字笔画只在少量行出现，
     * 而轨道会覆盖页面高度的大半，因此按列统计深色像素能稳定区分两者。
     */
    private static Rail detectSectionRail(BufferedImage image, TextFacts text, int bandBottom) {
        if (text.marginLeftPt() <= 0 || text.pageWidth() <= 0) {
            return Rail.none();
        }
        double scale = image.getWidth() / text.pageWidth();
        int searchRight = Math.min((int) (text.marginLeftPt() * scale - 2), image.getWidth() / 4);
        int fromY = Math.max(bandBottom + 4, image.getHeight() / 30);
        int toY = image.getHeight() - 5;
        if (searchRight <= 8 || toY <= fromY) {
            return Rail.none();
        }

        int bestX = -1;
        int bestDark = 0;
        int samples = Math.max(1, (toY - fromY) / 2);
        for (int x = 6; x <= searchRight; x++) {
            int dark = 0;
            for (int y = fromY; y < toY; y += 2) {
                if (isDark(image.getRGB(x, y))) dark++;
            }
            if (dark > bestDark) {
                bestDark = dark;
                bestX = x;
            }
        }
        if (bestX < 0 || bestDark < samples * 0.42d) {
            return Rail.none();
        }
        double railPt = bestX / scale;
        double offsetMm = (text.marginLeftPt() - railPt) / 72d * 25.4d;
        return offsetMm > 1d ? new Rail(true, round1(offsetMm)) : Rail.none();
    }

    /** 将强调色像素映射回文字坐标，只保存真正着色的词组，而不是把整行染色。 */
    private static List<String> extractAccentTerms(List<BufferedImage> images, TextFacts text, String accent) {
        int[] target = parseHex(accent);
        if (target == null || images.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        StringBuilder current = new StringBuilder();
        TextSpan previous = null;
        for (TextSpan span : text.spans()) {
            boolean highlighted = span.page() >= 0 && span.page() < images.size()
                    && hasAccentPixel(images.get(span.page()), text.pageWidth(), text.pageHeight(), span, target);
            if (!highlighted) {
                addAccentTerm(terms, current);
                previous = null;
                continue;
            }

            boolean sameRun = previous != null
                    && previous.page() == span.page()
                    && Math.abs(previous.top() - span.top()) <= Math.max(1.5f, span.size() * 0.25f)
                    && span.x0() - previous.x1() <= Math.max(3f, span.size() * 1.2f);
            if (!sameRun) {
                addAccentTerm(terms, current);
            } else if (span.x0() - previous.x1() > span.size() * 0.25f) {
                current.append(' ');
            }
            current.append(span.text());
            previous = span;
        }
        addAccentTerm(terms, current);
        return terms.stream().limit(120).toList();
    }

    private static void addAccentTerm(LinkedHashSet<String> terms, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isEmpty()) terms.add(value);
        current.setLength(0);
    }

    private static boolean hasAccentPixel(BufferedImage image, float pageWidth, float pageHeight,
                                         TextSpan span, int[] target) {
        if (pageWidth <= 0 || pageHeight <= 0) return false;
        double scaleX = image.getWidth() / pageWidth;
        double scaleY = image.getHeight() / (double) pageHeight;
        // 取字形内缩一格：字符框的右/下边界正好压在下一个字的首像素上，
        // 不内缩会把邻字的强调色算成本字着色，实测会产生「责」这类假阳性。
        int left = Math.max(0, (int) Math.floor(span.x0() * scaleX));
        int right = Math.min(image.getWidth() - 1, (int) Math.ceil(span.x1() * scaleX) - 1);
        int top = Math.max(0, (int) Math.floor(span.top() * scaleY));
        int bottom = Math.min(image.getHeight() - 1, (int) Math.ceil(span.bottom() * scaleY) - 1);
        if (right < left || bottom < top) return false;
        int maxDistance = 75 * 75;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int dr = r - target[0];
                int dg = g - target[1];
                int db = b - target[2];
                if (dr * dr + dg * dg + db * db <= maxDistance
                        && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) >= MIN_SATURATION) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[] parseHex(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) return null;
        int rgb = Integer.parseInt(color.substring(1), 16);
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }

    /** 找出页眉通栏的下边界（像素），没有通栏返回 0 */
    private static int headerBandBottom(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();
        int limit = Math.min(height, (int) (height * 0.45));
        int lastDarkRow = 0;
        for (int y = 0; y < limit; y++) {
            int dark = 0;
            int samples = 0;
            // 横向抽样即可，不用逐像素
            for (int x = 0; x < width; x += 4) {
                int rgb = image.getRGB(x, y);
                if (isDark(rgb)) {
                    dark++;
                }
                samples++;
            }
            if (samples > 0 && dark > samples * 0.85) {
                lastDarkRow = y;
            } else if (lastDarkRow > 0 && y - lastDarkRow > height * 0.02) {
                // 出现明显的非深色带就认为通栏结束
                break;
            }
        }
        // 太薄的“深色行”多半是分隔线，不算通栏
        return lastDarkRow > height * 0.05 ? lastDarkRow : 0;
    }

    /**
     * 统计颜色：强调色取出现最多的彩色簇，其余按明度分配。
     *
     * <p>两个坑必须避开，否则取到的颜色会明显偏色：</p>
     * <ol>
     *   <li>小字号文字的抗锯齿像素非常多，它们是「本色与白底混合」的结果，越靠边越浅。
     *       如果只按出现次数取众数，浅粉色边缘可能反超本色，所以先把接近白色的像素排除掉。</li>
     *   <li>头像照片天然带肤色等彩色，会污染强调色统计，所以统计时把头像区域挖掉。</li>
     * </ol>
     */
    private static Palette analyzeColors(BufferedImage image, ImageBox avatar) {
        List<int[]> colorful = new ArrayList<>();
        Map<Integer, Integer> grays = new HashMap<>();
        int width = image.getWidth();
        int height = image.getHeight();
        // 前景色统计跳过页眉区，避免深色通栏压过正文的颜色分布
        int startY = headerBandBottom(image);

        for (int y = startY; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (inImage(avatar, x, y)) {
                    continue;
                }
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int saturation = max - min;
                if (saturation >= MIN_SATURATION) {
                    colorful.add(new int[]{r, g, b, saturation});
                } else if (max < 220 && saturation <= 12) {
                    grays.merge(rgb, 1, Integer::sum);
                }
            }
        }

        String accent = dominantColor(colorful);
        List<Map.Entry<Integer, Integer>> grayList = grays.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .toList();
        String heading = pickGray(grayList, 0, 110);
        String body = pickGray(grayList, 110, 150);
        String meta = pickGray(grayList, 150, 210);
        return new Palette(accent, heading, body, meta, null, null);
    }

    /** 是否落在头像所占的像素范围内 */
    private static boolean inImage(ImageBox box, int x, int y) {
        if (box == null) {
            return false;
        }
        // 渲染精度固定为 RENDER_DPI，这里把 pt 换算成像素
        double scale = RENDER_DPI / 72d;
        int left = (int) Math.floor(box.left() * scale);
        int top = (int) Math.floor(box.top() * scale);
        int right = (int) Math.ceil((box.left() + box.widthPt()) * scale);
        int bottom = (int) Math.ceil((box.top() + box.heightPt()) * scale);
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    /**
     * 从彩色像素里定出强调色。
     *
     * <p>小字号文字的一个笔画只有一两个像素宽，渲染出来绝大多数是「本色 + 白底」的过渡色，
     * 例如 #e60000 的红色文字，边缘会散落一大片 #f8b8b8 这样的浅粉。直接取众数会取到浅粉，
     * 取「出现最多的色相」也会因为过渡色分属多个色相而摇摆。真正稳定的判据是饱和度：
     * 把本色与白色按比例 t 混合，饱和度会随 t 单调下降，所以只有本色像素最饱和。</p>
     *
     * <p>因此这里先找出全图的最高饱和度，再把「饱和度正好等于峰值」的像素取众数。
     * 用它而不是取平均：平均会把紧挨着的过渡色一起算进去，实测会得到 #e70b0b 这种偏色值，
     * 而众数能原样还原出 #e60000。</p>
     */
    private static String dominantColor(List<int[]> colorful) {
        if (colorful.size() < 40) {
            return null;
        }
        int peak = 0;
        for (int[] pixel : colorful) {
            peak = Math.max(peak, pixel[3]);
        }
        if (peak < MIN_SATURATION) {
            return null;
        }
        Map<Integer, Integer> exact = new HashMap<>();
        for (int[] pixel : colorful) {
            if (pixel[3] == peak) {
                exact.merge(pixel[0] << 16 | pixel[1] << 8 | pixel[2], 1, Integer::sum);
            }
        }
        int bestKey = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : exact.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestKey = entry.getKey();
            }
        }
        if (bestKey < 0) {
            return null;
        }
        return String.format("#%06x", bestKey);
    }

    /** 在给定明度区间里找出现最多的灰阶 */
    private static String pickGray(List<Map.Entry<Integer, Integer>> grays, int min, int max) {
        for (Map.Entry<Integer, Integer> entry : grays) {
            int rgb = entry.getKey();
            int value = rgb & 0xFF;
            if (value >= min && value < max) {
                return String.format("#%02x%02x%02x", value, value, value);
            }
        }
        return null;
    }

    private static boolean isDark(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Math.max(r, Math.max(g, b)) < DARK_THRESHOLD;
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private record ImageBox(int xref, float left, float top, float widthPt, float heightPt) {
    }

    /** 找出首页里最像«头像»的图片：近方形、尺寸适中、靠上 */
    private static ImageBox findAvatar(PDDocument document, TextFacts text) {
        List<ImageBox> candidates = new ArrayList<>();
        for (int i = 0; i < Math.min(ANALYZE_PAGES, document.getNumberOfPages()); i++) {
            ImageScanner scanner = new ImageScanner(document.getPage(i));
            try {
                scanner.processPage(document.getPage(i));
            } catch (IOException ignored) {
                continue;
            }
            candidates.addAll(scanner.images);
        }

        ImageBox best = null;
        for (ImageBox box : candidates) {
            float w = box.widthPt();
            float h = box.heightPt();
            if (w < MIN_AVATAR_PT || h < MIN_AVATAR_PT || w > MAX_AVATAR_PT || h > MAX_AVATAR_PT) {
                continue;
            }
            // 近方形：长宽比落在 0.7 ~ 1.4
            float ratio = w / h;
            if (ratio < 0.7f || ratio > 1.4f) {
                continue;
            }
            // 头像一般在上半页；姓名下方一点的位置也算（有些模板头像在名字旁边）
            if (box.top() > text.pageWidth() * 2f) {
                continue;
            }
            if (best == null || w * h > best.widthPt() * best.heightPt()) {
                best = box;
            }
        }
        return best;
    }

    /** 捕获图片的绘制矩阵，换算成页面坐标 */
    private static final class ImageScanner extends PDFGraphicsStreamEngine {

        private final List<ImageBox> images = new ArrayList<>();
        private final float pageHeight;

        ImageScanner(PDPage page) {
            super(page);
            this.pageHeight = page.getMediaBox().getHeight();
        }

        @Override
        public void drawImage(PDImage pdImage) {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Point2D.Float origin = new Point2D.Float(0, 0);
            Point2D.Float corner = new Point2D.Float(1, 1);
            ctm.transform(origin);
            ctm.transform(corner);
            float left = Math.min(origin.x, corner.x);
            float right = Math.max(origin.x, corner.x);
            // PDF 原点在左下，翻成从上往下
            float top = pageHeight - Math.max(origin.y, corner.y);
            int xref = pdImage instanceof PDImageXObject xobj ? xobj.getCOSObject().hashCode() : 0;
            images.add(new ImageBox(xref, left, top, right - left, Math.abs(corner.y - origin.y)));
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }
    }

    /** 头像转成 data URL 落库，省得再引一套文件存储 */
    private static String toDataUrl(PDDocument document, ImageBox box) {
        try {
            BufferedImage image = renderAvatar(document, box);
            if (image == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** 按头像的页面坐标裁剪首页，比直接取原图更稳（原图可能带白边或占用整页） */
    private static BufferedImage renderAvatar(PDDocument document, ImageBox box) {
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage page = renderer.renderImageWithDPI(0, 150f);
            float scale = 150f / 72f;
            int x = Math.max(0, Math.round(box.left() * scale));
            int y = Math.max(0, Math.round(box.top() * scale));
            int w = Math.min(page.getWidth() - x, Math.round(box.widthPt() * scale));
            int h = Math.min(page.getHeight() - y, Math.round(box.heightPt() * scale));
            if (w <= 0 || h <= 0) {
                return null;
            }
            return page.getSubimage(x, y, w, h);
        } catch (Exception e) {
            return null;
        }
    }
}
