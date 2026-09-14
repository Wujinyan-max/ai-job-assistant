package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页数据看板。
 */
@Schema(description = "数据看板")
public record DashboardVO(
        @Schema(description = "投递总数（不含仅收藏）")
        long totalApplications,

        @Schema(description = "面试数量")
        long interviewCount,

        @Schema(description = "Offer 数量")
        long offerCount,

        @Schema(description = "被拒绝数量")
        long rejectedCount,

        @Schema(description = "仅收藏数量")
        long wishlistCount,

        long companyCount,
        long jobCount,
        long resumeCount,
        long questionCount,

        @Schema(description = "面试转化率 %")
        double interviewRate,

        @Schema(description = "Offer 转化率 %")
        double offerRate,

        @Schema(description = "投递状态分布")
        List<CountItem> statusDistribution,

        @Schema(description = "最近 30 天投递趋势")
        List<TrendItem> trend,

        @Schema(description = "投递最多的公司")
        List<CountItem> topCompanies
) {

    @Schema(description = "名称-数量")
    public record CountItem(String name, String label, long value) {
    }

    @Schema(description = "日期-数量")
    public record TrendItem(String date, long value) {
    }
}
