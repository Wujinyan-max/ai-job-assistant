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
import com.jobassistant.vo.InterviewUpdateVO;
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
    public PageResult<InterviewVO> page(int pageNum, int pageSize, String result, String keyword,
                                        Integer upcomingDays) {
        // 传了天数就只查这个窗口内的面试：首页「7 天内面试」红点点进来时用，
        // 放在 SQL 里过滤而不是前端筛，翻页才不会漏数据
        boolean upcomingOnly = upcomingDays != null && upcomingDays > 0;
        LocalDateTime from = upcomingOnly ? LocalDateTime.now() : null;
        LocalDateTime to = upcomingOnly ? from.plusDays(upcomingDays) : null;
        IPage<InterviewVO> page = interviewMapper.selectInterviewPage(
                new Page<>(pageNum, pageSize), SecurityUtils.getUserId(), result, keyword,
                upcomingOnly, from, to);
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
    public InterviewUpdateVO update(Long id, InterviewDTO dto) {
        Interview interview = requireOwned(id);
        // 先把改动前的状态记下来：只有「从非 PASS 改成 PASS」才需要弹窗问下一步，
        // 已经是 PASS 的再次保存（比如只是补了复盘内容）不该反复打扰用户
        String previousResult = interview.getResult();

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

        return applyResultFlow(interview, previousResult, dto.nextStep());
    }

    /**
     * 面试结果落库后同步推进投递状态。
     * <p>失败直接置为「已拒绝」；通过则交给用户选下一步——「已拿 Offer」置为 OFFER，
     * 「进入下一轮」保持面试中并自动建一条下一轮草稿，不选则不改状态。</p>
     */
    private InterviewUpdateVO applyResultFlow(Interview interview, String previousResult, String nextStep) {
        Long applicationId = interview.getApplicationId();
        String result = interview.getResult();

        if ("FAIL".equals(result)) {
            applicationService.updateStatus(applicationId, ApplicationStatus.REJECTED);
            return flow(applicationId, ApplicationStatus.REJECTED, null, null, false);
        }
        if (!"PASS".equals(result)) {
            // 待定：不动投递状态，返回当前状态供前端刷新
            return flow(applicationId, currentStatus(applicationId), null, null, false);
        }

        boolean justPassed = !"PASS".equals(previousResult);
        String step = StringUtils.hasText(nextStep) ? nextStep.trim().toUpperCase() : "";

        if ("OFFER".equals(step)) {
            applicationService.updateStatus(applicationId, ApplicationStatus.OFFER);
            return flow(applicationId, ApplicationStatus.OFFER, null, null, false);
        }
        if ("NEXT_ROUND".equals(step)) {
            // 保持面试中，并建一条下一轮草稿，用户只需要补时间
            applicationService.updateStatus(applicationId, ApplicationStatus.INTERVIEW);
            int roundNo = nextRoundNo(applicationId);
            Interview draft = new Interview();
            draft.setUserId(SecurityUtils.getUserId());
            draft.setApplicationId(applicationId);
            draft.setRoundNo(roundNo);
            draft.setRoundName("第 " + roundNo + " 轮");
            draft.setInterviewType(interview.getInterviewType());
            draft.setResult("PENDING");
            interviewMapper.insert(draft);
            return flow(applicationId, ApplicationStatus.INTERVIEW, draft.getId(), roundNo, false);
        }

        // 没有选下一步：通过本身说明已经推进到面试阶段了
        if (justPassed && !ApplicationStatus.INTERVIEW.equals(currentStatus(applicationId))) {
            applicationService.updateStatus(applicationId, ApplicationStatus.INTERVIEW);
        }
        return flow(applicationId, currentStatus(applicationId), null, null, justPassed);
    }

    private InterviewUpdateVO flow(Long applicationId, String status, Long nextInterviewId,
                                   Integer nextRoundNo, boolean awaiting) {
        return new InterviewUpdateVO(applicationId, status, ApplicationStatus.label(status),
                nextInterviewId, nextRoundNo, awaiting);
    }

    private String currentStatus(Long applicationId) {
        return applicationService.requireOwned(applicationId).getApplicationStatus();
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
