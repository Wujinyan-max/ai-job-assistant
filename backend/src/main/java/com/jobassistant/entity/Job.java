package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位（含 JD 原文）。
 */
@Data
@TableName("job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long companyId;

    /** 关联查询出来的公司名称，不是数据库字段 */
    @TableField(exist = false)
    private String companyName;

    private String jobName;

    /** 职位描述原文 */
    private String jobDescription;

    /** 最低月薪（K） */
    private Integer salaryMin;

    /** 最高月薪（K） */
    private Integer salaryMax;

    private String location;

    private String jobUrl;

    /** OPEN-在招 CLOSED-已关闭 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
