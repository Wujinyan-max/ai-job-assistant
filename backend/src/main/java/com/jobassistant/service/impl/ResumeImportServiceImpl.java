package com.jobassistant.service.impl;

import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.service.ResumeImportService;
import com.jobassistant.vo.ResumeImportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 简历文件导入实现：先把文件转成纯文本，再用 {@link ResumeFieldExtractor} 抽取字段。
 * <p>解析结果不落库，前端拿到后回填表单，用户确认了才会保存。</p>
 */
@Slf4j
@Service
public class ResumeImportServiceImpl implements ResumeImportService {

    /** 与 spring.servlet.multipart.max-file-size 保持一致 */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    /** 正文上限，避免超长文件写进库 */
    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final Charset GBK = Charset.forName("GBK");

    @Override
    public ResumeImportVO parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要导入的简历文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim() : "resume";
        String fileType = resolveFileType(fileName);

        String content;
        try {
            content = ResumeFieldExtractor.normalize(extractText(fileType, file.getBytes()));
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("解析简历文件失败: {}", fileName, e);
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED);
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED,
                    "没有读取到文字，可能是扫描件或加密文件，请换成文字版的简历再试");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        ResumeFieldExtractor.ParsedFields fields = ResumeFieldExtractor.extract(content);
        List<String> filledFields = new ArrayList<>();
        filledFields.add("简历名称");
        if (StringUtils.hasText(fields.name())) {
            filledFields.add("姓名");
        }
        if (StringUtils.hasText(fields.phone())) {
            filledFields.add("电话");
        }
        if (StringUtils.hasText(fields.email())) {
            filledFields.add("邮箱");
        }
        if (StringUtils.hasText(fields.education())) {
            filledFields.add("学历");
        }
        if (fields.workYears() != null) {
            filledFields.add("工作年限");
        }
        if (StringUtils.hasText(fields.skills())) {
            filledFields.add("技能标签");
        }
        if (StringUtils.hasText(fields.summary())) {
            filledFields.add("个人简介");
        }
        filledFields.add("简历正文");

        return new ResumeImportVO(fileName, fileType, content.length(), filledFields,
                buildTitle(fields, fileName), fields.name(), fields.phone(), fields.email(),
                fields.education(), fields.workYears(), fields.skills(), fields.summary(), content);
    }

    private String resolveFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".docx")) {
            return "DOCX";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "TXT";
        }
        if (lower.endsWith(".doc")) {
            throw new BusinessException(ErrorCode.RESUME_UNSUPPORTED_TYPE,
                    "暂不支持 .doc 老格式，请在 Word 里另存为 .docx 或 PDF 后重新导入");
        }
        throw new BusinessException(ErrorCode.RESUME_UNSUPPORTED_TYPE);
    }

    private String extractText(String fileType, byte[] bytes) throws IOException {
        return switch (fileType) {
            case "PDF" -> extractPdf(bytes);
            case "DOCX" -> extractDocx(bytes);
            default -> decodeText(bytes);
        };
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException e) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "PDF 已加密，请先去掉密码再导入");
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /** .txt/.md 优先按 UTF-8 解码，失败再按 GBK 兜底（中文简历经常是 GBK） */
    private String decodeText(byte[] bytes) {
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, offset, bytes.length - offset, GBK);
        }
    }

    private String buildTitle(ResumeFieldExtractor.ParsedFields fields, String fileName) {
        String name = fields.name();
        String intent = fields.intent();
        if (StringUtils.hasText(name) && StringUtils.hasText(intent)) {
            return trimTo(name + "-" + intent);
        }
        if (StringUtils.hasText(name)) {
            return trimTo(name + "的简历");
        }
        if (StringUtils.hasText(intent)) {
            return trimTo(intent);
        }
        return trimTo(stripExtension(fileName));
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return StringUtils.hasText(base) ? base.strip() : "导入的简历";
    }

    private String trimTo(String value) {
        String trimmed = value.strip();
        return trimmed.length() <= MAX_TITLE_LENGTH ? trimmed : trimmed.substring(0, MAX_TITLE_LENGTH);
    }
}
