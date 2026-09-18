package com.jobassistant.service.impl;

import com.jobassistant.vo.InterviewQuestionVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「每个分类题数」这个输入必须真的生效：模型经常不按数量返回，这里裁剪到用户设置的数量。
 */
class AiServiceImplTest {

    @Test
    @DisplayName("指定了出题范围：每个分类最多保留 N 道，顺序不变")
    void limitsEachCategoryWhenCategoriesPicked() {
        List<InterviewQuestionVO> questions = List.of(
                question("Java基础", "q1"), question("Java基础", "q2"), question("Java基础", "q3"),
                question("Redis", "q4"), question("Redis", "q5"));

        List<InterviewQuestionVO> limited = AiServiceImpl.limitQuestions(questions, List.of("Java基础", "Redis"), 1);

        assertThat(limited).extracting(InterviewQuestionVO::question).containsExactly("q1", "q4");
    }

    @Test
    @DisplayName("交给 AI 决定分类：保留前 5 个分类，避免题目总数失控")
    void capsCategoryCountWhenAiPicksCategories() {
        List<InterviewQuestionVO> questions = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            questions.add(question("分类" + i, "q" + i));
        }

        List<InterviewQuestionVO> limited = AiServiceImpl.limitQuestions(questions, List.of(), 1);

        assertThat(limited).hasSize(5);
        assertThat(limited).extracting(InterviewQuestionVO::category)
                .containsExactly("分类0", "分类1", "分类2", "分类3", "分类4");
    }

    @Test
    @DisplayName("模型多返回的题目被裁掉，不会超过 分类数 × 每个分类题数")
    void neverExceedsRequestedTotal() {
        List<InterviewQuestionVO> questions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            questions.add(question("Java基础", "java" + i));
            questions.add(question("Redis", "redis" + i));
            questions.add(question("MySQL", "mysql" + i));
        }

        List<InterviewQuestionVO> limited = AiServiceImpl.limitQuestions(questions, List.of(), 2);

        assertThat(limited).hasSize(6);
    }

    @Test
    @DisplayName("分类为空、题目为空时不报错")
    void handlesEmptyInput() {
        assertThat(AiServiceImpl.limitQuestions(null, List.of(), 3)).isEmpty();
        assertThat(AiServiceImpl.limitQuestions(List.of(), List.of(), 3)).isEmpty();
        assertThat(AiServiceImpl.limitQuestions(List.of(question(null, "q")), List.of(), 2)).hasSize(1);
    }

    @Test
    @DisplayName("难度码转成给模型看的中文")
    void mapsDifficultyToChinese() {
        assertThat(AiServiceImpl.difficultyLabel("EASY")).isEqualTo("简单");
        assertThat(AiServiceImpl.difficultyLabel("HARD")).isEqualTo("困难");
        assertThat(AiServiceImpl.difficultyLabel("MEDIUM")).isEqualTo("中等");
        assertThat(AiServiceImpl.difficultyLabel("未知")).isEqualTo("中等");
    }

    @Test
    @DisplayName("简历优化另存：默认在原简历名后挂上岗位，方便区分版本")
    void buildsOptimizedResumeTitle() {
        assertThat(AiServiceImpl.optimizeResumeTitle(null, "Java 后端-社招版", "高级后端工程师", null, null))
                .isEqualTo("Java 后端-社招版-高级后端工程师优化版");
        assertThat(AiServiceImpl.optimizeResumeTitle(null, null, null, null, null)).isEqualTo("我的简历-优化版");
    }

    @Test
    @DisplayName("简历优化另存：用户填了名称就用用户的，并截断到数据库列长度")
    void respectsUserTitleAndColumnLength() {
        assertThat(AiServiceImpl.optimizeResumeTitle(" 投字节跳动用 ", "旧标题", "岗位", null, null))
                .isEqualTo("投字节跳动用");

        String longBaseTitle = "很长的简历名称".repeat(20);
        assertThat(AiServiceImpl.optimizeResumeTitle(null, longBaseTitle, "岗位", null, null)).hasSize(100);
        assertThat(AiServiceImpl.optimizeResumeTitle("名".repeat(150), null, null, null, null)).hasSize(100);
    }

    private InterviewQuestionVO question(String category, String text) {
        return new InterviewQuestionVO(category, text, "MEDIUM", "答案");
    }
}
