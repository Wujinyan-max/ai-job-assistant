package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.entity.InterviewQuestion;

import java.util.List;

public interface QuestionService {

    PageResult<InterviewQuestion> page(int pageNum, int pageSize, String category, Integer mastered, Long jobId);

    List<String> categories();

    void markMastered(Long id, boolean mastered);

    void delete(Long id);

    void deleteBatch(List<Long> ids);
}
