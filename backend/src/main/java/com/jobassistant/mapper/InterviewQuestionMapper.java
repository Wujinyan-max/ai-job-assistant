package com.jobassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobassistant.entity.InterviewQuestion;
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
}
