package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录视图，带上了对应的职位与公司名称。
 */
@Data
@Schema(description = "面试记录")
public class InterviewVO {

    private Long id;
    private Long applicationId;
    private Long jobId;
    private String jobName;
    private String companyName;
    private Integer roundNo;
    private String roundName;
    private String interviewType;
    private LocalDateTime interviewTime;
    private String interviewer;
    private String location;
    private String meetingUrl;
    private String result;
    private String review;
    private LocalDateTime createdAt;
}
