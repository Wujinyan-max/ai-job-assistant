package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新面试结果后的流转结果。
 * <p>面试标记为「通过」时用户会被问一句「然后呢」，这个对象把选择带来的连锁变化
 * 一次性回给前端：投递状态变成了什么、有没有自动建下一轮面试草稿。</p>
 */
@Schema(description = "面试结果更新后的流转结果")
public record InterviewUpdateVO(
        Long applicationId,

        @Schema(description = "更新后的投递状态")
        String applicationStatus,

        @Schema(description = "投递状态中文标签")
        String applicationStatusLabel,

        @Schema(description = "选择「进入下一轮」时自动创建的面试草稿 ID，否则为 null")
        Long nextRoundInterviewId,

        @Schema(description = "自动创建的下一轮轮次号，否则为 null")
        Integer nextRoundNo,

        @Schema(description = "结果刚变成「通过」、还在等用户选下一步时为 true")
        boolean awaitingNextStep
) {
}
