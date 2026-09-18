package com.jobassistant.controller;

import com.jobassistant.common.Result;
import com.jobassistant.service.DashboardService;
import com.jobassistant.service.InterviewService;
import com.jobassistant.vo.DashboardVO;
import com.jobassistant.vo.InterviewVO;
import com.jobassistant.vo.ResumePerformanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "09-数据看板")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final InterviewService interviewService;

    @Operation(summary = "首页看板", description = "投递数、面试数、Offer 数、转化率、趋势与分布")
    @GetMapping
    public Result<DashboardVO> overview(@RequestParam(defaultValue = "30") int trendDays) {
        return Result.success(dashboardService.overview(trendDays));
    }

    @Operation(summary = "首页聚合数据", description = "看板 + 近期面试，一次请求拿完首页需要的所有数据")
    @GetMapping("/home")
    public Result<Map<String, Object>> home(@RequestParam(defaultValue = "30") int trendDays,
                                            @RequestParam(defaultValue = "7") int upcomingDays) {
        Map<String, Object> data = new HashMap<>();
        data.put("dashboard", dashboardService.overview(trendDays));
        List<InterviewVO> upcoming = interviewService.upcoming(upcomingDays);
        data.put("upcomingInterviews", upcoming);
        return Result.success(data);
    }

    @Operation(summary = "简历版本效果分析", description = "按简历版本统计投递数、面试率、Offer 率")
    @GetMapping("/resume-performance")
    public Result<List<ResumePerformanceVO>> resumePerformance(@RequestParam(defaultValue = "30") int days) {
        return Result.success(dashboardService.resumePerformance(days));
    }
}
