package com.jobassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.vo.ResumeStyleVO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「还原导入简历的版式」靠这里的提取结果，用一份合成的 PDF 把行为固定下来。
 * <p>不用真实简历做测试样本：那是用户的隐私数据，而且合成样本才能精确控制颜色和版式。</p>
 */
class ResumeStyleExtractorTest {

    private static final Color BAND = new Color(0x33, 0x33, 0x33);
    private static final Color ACCENT = new Color(0xE6, 0x00, 0x00);
    private static final Color BODY = new Color(0x55, 0x55, 0x55);

    @Test
    @DisplayName("提取版式：深色通栏、居中头像、红色强调色都能识别出来")
    void extractFullStyleFromSyntheticResume() throws Exception {
        byte[] pdf = syntheticResume(true, true);

        ResumeStyleExtractor.Extracted extracted = ResumeStyleExtractor.extract(pdf);
        ResumeStyleVO style = extracted.style();

        // 强调色必须还原成原来的红色，而不是色彩空间转换后的浅灰
        assertThat(style.accentColor()).isEqualTo("#e60000");
        assertThat(style.headerBand()).isTrue();
        assertThat(style.headerHeightRatio()).isBetween(0.05d, 0.6d);
        assertThat(style.avatarPosition()).isEqualTo(ResumeStyleVO.POS_CENTER_TOP);
        assertThat(style.avatarSizeMm()).isBetween(10d, 40d);
        assertThat(style.marginMm()).isBetween(5d, 30d);
        assertThat(style.baseFontSizePt()).isGreaterThan(0d);

        // 头像要能取到图上，并且是可直接塞进 img src 的 data URL
        assertThat(extracted.avatarDataUrl()).startsWith("data:image/png;base64,");
        assertThat(extracted.avatarDataUrl().length()).isGreaterThan(200);
    }

    @Test
    @DisplayName("没有头像、没有通栏的朴素简历：不该硬编出这些特征")
    void plainResumeHasNoBandAndNoAvatar() throws Exception {
        byte[] pdf = syntheticResume(false, false);

        ResumeStyleVO style = ResumeStyleExtractor.extract(pdf).style();

        assertThat(style.headerBand()).isFalse();
        assertThat(style.headerHeightRatio()).isZero();
        assertThat(style.avatarPosition()).isEqualTo(ResumeStyleVO.POS_NONE);
        assertThat(style.avatarSizeMm()).isZero();
        assertThat(ResumeStyleExtractor.extract(pdf).avatarDataUrl()).isNull();
    }

    @Test
    @DisplayName("字号取的是视觉大小，不受内容流缩放影响")
    void fontSizeReflectsVisibleSizeNotTfValue() throws Exception {
        // 同样的文字，一个按 10pt 直接写，一个把整段内容流放大 4 倍再用 2.5pt 写，
        // 两者在页面上显示一样大，提取到的字号就必须一样
        byte[] plain = resumeWithBodyFont(10f, 1f);
        byte[] scaled = resumeWithBodyFont(2.5f, 4f);

        double plainSize = ResumeStyleExtractor.extract(plain).style().baseFontSizePt();
        double scaledSize = ResumeStyleExtractor.extract(scaled).style().baseFontSizePt();

        assertThat(plainSize).isCloseTo(10d, org.assertj.core.data.Offset.offset(0.6d));
        assertThat(scaledSize).isCloseTo(plainSize, org.assertj.core.data.Offset.offset(0.6d));
    }

    @Test
    @DisplayName("纯黑白简历不套强调色，避免无中生有")
    void monochromeResumeHasNoAccent() throws Exception {
        // 有通栏、有头像，但整份简历不用任何彩色强调
        byte[] pdf = syntheticResume(true, false);

        ResumeStyleVO style = ResumeStyleExtractor.extract(pdf).style();

        assertThat(style.accentColor()).isNull();
    }

    @Test
    @DisplayName("不是 PDF / 空内容时不抛异常，只返回空样式")
    void garbageInputDegradesGracefully() {
        assertThat(ResumeStyleExtractor.extract(null).style().accentColor()).isNull();
        assertThat(ResumeStyleExtractor.extract(new byte[0]).avatarDataUrl()).isNull();
        assertThat(ResumeStyleExtractor.extract("not a pdf".getBytes()).style().avatarPosition())
                .isEqualTo(ResumeStyleVO.POS_NONE);
    }

