package com.jobassistant.controller;

import com.jobassistant.common.ApplicationStatus;
import com.jobassistant.common.PageResult;
import com.jobassistant.common.Result;
import com.jobassistant.dto.ApplicationDTO;
import com.jobassistant.dto.StatusUpdateDTO;
import com.jobassistant.service.ApplicationService;
import com.jobassistant.vo.ApplicationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "05-投递管理", description = "收藏 / 投递 / 笔试 / 面试 / Offer 全流程")
@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "分页查询投递记录")
    @GetMapping
    public Result<PageResult<ApplicationVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(applicationService.page(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "看板视图", description = "按状态分组，前端直接渲染成拖拽看板")
    @GetMapping("/board")
    public Result<Map<String, List<ApplicationVO>>> board() {
        return Result.success(applicationService.board());
    }

    @Operation(summary = "状态字典", description = "返回全部状态值与中文标签")
    @GetMapping("/statuses")
    public Result<Map<String, String>> statuses() {
        Map<String, String> map = new LinkedHashMap<>();
        ApplicationStatus.ALL.forEach(status -> map.put(status, ApplicationStatus.label(status)));
        return Result.success(map);
    }

    @Operation(summary = "查询投递详情")
    @GetMapping("/{id}")
    public Result<ApplicationVO> detail(@PathVariable Long id) {
        return Result.success(applicationService.detail(id));
    }

    @Operation(summary = "新增投递记录", description = "收藏职位时状态传 WISHLIST，直接投递传 APPLIED")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ApplicationDTO dto) {
        return Result.success(applicationService.create(dto));
    }

    @Operation(summary = "修改投递记录")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ApplicationDTO dto) {
        applicationService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "更新投递状态", description = "看板拖拽时调用")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        applicationService.updateStatus(id, dto.status());
        return Result.success();
    }

    @Operation(summary = "删除投递记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return Result.success();
    }
}
