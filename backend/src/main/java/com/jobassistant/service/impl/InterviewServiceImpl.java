package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobassistant.common.ApplicationStatus;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.common.PageResult;
import com.jobassistant.dto.InterviewDTO;
import com.jobassistant.entity.Interview;
import com.jobassistant.entity.JobApplication;
import com.jobassistant.mapper.InterviewMapper;
import com.jobassistant.mapper.JobApplicationMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.ApplicationService;
import com.jobassistant.service.InterviewService;
import com.jobassistant.vo.InterviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    /** 面试形式白名单 */
    private static final Set<String> TYPES = Set.of("PHONE", "VIDEO", "ONSITE", "WRITTEN");
    /** 面试结果白名单 */
    private static final Set<String> RESULTS = Set.of("PENDING", "PASS", "FAIL");
    /** 还没有推进到面试阶段的状态 */
    private static final Set<String> BEFORE_INTERVIEW =
            Set.of(ApplicationStatus.WISHLIST, ApplicationStatus.APPLIED, ApplicationStatus.WRITTEN_TEST);

    private final InterviewMapper interviewMapper;
    private final JobApplicationMapper applicationMapper;
    private final ApplicationService applicationService;

    @Override
    public PageResult<InterviewVO> page(int pageNum, int pageSize, String result, String keyword) {
        IPage<InterviewVO> page = interviewMapper.selectInterviewPage(
                new Page<>(pageNum, pageSize), SecurityUtils.getUserId(), result, keyword);
        return PageResult.of(page);
    }

    @Override
    public List<InterviewVO> upcoming(int days) {
        LocalDateTime from = LocalDateTime.now();
        LocalDateTime to = from.plusDays(Math.max(1, days));
        return interviewMapper.selectUpcoming(SecurityUtils.getUserId(), from, to, 10);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InterviewDTO dto) {
        JobApplication application = applicationService.requireOwned(dto.applicationId());

        Interview interview = new Interview();
        interview.setUserId(SecurityUtils.getUserId());
        interview.setApplicationId(application.getId());
        interview.setRoundNo(dto.roundNo() == null ? nextRoundNo(application.getId()) : dto.roundNo());
        interview.setRoundName(StringUtils.hasText(dto.roundName())
                ? dto.roundName() : "第 " + interview.getRoundNo() + " 轮");
        interview.setInterviewType(normalizeType(dto.interviewType()));
        interview.setInterviewTime(dto.interviewTime());
        interview.setInterviewer(dto.interviewer());
        interview.setLocation(dto.location());
        interview.setMeetingUrl(dto.meetingUrl());
        interview.setResult(normalizeResult(dto.result()));
        interview.setReview(dto.review());
        interviewMapper.insert(interview);

        // 排了面试就说明已经推进到面试阶段，自动把投递状态往前推一格
        if (BEFORE_INTERVIEW.contains(application.getApplicationStatus())) {
            applicationService.updateStatus(application.getId(), ApplicationStatus.INTERVIEW);
        }
        return interview.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InterviewDTO dto) {
        Interview interview = requireOwned(id);
        if (!interview.getApplicationId().equals(dto.applicationId())) {
            applicationService.requireOwned(dto.applicationId());
            interview.setApplicationId(dto.applicationId());
        }
        if (dto.roundNo() != null) {
            interview.setRoundNo(dto.roundNo());
        }
        interview.setRoundName(dto.roundName());
        if (StringUtils.hasText(dto.interviewType())) {
            interview.setInterviewType(normalizeType(dto.interviewType()));
        }
        interview.setInterviewTime(dto.interviewTime());
        interview.setInterviewer(dto.interviewer());
        interview.setLocation(dto.location());
        interview.setMeetingUrl(dto.meetingUrl());
        if (StringUtils.hasText(dto.result())) {
            interview.setResult(normalizeResult(dto.result()));
        }
        interview.setReview(dto.review());
        interviewMapper.updateById(interview);

        // 某轮通过/失败后，同步更新投递状态
        if ("FAIL".equals(interview.getResult())) {
            applicationService.updateStatus(interview.getApplicationId(), ApplicationStatus.REJECTED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        interviewMapper.deleteById(id);
    }

    private Interview requireOwned(Long id) {
        Interview interview = interviewMapper.selectOne(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getId, id)
                .eq(Interview::getUserId, SecurityUtils.getUserId()));
        if (interview == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_FOUND);
        }
        return interview;
    }

    private int nextRoundNo(Long applicationId) {
        Long count = interviewMapper.selectCount(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getApplicationId, applicationId));
        return (int) ((count == null ? 0 : count) + 1);
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "VIDEO";
        }
        String upper = type.trim().toUpperCase();
        if (!TYPES.contains(upper)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试形式不合法：" + type);
        }
        return upper;
    }

    private String normalizeResult(String result) {
        if (!StringUtils.hasText(result)) {
            return "PENDING";
        }
        String upper = result.trim().toUpperCase();
        if (!RESULTS.contains(upper)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试结果不合法：" + result);
        }
        return upper;
    }
}
