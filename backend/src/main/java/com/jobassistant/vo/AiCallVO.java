package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 调用结果包装：业务数据 + 本次调用的 token 消耗。
 *
 * @param data   业务数据（JD 解析结果 / 匹配结果 / 面试题）
 * @param usage  本次调用的 token 消耗与费用估算
 * @param mocked 是否由本地模拟引擎产生（此时不消耗 token）
 */
@Schema(description = "AI 调用结果")
public record AiCallVO<T>(
        @Schema(description = "业务数据")
        T data,

        @Schema(description = "本次调用的 token 消耗与费用估算")
        AiUsageVO usage,

        @Schema(description = "是否由本地模拟引擎产生（不消耗 token）")
        boolean mocked
) {
}
