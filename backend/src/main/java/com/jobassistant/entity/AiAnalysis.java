package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 分析结果（JD 解析 / 简历匹配），result 字段存放模型返回的 JSON 字符串。
 */
@Data
@TableName("ai_analysis")
public class AiAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    private Long resumeId;

    /** 见 {@link com.jobassistant.common.AiAnalysisType} */
    private String analysisType;

    private String model;

    /** 匹配分数 0-100，仅简历匹配有值 */
    private Integer score;

    private String result;

    private LocalDateTime createdAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
