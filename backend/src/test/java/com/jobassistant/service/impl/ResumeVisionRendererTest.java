package com.jobassistant.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 渲染层是视觉识别的瓶颈：页数决定费用，体积决定请求体能不能发出去。
 */
class ResumeVisionRendererTest {

    @Test
    @DisplayName("多页 PDF 每页一张 JPEG data URL，按页码顺序排列")
    void rendersEveryPageInOrder() throws Exception {
        List<String> images = ResumeVisionRenderer.render(pdf(3));

        assertThat(images).hasSize(3);
        assertThat(images).allSatisfy(image -> assertThat(image).startsWith("data:image/jpeg;base64,"));
    }

    @Test
    @DisplayName("超过 5 页只渲染前 5 页，避免导入同步接口超时")
    void capsPageCount() throws Exception {
        assertThat(ResumeVisionRenderer.render(pdf(8))).hasSize(5);
    }

    @Test
    @DisplayName("体积可控：单页要远小于厂商 32MiB 的内联上限")
    void keepsEachPageSmall() throws Exception {
        for (String image : ResumeVisionRenderer.render(pdf(1))) {
            assertThat(image.length()).isLessThan(4 * 1024 * 1024);
        }
    }

    @Test
    @DisplayName("不是 PDF 时返回空列表，交给调用方降级到文本识别")
    void returnsEmptyForInvalidInput() {
        assertThat(ResumeVisionRenderer.render(null)).isEmpty();
        assertThat(ResumeVisionRenderer.render(new byte[0])).isEmpty();
        assertThat(ResumeVisionRenderer.render("not-a-pdf".getBytes())).isEmpty();
    }

    private static byte[] pdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText("Page " + (i + 1));
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
