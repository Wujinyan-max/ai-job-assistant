package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
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

    /** 本次调用的 token 消耗，本地模拟或厂商未返回 usage 时为 null */
    private Integer inputTokens;
    private Integer outputTokens;
    /** 输入中命中上下文缓存的 token 数 */
    private Integer cachedTokens;
    /** 思维链 token 数 */
    private Integer reasoningTokens;
    /** 按配置单价估算的费用（元），未配置单价时为 null */
    private BigDecimal estimatedCost;

    private String result;

    private LocalDateTime createdAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
