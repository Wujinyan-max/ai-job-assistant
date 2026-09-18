package com.jobassistant.service.impl;

import com.jobassistant.mapper.InterviewQuestionMapper;
import com.jobassistant.security.LoginUser;
import com.jobassistant.vo.QuestionCategoryStatsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题库首页按分类平铺，分类卡片的顺序必须稳定，否则每次刷新卡片都在跳。
 */
class QuestionServiceImplTest {

    private static final long USER_ID = 7L;

    private final InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
    private final QuestionServiceImpl service = new QuestionServiceImpl(questionMapper);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(USER_ID, "alice", "token-1"), null));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("分类统计按固定分类顺序返回，不受数据库返回顺序影响")
    void sortsCategoryStatsByCanonicalOrder() {
        when(questionMapper.selectCategoryStats(USER_ID)).thenReturn(List.of(
                stats("HR与软素质", 3, 1),
                stats("测试与质量", 5, 2),
                stats("编程语言与基础", 4, 0),
                stats("数据库与缓存", 2, 2)));

        List<QuestionCategoryStatsVO> stats = service.categoryStats();

        verify(questionMapper).selectCategoryStats(USER_ID);
        assertThat(stats).extracting(QuestionCategoryStatsVO::getCategory)
                .containsExactly("编程语言与基础", "数据库与缓存", "测试与质量", "HR与软素质");
    }

    @Test
    @DisplayName("历史遗留的自由分类排在固定分类后面，但不会被丢掉")
    void keepsUnknownCategoriesAtTheEnd() {
        when(questionMapper.selectCategoryStats(USER_ID)).thenReturn(List.of(
                stats("玄学分类", 1, 0),
                stats("项目与业务", 2, 1)));

        assertThat(service.categoryStats()).extracting(QuestionCategoryStatsVO::getCategory)
                .containsExactly("项目与业务", "玄学分类");
    }

    @Test
    @DisplayName("题库为空时返回空列表，不报错")
    void handlesEmptyBank() {
        when(questionMapper.selectCategoryStats(USER_ID)).thenReturn(List.of());

        assertThat(service.categoryStats()).isEmpty();
    }

    private static QuestionCategoryStatsVO stats(String category, int total, int mastered) {
        return new QuestionCategoryStatsVO(category, total, mastered);
    }
}
