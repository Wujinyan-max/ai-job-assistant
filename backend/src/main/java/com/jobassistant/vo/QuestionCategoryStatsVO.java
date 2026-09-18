package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某个分类下的题目统计，题库首页用它平铺成一张张分类卡片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题库分类统计")
public class QuestionCategoryStatsVO {

    @Schema(description = "分类名")
    private String category;

    @Schema(description = "该分类下的题目总数")
    private Integer total;

    @Schema(description = "其中已掌握的数量")
    private Integer mastered;
}
