package com.jobassistant.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 把 PDF 简历渲染成给多模态模型看的图片。
 *
 * <p>图片按 data URL 内联进请求体，所以体积直接决定耗时和费用。实测 110 DPI +
 * JPEG 0.75 下一页约 170–260KB，三页合计不到 700KB，辨认文字足够清晰，又不会把
 * 请求体撑大（厂商上限 48MiB）。</p>
 */
public final class ResumeVisionRenderer {

    /** 渲染精度：低于 96 时小字号笔画会糊成一片，模型容易认错字 */
    private static final float RENDER_DPI = 110f;
    /** 最多识别前五页：覆盖常见简历，超出的部分交给纯文本路径兜底 */
    private static final int MAX_PAGES = 5;
    private static final float JPEG_QUALITY = 0.75f;

    private ResumeVisionRenderer() {
    }

    /**
     * 渲染 PDF 的页面图片，返回可直接放进请求体的 data URL 列表。
     * <p>任何一页渲染失败就跳过该页；整个文件都渲染不出来时返回空列表，
     * 由调用方降级到纯文本路径。</p>
     */
    public static List<String> render(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(MAX_PAGES, document.getNumberOfPages());
            for (int i = 0; i < pages; i++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI);
                    String dataUrl = toJpegDataUrl(image);
                    if (dataUrl != null) {
                        images.add(dataUrl);
                    }
                } catch (Exception ignored) {
                    // 单页渲染失败不影响其它页
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return images;
    }

    /** 转 JPEG 而不是 PNG：同样清晰度下体积小一个数量级 */
    static String toJpegDataUrl(BufferedImage image) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}