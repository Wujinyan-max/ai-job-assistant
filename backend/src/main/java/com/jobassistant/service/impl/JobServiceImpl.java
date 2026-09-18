package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.common.PageResult;
import com.jobassistant.dto.JobDTO;
import com.jobassistant.entity.Company;
import com.jobassistant.entity.Job;
import com.jobassistant.mapper.CompanyMapper;
import com.jobassistant.mapper.JobMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;

    @Override
    public PageResult<Job> page(int pageNum, int pageSize, String keyword, Long companyId, String status) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
                .eq(Job::getUserId, SecurityUtils.getUserId())
                .eq(companyId != null, Job::getCompanyId, companyId)
                .eq(StringUtils.hasText(status), Job::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Job::getJobName, keyword)
                        .or().like(Job::getLocation, keyword))
                .orderByDesc(Job::getUpdatedAt);
        Page<Job> page = jobMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillCompanyName(page.getRecords());
        return PageResult.of(page);
    }

    /** 批量查公司名，避免每条职位都去查一次数据库（N+1） */
    private void fillCompanyName(List<Job> jobs) {
        List<Long> companyIds = jobs.stream()
                .map(Job::getCompanyId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (companyIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = companyMapper.selectBatchIds(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));
        jobs.forEach(job -> job.setCompanyName(nameMap.get(job.getCompanyId())));
    }

    @Override
    public Job requireOwned(Long id) {
        Job job = jobMapper.selectOne(new LambdaQueryWrapper<Job>()
                .eq(Job::getId, id)
                .eq(Job::getUserId, SecurityUtils.getUserId()));
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(JobDTO dto) {
        Job job = new Job();
        copy(dto, job);
        job.setUserId(SecurityUtils.getUserId());
        if (!StringUtils.hasText(job.getStatus())) {
            job.setStatus("OPEN");
        }
        jobMapper.insert(job);
        return job.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, JobDTO dto) {
        Job job = requireOwned(id);
        copy(dto, job);
        jobMapper.updateById(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        jobMapper.deleteById(id);
    }

    private void copy(JobDTO dto, Job job) {
        if (dto.companyId() != null) {
            // 校验公司归属，避免把职位挂到别人的公司上
            Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>()
                    .eq(Company::getId, dto.companyId())
                    .eq(Company::getUserId, SecurityUtils.getUserId()));
            if (company == null) {
                throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
            }
            job.setCompanyId(dto.companyId());
        }
        job.setJobName(dto.jobName());
        job.setJobDescription(dto.jobDescription());
        job.setSalaryMin(dto.salaryMin());
        job.setSalaryMax(dto.salaryMax());
        job.setSalaryDesc(dto.salaryDesc());
        job.setLocation(dto.location());
        job.setJobUrl(dto.jobUrl());
        if (StringUtils.hasText(dto.status())) {
            job.setStatus(dto.status());
        }
    }
}
