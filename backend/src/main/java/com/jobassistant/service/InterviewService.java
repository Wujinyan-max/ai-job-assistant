package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.dto.InterviewDTO;
import com.jobassistant.vo.InterviewVO;
import com.jobassistant.vo.InterviewUpdateVO;

import java.util.List;

public interface InterviewService {

    /**
     * 面试列表。
     *
     * @param upcomingDays 只查未来 N 天内的面试，传 null 查全部
     */
    PageResult<InterviewVO> page(int pageNum, int pageSize, String result, String keyword, Integer upcomingDays);

    /** 未来 N 天内的待面试 */
    List<InterviewVO> upcoming(int days);

    Long create(InterviewDTO dto);

    /** 更新面试记录，返回结果流转的连锁变化（投递状态、下一轮草稿） */
    InterviewUpdateVO update(Long id, InterviewDTO dto);

    void delete(Long id);
}
