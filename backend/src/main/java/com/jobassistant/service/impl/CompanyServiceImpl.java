package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.common.PageResult;
import com.jobassistant.dto.CompanyDTO;
import com.jobassistant.entity.Company;
import com.jobassistant.mapper.CompanyMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper companyMapper;

    @Override
    public PageResult<Company> page(int pageNum, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .eq(Company::getUserId, SecurityUtils.getUserId())
                .eq(StringUtils.hasText(status), Company::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Company::getName, keyword)
                        .or().like(Company::getIndustry, keyword))
                .orderByDesc(Company::getUpdatedAt);
        return PageResult.of(companyMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public List<Company> listMine() {
        return companyMapper.selectList(new LambdaQueryWrapper<Company>()
                .eq(Company::getUserId, SecurityUtils.getUserId())
                .orderByDesc(Company::getUpdatedAt));
    }

    @Override
    public Company requireOwned(Long id) {
        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>()
                .eq(Company::getId, id)
                .eq(Company::getUserId, SecurityUtils.getUserId()));
        if (company == null) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
        return company;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CompanyDTO dto) {
        Company company = new Company();
        copy(dto, company);
        company.setUserId(SecurityUtils.getUserId());
        if (!StringUtils.hasText(company.getStatus())) {
            company.setStatus("TARGET");
        }
        companyMapper.insert(company);
        return company.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CompanyDTO dto) {
        Company company = requireOwned(id);
        copy(dto, company);
        companyMapper.updateById(company);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        companyMapper.deleteById(id);
    }

    private void copy(CompanyDTO dto, Company company) {
        company.setName(dto.name());
        company.setIndustry(dto.industry());
        company.setScale(dto.scale());
        company.setCity(dto.city());
        company.setWebsite(dto.website());
        if (StringUtils.hasText(dto.status())) {
            company.setStatus(dto.status());
        }
        company.setRemark(dto.remark());
    }
}
