package com.jobassistant.service;

import com.jobassistant.vo.DashboardVO;
import com.jobassistant.vo.ResumePerformanceVO;

import java.util.List;

public interface DashboardService {

    /**
     * 首页数据看板。
     *
     * @param trendDays 趋势图展示的天数，7-90
     */
    DashboardVO overview(int trendDays);

    /**
     * 简历版本效果分析。
     *
     * @param days 统计最近多少天的投递，7 / 30 / 90
     */
    List<ResumePerformanceVO> resumePerformance(int days);
}
