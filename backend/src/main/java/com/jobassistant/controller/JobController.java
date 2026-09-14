package com.jobassistant.controller;

import com.jobassistant.common.PageResult;
import com.jobassistant.common.Result;
import com.jobassistant.dto.JobDTO;
import com.jobassistant.entity.Job;
import com.jobassistant.service.JobService;
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

@Tag(name = "03-职位管理", description = "职位与 JD 的维护")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "分页查询职位")
    @GetMapping
    public Result<PageResult<Job>> page(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long companyId,
                                        @RequestParam(required = false) String status) {
        return Result.success(jobService.page(pageNum, pageSize, keyword, companyId, status));
    }

    @Operation(summary = "查询职位详情")
    @GetMapping("/{id}")
    public Result<Job> detail(@PathVariable Long id) {
        return Result.success(jobService.requireOwned(id));
    }

    @Operation(summary = "新增职位")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody JobDTO dto) {
        return Result.success(jobService.create(dto));
    }

    @Operation(summary = "修改职位")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody JobDTO dto) {
        jobService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除职位")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.success();
    }
}
