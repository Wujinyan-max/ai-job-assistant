package com.jobassistant.service;

import com.jobassistant.common.PageResult;
import com.jobassistant.dto.CompanyDTO;
import com.jobassistant.entity.Company;

import java.util.List;

public interface CompanyService {

    PageResult<Company> page(int pageNum, int pageSize, String keyword, String status);

    List<Company> listMine();

    Company requireOwned(Long id);

    Long create(CompanyDTO dto);

    void update(Long id, CompanyDTO dto);

    void delete(Long id);
}
