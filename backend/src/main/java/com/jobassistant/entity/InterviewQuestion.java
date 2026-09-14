package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试题（AI 生成或手动录入），可作为刷题题库。
 */
@Data
@TableName("interview_question")
public class InterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    /** Java基础 / Spring Boot / MySQL / Redis / 项目 / HR */
    private String category;

    private String question;

    private String answer;

    /** EASY / MEDIUM / HARD */
    private String difficulty;

    /** AI / MANUAL */
    private String source;

    private Integer mastered;

    private LocalDateTime createdAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
