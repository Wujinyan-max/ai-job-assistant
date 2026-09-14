package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.dto.ResumeDTO;
import com.jobassistant.entity.Resume;

import java.util.List;

public interface ResumeService {

    PageResult<Resume> page(int pageNum, int pageSize, String keyword);

    List<Resume> listMine();

    Resume requireOwned(Long id);

    Long create(ResumeDTO dto);

    void update(Long id, ResumeDTO dto);

    void delete(Long id);

    void setDefault(Long id);
}
