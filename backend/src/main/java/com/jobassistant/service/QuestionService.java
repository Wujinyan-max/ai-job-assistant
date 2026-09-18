package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.entity.InterviewQuestion;
import com.jobassistant.vo.QuestionCategoryStatsVO;

import java.util.List;

public interface QuestionService {

    PageResult<InterviewQuestion> page(int pageNum, int pageSize, String category, Integer mastered, Long jobId);

    List<String> categories();

    /** 每个分类的题目总数与已掌握数量，题库首页按分类平铺时用 */
    List<QuestionCategoryStatsVO> categoryStats();

    /** 题库支持的固定分类，用于 AI 出题时选择范围 */
    List<String> categoryOptions();

    void markMastered(Long id, boolean mastered);

    void delete(Long id);

    void deleteBatch(List<Long> ids);
}
