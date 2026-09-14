package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录（一场面试一条，支持多轮）。
 */
@Data
@TableName("interview")
public class Interview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long applicationId;

    /** 第几轮 */
    private Integer roundNo;

    /** 轮次名称，如「技术一面」 */
    private String roundName;

    /** PHONE / VIDEO / ONSITE / WRITTEN */
    private String interviewType;

    private LocalDateTime interviewTime;

    private String interviewer;

    private String location;

    private String meetingUrl;

    /** PENDING-待定 PASS-通过 FAIL-未通过 */
    private String result;

    /** 复盘总结 */
    private String review;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
