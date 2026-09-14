package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.dto.JobDTO;
import com.jobassistant.entity.Job;

public interface JobService {

    PageResult<Job> page(int pageNum, int pageSize, String keyword, Long companyId, String status);

    Job requireOwned(Long id);

    Long create(JobDTO dto);

    void update(Long id, JobDTO dto);

    void delete(Long id);
}
