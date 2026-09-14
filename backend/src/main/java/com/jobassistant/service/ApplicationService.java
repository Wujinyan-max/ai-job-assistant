package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.dto.ApplicationDTO;
import com.jobassistant.entity.JobApplication;
import com.jobassistant.vo.ApplicationVO;

import java.util.List;
import java.util.Map;

public interface ApplicationService {

    PageResult<ApplicationVO> page(int pageNum, int pageSize, String status, String keyword);

    ApplicationVO detail(Long id);

    /** 看板视图：按状态分组 */
    Map<String, List<ApplicationVO>> board();

    Long create(ApplicationDTO dto);

    void update(Long id, ApplicationDTO dto);

    void updateStatus(Long id, String status);

    void delete(Long id);

    JobApplication requireOwned(Long id);
}
