package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递记录视图，已经把职位、公司、简历信息拼好，前端列表可以直接渲染。
 */
@Data
@Schema(description = "投递记录")
public class ApplicationVO {

    private Long id;
    private Long jobId;
    private String jobName;
    private Long companyId;
    private String companyName;
    private Long resumeId;
    private String resumeTitle;
    private String applicationStatus;
    private String statusLabel;
    private LocalDateTime applyTime;
    private String source;
    private String remark;
    private Integer interviewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
