package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目标公司。
 */
@Data
@TableName("company")
public class Company {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String industry;

    /** 公司规模，如 500-999 人 */
    private String scale;

    private String city;

    private String website;

    /** TARGET-目标 CONTACTED-已沟通 CLOSED-已放弃 */
    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