    @Test
    @DisplayName("样式里的脏值会被拦掉，不会写进 CSS")
    void styleSanitizesUntrustedValues() {
        ResumeStyleVO dirty = new ResumeStyleVO(
                "red; background: url(x)", "#ABCDEF", "rgb(1,2,3)", null,
                true, null, null, 9d,
                "SOMEWHERE", -5d, "OVAL", false,
                true, 999d, 9, java.util.List.of(" Redis ", "Redis", ""),
                999d, 100d, "  ");

        assertThat(dirty.accentColor()).isNull();
        assertThat(dirty.bodyColor()).isNull();
        assertThat(dirty.headingColor()).isEqualTo("#abcdef");
        // 越界的数值一律夹回合法区间
        assertThat(dirty.headerHeightRatio()).isEqualTo(0.6d);
        assertThat(dirty.avatarSizeMm()).isZero();
        assertThat(dirty.marginMm()).isEqualTo(40d);
        assertThat(dirty.baseFontSizePt()).isEqualTo(24d);
        assertThat(dirty.avatarPosition()).isEqualTo(ResumeStyleVO.POS_NONE);
        assertThat(dirty.avatarShape()).isEqualTo(ResumeStyleVO.SHAPE_SQUARE);
        assertThat(dirty.sectionRailOffsetMm()).isEqualTo(30d);
        assertThat(dirty.skillsColumns()).isEqualTo(1);
        assertThat(dirty.accentTerms()).containsExactly("Redis");
        assertThat(dirty.fontStack()).isNull();
    }

    @Test
    @DisplayName("复刻细节：识别左侧时间轴、单栏技能和局部强调词")
    void extractsRailColumnsAndAccentTerms() throws Exception {
        ResumeStyleVO style = ResumeStyleExtractor.extract(replicaDetailsResume()).style();

        assertThat(style.sectionRail()).isTrue();
        assertThat(style.sectionRailOffsetMm()).isBetween(3d, 12d);
        assertThat(style.skillsColumns()).isEqualTo(1);
        // 只保留真正着色过的词组，整行普通正文不能被写进去
        assertThat(style.accentTerms()).contains("Redis");
        assertThat(style.accentTerms()).noneMatch(term -> term.contains("Skilled in"));
    }

    @Test
    @DisplayName("新字段进得了 style_json，旧记录的 style_json 也要能安全读回")
    void styleJsonRoundTripsRailFields() throws Exception {
        // 老记录里没有这四个字段，反序列化时必须降级成默认值而不是抛异常
        ResumeStyleVO legacy = new ObjectMapper().readValue(
                "{\"accentColor\":\"#e60000\",\"sectionBadge\":false}", ResumeStyleVO.class);
        assertThat(legacy.accentColor()).isEqualTo("#e60000");
        assertThat(legacy.sectionRail()).isFalse();
        assertThat(legacy.sectionRailOffsetMm()).isZero();
        assertThat(legacy.skillsColumns()).isEqualTo(1);
        assertThat(legacy.accentTerms()).isEmpty();

        ResumeStyleVO style = ResumeStyleExtractor.extract(replicaDetailsResume()).style();
        JsonNode json = new ObjectMapper().valueToTree(style);
        assertThat(json.path("sectionRail").asBoolean()).isTrue();
        assertThat(json.path("sectionRailOffsetMm").asDouble()).isBetween(3d, 12d);
        assertThat(json.path("skillsColumns").asInt()).isEqualTo(1);
        assertThat(json.path("accentTerms").isArray())
                .as("accentTerms should be an array of exact highlighted phrases")
                .isTrue();
        assertThat(json.path("accentTerms").toString()).contains("Redis").doesNotContain("Skilled in");
    }

    // ------------------------------------------------------------------ 构造样本

    @Test
    @DisplayName("灰字紧贴红字时，边界像素不能把灰字误当成着重词")
    void accentTermsDoNotBleedAcrossGlyphBoundaries() throws Exception {
        // 字符框的右边界正好压在下一个字的首像素上：不内缩取样，
        // 灰色字会被邻字的红色“染红”，实测真实简历里的「责」就是这样被误收的。
        ResumeStyleVO style = ResumeStyleExtractor.extract(adjacentGrayThenAccentResume()).style();

        assertThat(style.accentTerms()).containsExactly("CD");
    }

