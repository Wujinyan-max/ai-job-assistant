package com.jobassistant.service;

import com.jobassistant.vo.DashboardVO;

public interface DashboardService {

    /**
     * 首页数据看板。
     *
     * @param trendDays 趋势图展示的天数，7-90
     */
    DashboardVO overview(int trendDays);
}
