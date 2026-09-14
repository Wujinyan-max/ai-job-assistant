package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历，一个用户可以维护多个版本。
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 简历名称，如「Java 后端-社招版」 */
    private String title;

    private String name;

    private String phone;

    private String email;

    private String education;

    private Integer workYears;

    /** 技能标签，逗号分隔 */
    private String skills;

    /** 个人简介 */
    private String summary;

    /** 简历正文，AI 分析的主要输入 */
    private String content;

    /** 是否默认简历 */
    private Integer isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
