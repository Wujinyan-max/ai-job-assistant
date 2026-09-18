package com.jobassistant.service;

import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.service.impl.ResumeImportServiceImpl;
import com.jobassistant.vo.ResumeImportVO;
import com.jobassistant.vo.ResumeStructureVO;
import com.jobassistant.vo.ResumeStyleVO;
import com.jobassistant.vo.ResumeVisionVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导入接口的边界：格式、大小、扫不出文字的文件，都要给出明确的中文提示。
 */
class ResumeImportServiceImplTest {

    /** 只测导入本身（字段抽取、边界提示），视觉识别用一个固定返回的实现替掉 */
    private final ResumeVisionService vision = (pdf, text) -> new ResumeVisionService.VisionOutcome(
            new ResumeVisionVO(ResumeStructureVO.empty(), ResumeStyleVO.empty()),
            ResumeVisionService.Source.LOCAL, null);

    private final ResumeImportServiceImpl service = new ResumeImportServiceImpl(vision);

    @Test
    @DisplayName("DOCX 简历能解析出正文和常用字段")
    void parsesDocxResume() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "张伟-Java后端.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes(
                        "张伟",
                        "男 | 5年经验 | 13812345678 | zhangwei@example.com",
                        "求职意向：Java 后端开发工程师",
                        "教育背景",
                        "某某大学  计算机科学与技术  本科",
                        "专业技能",
                        "Java、Spring Boot、MySQL、Redis、Docker",
                        "自我评价",
                        "五年后端开发经验，负责过订单系统的性能优化。"));

        ResumeImportVO result = service.parse(file);

        assertThat(result.fileType()).isEqualTo("DOCX");
        assertThat(result.fileName()).isEqualTo("张伟-Java后端.docx");
        assertThat(result.name()).isEqualTo("张伟");
        assertThat(result.phone()).isEqualTo("13812345678");
        assertThat(result.email()).isEqualTo("zhangwei@example.com");
        assertThat(result.education()).isEqualTo("本科");
        assertThat(result.workYears()).isEqualTo(5);
        assertThat(result.skills()).contains("Java", "Spring Boot", "MySQL", "Redis", "Docker");
        assertThat(result.summary()).contains("性能优化");
        assertThat(result.title()).startsWith("张伟");
        assertThat(result.filledFields()).contains("简历名称", "姓名", "电话", "学历", "技能标签", "简历正文");
        assertThat(result.textLength()).isEqualTo(result.content().length());
    }

    @Test
    @DisplayName("GBK 编码的 txt 简历也能读出来")
    void decodesGbkTextFile() {
        MockMultipartFile file = new MockMultipartFile("file", "王芳.txt", "text/plain",
                "姓名：王芳\n电话：13900001111\n技能：Java、MySQL\n".getBytes(Charset.forName("GBK")));

        ResumeImportVO result = service.parse(file);

        assertThat(result.fileType()).isEqualTo("TXT");
        assertThat(result.name()).isEqualTo("王芳");
        assertThat(result.phone()).isEqualTo("13900001111");
        assertThat(result.skills()).contains("Java", "MySQL");
    }

    @Test
    @DisplayName(".doc 老格式提示另存为 docx 或 PDF，而不是笼统报解析失败")
    void rejectsLegacyDocWithHint() {
        MockMultipartFile file = new MockMultipartFile("file", "简历.doc", "application/msword",
                "not-a-real-doc".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parse(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(".docx")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.RESUME_UNSUPPORTED_TYPE.getCode());
    }

    @Test
    @DisplayName("扫不出文字的文件（扫描件）提示换成文字版")
    void failsWhenNoTextFound() {
        MockMultipartFile file = new MockMultipartFile("file", "扫描件.txt", "text/plain",
                "   \n\n  ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parse(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文字")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.RESUME_PARSE_FAILED.getCode());
    }

    @Test
    @DisplayName("超过 10MB 的文件直接拒绝，不做解析")
    void rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile("file", "简历.pdf", "application/pdf",
                new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.parse(file))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE.getCode());
    }

    /** 用 POI 现场生成一份真的 docx，避免把二进制文件塞进仓库 */
    private static byte[] docxBytes(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(text);
            }
            document.write(out);
            return out.toByteArray();
        }
    }
}
