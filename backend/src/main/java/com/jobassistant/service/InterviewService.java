package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.dto.InterviewDTO;
import com.jobassistant.vo.InterviewVO;

import java.util.List;

public interface InterviewService {

    PageResult<InterviewVO> page(int pageNum, int pageSize, String result, String keyword);

    /** 未来 N 天内的待面试 */
    List<InterviewVO> upcoming(int days);

    Long create(InterviewDTO dto);

    void update(Long id, InterviewDTO dto);

    void delete(Long id);
}
