package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 简历版本效果：某一份简历在一段时间内的投递转化情况。
 * <p>用于回答「哪份简历更有效」——同样是投 20 份，Java 版进了 5 次面试，
 * 全栈版一次没进，就说明该换简历了。</p>
 */
@Schema(description = "简历版本效果分析")
public record ResumePerformanceVO(
        @Schema(description = "简历 ID，未关联简历时为 null")
        Long resumeId,

        @Schema(description = "简历名称，未关联简历时为「未关联简历」")
        String resumeTitle,

        @Schema(description = "该时间范围内使用这份简历的投递数")
        long applicationCount,

        @Schema(description = "其中进入面试（含 Offer）的数量")
        long interviewCount,

        @Schema(description = "其中拿到 Offer 的数量")
        long offerCount,

        @Schema(description = "面试率 %")
        double interviewRate,

        @Schema(description = "Offer 率 %")
        double offerRate
) {
}
