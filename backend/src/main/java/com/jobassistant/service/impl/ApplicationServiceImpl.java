package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobassistant.common.ApplicationStatus;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.common.PageResult;
import com.jobassistant.dto.ApplicationDTO;
import com.jobassistant.entity.JobApplication;
import com.jobassistant.mapper.JobApplicationMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.ApplicationService;
import com.jobassistant.service.JobService;
import com.jobassistant.service.ResumeService;
import com.jobassistant.vo.ApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    /** 看板每一列最多展示的卡片数 */
    private static final int BOARD_COLUMN_LIMIT = 50;

    private final JobApplicationMapper applicationMapper;
    private final JobService jobService;
    private final ResumeService resumeService;

    @Override
    public PageResult<ApplicationVO> page(int pageNum, int pageSize, String status, String keyword) {
        IPage<ApplicationVO> page = applicationMapper.selectApplicationPage(
                new Page<>(pageNum, pageSize), SecurityUtils.getUserId(), status, keyword);
        page.getRecords().forEach(this::fillLabel);
        return PageResult.of(page);
    }

    @Override
    public ApplicationVO detail(Long id) {
        ApplicationVO vo = applicationMapper.selectApplicationDetail(id, SecurityUtils.getUserId());
        if (vo == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        fillLabel(vo);
        return vo;
    }

    @Override
    public Map<String, List<ApplicationVO>> board() {
        Long userId = SecurityUtils.getUserId();
        Map<String, List<ApplicationVO>> result = new LinkedHashMap<>();
        for (String status : ApplicationStatus.ALL) {
            IPage<ApplicationVO> page = applicationMapper.selectApplicationPage(
                    new Page<>(1, BOARD_COLUMN_LIMIT), userId, status, null);
            page.getRecords().forEach(this::fillLabel);
            result.put(status, new ArrayList<>(page.getRecords()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ApplicationDTO dto) {
        Long userId = SecurityUtils.getUserId();
        // 校验职位归属
        jobService.requireOwned(dto.jobId());

        Long exists = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getJobId, dto.jobId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.APPLICATION_EXISTS);
        }

        JobApplication application = new JobApplication();
        application.setUserId(userId);
        application.setJobId(dto.jobId());
        application.setApplicationStatus(normalizeStatus(dto.applicationStatus()));
        application.setResumeId(resolveResumeId(dto.resumeId()));
        application.setSource(dto.source());
        application.setRemark(dto.remark());
        application.setApplyTime(resolveApplyTime(dto.applyTime(), application.getApplicationStatus()));
        applicationMapper.insert(application);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ApplicationDTO dto) {
        JobApplication application = requireOwned(id);
        if (!application.getJobId().equals(dto.jobId())) {
            jobService.requireOwned(dto.jobId());
            application.setJobId(dto.jobId());
        }
        if (StringUtils.hasText(dto.applicationStatus())) {
            application.setApplicationStatus(normalizeStatus(dto.applicationStatus()));
        }
        application.setResumeId(resolveResumeId(dto.resumeId()));
        application.setSource(dto.source());
        application.setRemark(dto.remark());
        application.setApplyTime(resolveApplyTime(dto.applyTime(), application.getApplicationStatus()));
        applicationMapper.updateById(application);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        JobApplication application = requireOwned(id);
        String normalized = normalizeStatus(status);
        application.setApplicationStatus(normalized);
        application.setApplyTime(resolveApplyTime(application.getApplyTime(), normalized));
        applicationMapper.updateById(application);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        applicationMapper.deleteById(id);
    }

    @Override
    public JobApplication requireOwned(Long id) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, SecurityUtils.getUserId()));
        if (application == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        return application;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return ApplicationStatus.WISHLIST;
        }
        String upper = status.trim().toUpperCase();
        if (!ApplicationStatus.isValid(upper)) {
            throw new BusinessException(ErrorCode.STATUS_INVALID, "投递状态不合法：" + status);
        }
        return upper;
    }

    /** 只有真正投出去的记录才需要投递时间，仅收藏时留空 */
    private LocalDateTime resolveApplyTime(LocalDateTime applyTime, String status) {
        if (applyTime != null) {
            return applyTime;
        }
        return ApplicationStatus.WISHLIST.equals(status) ? null : LocalDateTime.now();
    }

    private Long resolveResumeId(Long resumeId) {
        return resumeId == null ? null : resumeService.requireOwned(resumeId).getId();
    }

    private void fillLabel(ApplicationVO vo) {
        vo.setStatusLabel(ApplicationStatus.label(vo.getApplicationStatus()));
        if (vo.getInterviewCount() == null) {
            vo.setInterviewCount(0);
        }
    }
}
