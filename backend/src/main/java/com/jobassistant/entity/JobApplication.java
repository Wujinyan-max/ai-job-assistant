package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递记录，映射 application 表，是整个求职流程的核心。
 */
@Data
@TableName("application")
public class JobApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    /** 投递时使用的简历 */
    private Long resumeId;

    /** 见 {@link com.jobassistant.common.ApplicationStatus} */
    private String applicationStatus;

    private LocalDateTime applyTime;

    /** 投递渠道：Boss 直聘 / 拉勾 / 内推 / 官网 */
    private String source;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