    /**
     * 灰字 "AB" 后面紧挨着一列红墨，再加一处正常红字 "CD"。
     * <p>关键在那一列红墨：它正好落在 AB 字形框右侧紧邻的一格像素里。
     * 取样范围若包含这一格，灰色 "B" 就会被算成着红色（实测会得到 [B, CD]），
     * 所以提取时必须把右/下边界各内缩一像素。</p>
     */
    private byte[] adjacentGrayThenAccentResume() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float size = 10f;
            float scale = 150f / 72f;
            float y = 700f;
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float x = 60f;
                cs.setNonStrokingColor(BODY);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y);
                cs.showText("AB");
                cs.endText();
                float advance = x + font.getStringWidth("AB") / 1000f * size;

                // 把 AB 字形框之后的第一格像素整列涂红
                int column = (int) Math.ceil(advance * scale);
                cs.setNonStrokingColor(ACCENT);
                cs.addRect((column + 0.05f) / scale, y - 2f,
                        (column + 2.95f) / scale - (column + 0.05f) / scale, 10f);
                cs.fill();

                cs.setNonStrokingColor(ACCENT);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y - 60f);
                cs.showText("CD");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * 造一份接近真实中文简历的 PDF：深色通栏 + 居中头像 + 灰色正文 + 红色强调。
     *
     * @param withBand   是否加深色通栏页眉
     * @param withAccent 正文里是否用红色强调
     */
    private byte[] syntheticResume(boolean withBand, boolean withAccent) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float bandHeight = 0f;
                if (withBand) {
                    bandHeight = height * 0.20f;
                    // PDF 原点在左下角，通栏贴着页面顶部
                    cs.setNonStrokingColor(BAND);
                    cs.addRect(0, height - bandHeight, width, bandHeight);
                    cs.fill();
                }

                if (withBand) {
                    PDImageXObject avatar = avatarImage(document);
                    // 头像 75x75pt，水平居中，落在通栏偏下的位置
                    float size = 75f;
                    cs.drawImage(avatar, (width - size) / 2f, height - bandHeight + 10f, size, size);
                }

                cs.setNonStrokingColor(withBand ? Color.WHITE : Color.BLACK);
                cs.beginText();
                cs.setFont(font, 21f);
                cs.newLineAtOffset(width / 2f - 40f, height - bandHeight - 30f);
                cs.showText("ZHANG WEI");
                cs.endText();

                cs.setNonStrokingColor(BODY);
                cs.beginText();
                cs.setFont(font, 10.5f);
                cs.newLineAtOffset(43f, height - bandHeight - 70f);
                cs.showText("Java Backend Engineer");
                cs.endText();

                cs.setNonStrokingColor(withAccent ? ACCENT : BODY);
                cs.beginText();
                cs.setFont(font, 9.8f);
                cs.newLineAtOffset(43f, height - bandHeight - 110f);
                cs.showText("Spring Boot / MySQL / Redis / Kafka");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /** 造一张五彩的照片当头像，保证提取出来的不是纯色块 */
    private PDImageXObject avatarImage(PDDocument document) throws Exception {
        BufferedImage image = new BufferedImage(264, 266, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x25, 0x28, 0x2D));
        g.fillRect(0, 0, 264, 266);
        g.setColor(new Color(0xE1, 0xC4, 0xB6));
        g.fillOval(80, 50, 100, 120);
        g.setColor(new Color(0x1F, 0x23, 0x29));
        g.fillRect(40, 180, 184, 86);
        g.dispose();
        return LosslessFactory.createFromImage(document, image);
    }

    /**
     * 造一份只有一段正文的 PDF，用 {@code ctmScale} 模拟「内容流带缩放」的情况。
     * <p>缩放写进内容流的 cm 操作符，而不是改 Tf 值：这正是真实简历导出工具的做法，
     * 也是 {@code getFontSizeInPt()} 会报错的场景。</p>
     */
    private byte[] resumeWithBodyFont(float tfSize, float ctmScale) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setNonStrokingColor(BODY);
                cs.beginText();
                cs.setFont(font, tfSize);
                // 内容流整体缩放，文字在页面上仍是 tfSize * ctmScale 那么大
                cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getScaleInstance(ctmScale, ctmScale));
                cs.newLineAtOffset(50, 700);
                cs.showText("This is the body text of the resume sample for font size check.");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /** 单栏正文 + 左侧深色轨道 + 一小段红色关键词，模拟用户提供的原版简历。 */
    private byte[] replicaDetailsResume() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float height = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setNonStrokingColor(BAND);
                cs.addRect(30f, 80f, 1.2f, 620f);
                cs.fill();

                float size = 10f;
                float x = 60f;
                float y = height - 120f;
                cs.setNonStrokingColor(BODY);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y);
                cs.showText("Skilled in ");
                cs.endText();
                x += font.getStringWidth("Skilled in ") / 1000f * size;

                cs.setNonStrokingColor(ACCENT);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y);
                cs.showText("Redis");
                cs.endText();
                x += font.getStringWidth("Redis") / 1000f * size;

                cs.setNonStrokingColor(BODY);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y);
                cs.showText(" and MySQL");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
