package com.jobassistant.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 从用户导入的简历文件里提取出来的版式与配色，用于「原版复刻」模板。
 * <p>只描述“看起来是什么样”，不含任何简历内容：内容是 {@link ResumeStructureVO} 的事，
 * 两者分开存，用户改内容不会影响版式，换版式也不会丢内容。</p>
 * <p>所有字段都可能为 null / 缺省：提取不到就退回模板默认值，不能因为样式没取到就让导入失败。</p>
 */
@Schema(description = "从简历文件里提取到的版式配色，用于「原版复刻」模板")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeStyleVO(
        @Schema(description = "强调色，原简历里用于技能标签、项目符号等")
        String accentColor,

        @Schema(description = "小节标题颜色")
        String headingColor,

        @Schema(description = "正文字色")
        String bodyColor,

        @Schema(description = "次要信息色，如起止时间、地点")
        String metaColor,

        @Schema(description = "是否有深色通栏页眉")
        boolean headerBand,

        @Schema(description = "页眉底色")
        String headerBandColor,

        @Schema(description = "页眉里的文字颜色")
        String headerTextColor,

        @Schema(description = "页眉占首页高度的比例，0~1")
        double headerHeightRatio,

        @Schema(description = "头像位置：CENTER_TOP / LEFT_TOP / NONE")
        String avatarPosition,

        @Schema(description = "头像边长（毫米）")
        double avatarSizeMm,

        @Schema(description = "头像形状：SQUARE / CIRCLE")
        String avatarShape,

        @Schema(description = "小节标题是否带左侧图标徽章")
        boolean sectionBadge,

        @Schema(description = "小节标题是否使用左侧贯穿轨道")
        boolean sectionRail,

        @Schema(description = "轨道中心相对正文左边界向左偏移的毫米数")
        double sectionRailOffsetMm,

        @Schema(description = "专业技能栏数：1 或 2")
        int skillsColumns,

        @Schema(description = "原 PDF 中使用强调色的精确文本片段")
        List<String> accentTerms,

        @Schema(description = "页边距（毫米）")
        double marginMm,

        @Schema(description = "正文字号（pt）")
        double baseFontSizePt,

        @Schema(description = "字体栈，直接写进 CSS font-family")
        String fontStack
) {

    public static final String POS_CENTER_TOP = "CENTER_TOP";
    public static final String POS_LEFT_TOP = "LEFT_TOP";
    public static final String POS_NONE = "NONE";
    public static final String SHAPE_SQUARE = "SQUARE";
    public static final String SHAPE_CIRCLE = "CIRCLE";

    /** 什么都没提取到时的兜底：全部走模板默认值 */
    public static ResumeStyleVO empty() {
        return new ResumeStyleVO(null, null, null, null, false, null, null, 0d,
                POS_NONE, 0d, SHAPE_SQUARE, false, false, 0d, 1, List.of(), 0d, 0d, null);
    }

    public ResumeStyleVO {
        accentColor = clean(accentColor);
        headingColor = clean(headingColor);
        bodyColor = clean(bodyColor);
        metaColor = clean(metaColor);
        headerBandColor = clean(headerBandColor);
        headerTextColor = clean(headerTextColor);
        avatarPosition = POS_LEFT_TOP.equals(avatarPosition) || POS_CENTER_TOP.equals(avatarPosition)
                ? avatarPosition : POS_NONE;
        avatarShape = SHAPE_CIRCLE.equals(avatarShape) ? SHAPE_CIRCLE : SHAPE_SQUARE;
        headerHeightRatio = clamp(headerHeightRatio, 0d, 0.6d);
        avatarSizeMm = clamp(avatarSizeMm, 0d, 60d);
        sectionRailOffsetMm = clamp(sectionRailOffsetMm, 0d, 30d);
        skillsColumns = skillsColumns == 2 ? 2 : 1;
        if (accentTerms == null) {
            accentTerms = List.of();
        } else {
            LinkedHashSet<String> cleaned = new LinkedHashSet<>();
            for (String term : accentTerms) {
                if (term == null) continue;
                String value = term.trim();
                if (!value.isEmpty() && value.length() <= 200) cleaned.add(value);
                if (cleaned.size() >= 120) break;
            }
            accentTerms = List.copyOf(cleaned);
        }
        marginMm = clamp(marginMm, 0d, 40d);
        baseFontSizePt = clamp(baseFontSizePt, 0d, 24d);
        if (fontStack != null && fontStack.isBlank()) {
            fontStack = null;
        }
    }

    /** 只接受 #rrggbb，避免把提取过程中的脏值写进 CSS */
    private static String clean(String color) {
        if (color == null) {
            return null;
        }
        String value = color.trim().toLowerCase();
        return value.matches("#[0-9a-f]{6}") ? value : null;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
