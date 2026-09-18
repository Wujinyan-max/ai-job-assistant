package com.jobassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobassistant.entity.InterviewQuestion;
import com.jobassistant.vo.QuestionCategoryStatsVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {

    /** 当前用户已有的题目分类，用于前端筛选下拉框 */
    @Select("""
            SELECT DISTINCT category FROM interview_question
            WHERE user_id = #{userId} AND deleted = 0 AND category IS NOT NULL
            ORDER BY category
            """)
    List<String> selectCategories(@Param("userId") Long userId);

    /** 每个分类的题目总数与已掌握数量，题库首页按分类平铺时用 */
    @Select("""
            SELECT category,
                   COUNT(*) AS total,
                   COUNT(CASE WHEN mastered = 1 THEN 1 END) AS mastered
            FROM interview_question
            WHERE user_id = #{userId} AND deleted = 0 AND category IS NOT NULL
            GROUP BY category
            """)
    List<QuestionCategoryStatsVO> selectCategoryStats(@Param("userId") Long userId);
}
