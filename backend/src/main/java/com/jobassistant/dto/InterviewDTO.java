package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "面试记录新增/修改请求")
public record InterviewDTO(
        @NotNull(message = "请选择对应的投递记录")
        Long applicationId,

        Integer roundNo,
        String roundName,

        @Schema(description = "PHONE / VIDEO / ONSITE / WRITTEN")
        String interviewType,

        @Schema(description = "面试时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-09-18 14:00:00")
        LocalDateTime interviewTime,

        String interviewer,
        String location,
        String meetingUrl,

        @Schema(description = "PENDING / PASS / FAIL")
        String result,

        String review,

        @Schema(description = "通过后用户选的下一步：OFFER 已拿 Offer / NEXT_ROUND 进入下一轮 / 留空保持现状")
        String nextStep
) {
}
