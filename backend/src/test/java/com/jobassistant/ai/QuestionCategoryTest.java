package com.jobassistant.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分类归一化：模型自由发挥的分类名要收敛到固定分类，且同一个方向不能再被拆成多个桶。
 */
class QuestionCategoryTest {

    @Test
    @DisplayName("固定分类里不能出现兜底分类")
    void labelsExcludeFallback() {
        assertThat(QuestionCategory.labels()).hasSize(7).doesNotContain("其他");
        assertThat(QuestionCategory.allLabels()).contains("其他").hasSize(8);
    }

    @Test
    @DisplayName("模型给出的同义分类名会合并到同一个分类")
    void mergesSynonymCategories() {
        assertThat(QuestionCategory.normalize("项目深挖")).isEqualTo("项目与业务");
        assertThat(QuestionCategory.normalize("项目")).isEqualTo("项目与业务");
        assertThat(QuestionCategory.normalize("HR与软素质")).isEqualTo("HR与软素质");
        assertThat(QuestionCategory.normalize("HR")).isEqualTo("HR与软素质");
        assertThat(QuestionCategory.normalize("MySQL")).isEqualTo("数据库与缓存");
        assertThat(QuestionCategory.normalize("Redis")).isEqualTo("数据库与缓存");
        assertThat(QuestionCategory.normalize("数据库与中间件")).isEqualTo("数据库与缓存");
        assertThat(QuestionCategory.normalize("Kafka")).isEqualTo("框架与中间件");
        assertThat(QuestionCategory.normalize("Spring Boot")).isEqualTo("框架与中间件");
        assertThat(QuestionCategory.normalize("Java基础")).isEqualTo("编程语言与基础");
        assertThat(QuestionCategory.normalize("功能测试与用例设计")).isEqualTo("测试与质量");
        assertThat(QuestionCategory.normalize("AI辅助测试")).isEqualTo("测试与质量");
    }

    @Test
    @DisplayName("关键词冲突时按更具体的分类优先")
    void prefersMoreSpecificKeyword() {
        // 「性能测试」是测试方向，不能被 系统设计与性能 抢走
        assertThat(QuestionCategory.normalize("接口与性能测试")).isEqualTo("测试与质量");
        // Spring 事务属于框架，不能被 数据库与缓存 抢走
        assertThat(QuestionCategory.normalize("Spring事务")).isEqualTo("框架与中间件");
        // 高并发仍然是系统设计方向
        assertThat(QuestionCategory.normalize("高并发系统设计")).isEqualTo("系统设计与性能");
    }

    @Test
    @DisplayName("已经是固定分类的值必须保持不变，脚本才能重复执行")
    void normalizationIsIdempotent() {
        for (String label : QuestionCategory.allLabels()) {
            assertThat(QuestionCategory.normalize(label)).isEqualTo(label);
        }
    }

    @Test
    @DisplayName("空值和不认识的分类落到兜底分类")
    void fallsBackToOther() {
        assertThat(QuestionCategory.normalize(null)).isEqualTo("其他");
        assertThat(QuestionCategory.normalize("  ")).isEqualTo("其他");
        assertThat(QuestionCategory.normalize("玄学")).isEqualTo("其他");
    }

    @Test
    @DisplayName("筛选下拉框按固定顺序排序，未知分类排在最后")
    void sortsByCanonicalOrder() {
        List<String> sorted = List.of("HR与软素质", "Java基础", "Redis", "系统设计与性能")
                .stream()
                .map(QuestionCategory::normalize)
                .distinct()
                .sorted(QuestionCategory.canonicalOrder())
                .toList();

        // 顺序由 QuestionCategory 的声明顺序决定：语言 → 框架 → 数据库 → 系统设计 → 测试 → 项目 → HR
        assertThat(sorted).containsExactly("编程语言与基础", "数据库与缓存", "系统设计与性能", "HR与软素质");
    }
}
