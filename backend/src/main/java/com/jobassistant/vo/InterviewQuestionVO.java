package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "面试题")
public record InterviewQuestionVO(
        String category,
        String question,
        @Schema(description = "EASY / MEDIUM / HARD")
        String difficulty,
        String answer
) {
}
