package com.jobassistant.service;

import com.jobassistant.vo.ResumeImportVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历文件导入：从 PDF / DOCX / TXT 里读出文字并抽取结构化字段。
 */
public interface ResumeImportService {

    /**
     * 解析简历文件，不落库，由前端确认后再调用新增简历接口。
     */
    ResumeImportVO parse(MultipartFile file);
}
