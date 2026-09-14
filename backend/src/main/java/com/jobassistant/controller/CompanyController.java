package com.jobassistant.controller;

import com.jobassistant.common.PageResult;
import com.jobassistant.common.Result;
import com.jobassistant.dto.CompanyDTO;
import com.jobassistant.entity.Company;
import com.jobassistant.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "02-公司管理")
@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @Operation(summary = "分页查询公司")
    @GetMapping
    public Result<PageResult<Company>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return Result.success(companyService.page(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "查询全部公司", description = "下拉选择用，不分页")
    @GetMapping("/all")
    public Result<List<Company>> listAll() {
        return Result.success(companyService.listMine());
    }

    @Operation(summary = "查询公司详情")
    @GetMapping("/{id}")
    public Result<Company> detail(@PathVariable Long id) {
        return Result.success(companyService.requireOwned(id));
    }

    @Operation(summary = "新增公司")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CompanyDTO dto) {
        return Result.success(companyService.create(dto));
    }

    @Operation(summary = "修改公司")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CompanyDTO dto) {
        companyService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除公司")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return Result.success();
    }
}
