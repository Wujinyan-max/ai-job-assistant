package com.jobassistant.controller;

import com.jobassistant.common.PageResult;
import com.jobassistant.common.Result;
import com.jobassistant.dto.InterviewDTO;
import com.jobassistant.service.InterviewService;
import com.jobassistant.vo.InterviewVO;
import com.jobassistant.vo.InterviewUpdateVO;
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

@Tag(name = "06-面试管理", description = "面试安排与复盘，支持多轮")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @Operation(summary = "分页查询面试记录")
    @GetMapping
    public Result<PageResult<InterviewVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String result,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer upcomingDays) {
        return Result.success(interviewService.page(pageNum, pageSize, result, keyword, upcomingDays));
    }

    @Operation(summary = "即将到来的面试", description = "默认查未来 7 天")
    @GetMapping("/upcoming")
    public Result<List<InterviewVO>> upcoming(@RequestParam(defaultValue = "7") int days) {
        return Result.success(interviewService.upcoming(days));
    }

    @Operation(summary = "新增面试")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InterviewDTO dto) {
        return Result.success(interviewService.create(dto));
    }

    @Operation(summary = "修改面试",
            description = "结果改为「通过」时返回 awaitingNextStep=true，前端应询问下一步；"
                    + "nextStep 传 OFFER / NEXT_ROUND 来落库用户的选择")
    @PutMapping("/{id}")
    public Result<InterviewUpdateVO> update(@PathVariable Long id, @Valid @RequestBody InterviewDTO dto) {
        return Result.success(interviewService.update(id, dto));
    }

    @Operation(summary = "删除面试")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        interviewService.delete(id);
        return Result.success();
    }
}
