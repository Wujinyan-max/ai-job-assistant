package com.jobassistant.controller;

import com.jobassistant.common.PageResult;
import com.jobassistant.common.Result;
import com.jobassistant.entity.InterviewQuestion;
import com.jobassistant.service.QuestionService;
import com.jobassistant.vo.QuestionCategoryStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "07-面试题库", description = "AI 生成的题目会落到这里，可持续刷题")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @Operation(summary = "分页查询题目")
    @GetMapping
    public Result<PageResult<InterviewQuestion>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) Integer mastered,
                                                      @RequestParam(required = false) Long jobId) {
        return Result.success(questionService.page(pageNum, pageSize, category, mastered, jobId));
    }

    @Operation(summary = "查询已有的题目分类")
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(questionService.categories());
    }

    @Operation(summary = "按分类统计题目数量（题库首页的分类卡片）")
    @GetMapping("/category-stats")
    public Result<List<QuestionCategoryStatsVO>> categoryStats() {
        return Result.success(questionService.categoryStats());
    }

    @Operation(summary = "查询题库支持的固定分类（AI 出题时选用）")
    @GetMapping("/category-options")
    public Result<List<String>> categoryOptions() {
        return Result.success(questionService.categoryOptions());
    }

    @Operation(summary = "标记是否已掌握")
    @PatchMapping("/{id}/mastered")
    public Result<Void> markMastered(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        questionService.markMastered(id, Boolean.TRUE.equals(body.get("mastered")));
        return Result.success();
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除题目")
    @DeleteMapping
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        questionService.deleteBatch(ids);
        return Result.success();
    }
}
