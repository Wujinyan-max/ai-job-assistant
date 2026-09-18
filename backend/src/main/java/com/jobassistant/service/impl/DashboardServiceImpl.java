package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobassistant.common.ApplicationStatus;
import com.jobassistant.entity.Company;
import com.jobassistant.entity.Job;
import com.jobassistant.entity.InterviewQuestion;
import com.jobassistant.entity.Resume;
import com.jobassistant.mapper.CompanyMapper;
import com.jobassistant.mapper.InterviewQuestionMapper;
import com.jobassistant.mapper.JobApplicationMapper;
import com.jobassistant.mapper.JobMapper;
import com.jobassistant.mapper.ResumeMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.DashboardService;
import com.jobassistant.vo.DashboardVO;
import com.jobassistant.vo.ResumePerformanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int MIN_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 90;

    /** 简历效果分析的默认统计窗口 */
    private static final int DEFAULT_REPORT_DAYS = 30;
    private static final int MAX_REPORT_DAYS = 365;

    private final JobApplicationMapper applicationMapper;
    private final CompanyMapper companyMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewQuestionMapper questionMapper;

    @Override
    public List<ResumePerformanceVO> resumePerformance(int days) {
        int window = days <= 0 ? DEFAULT_REPORT_DAYS : Math.min(days, MAX_REPORT_DAYS);
        Long userId = SecurityUtils.getUserId();
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        List<ResumePerformanceVO> report = new ArrayList<>();
        for (Map<String, Object> row : applicationMapper.countByResume(userId, from)) {
            long applications = number(row.get("applicationCount"));
            long interviews = number(row.get("interviewCount"));
            long offers = number(row.get("offerCount"));
            Object resumeId = row.get("resumeId");
            Object resumeTitle = row.get("resumeTitle");
            report.add(new ResumePerformanceVO(
                    resumeId instanceof Number id ? id.longValue() : null,
                    resumeTitle == null ? "未关联简历" : resumeTitle.toString(),
                    applications,
                    interviews,
                    offers,
                    rate(interviews, applications),
                    rate(offers, applications)));
        }
        return report;
    }

    @Override
    public DashboardVO overview(int trendDays) {
        Long userId = SecurityUtils.getUserId();

        Map<String, Long> statusCount = loadStatusCount(userId);

        // 投递总数只统计真正投出去的（收藏不算投递）
        long total = ApplicationStatus.SUBMITTED.stream()
                .mapToLong(status -> statusCount.getOrDefault(status, 0L))
                .sum();
        // 面试数 = 当前处于面试阶段或已拿到 Offer 的投递数
        long interviewCount = statusCount.getOrDefault(ApplicationStatus.INTERVIEW, 0L)
                + statusCount.getOrDefault(ApplicationStatus.OFFER, 0L);
        long offerCount = statusCount.getOrDefault(ApplicationStatus.OFFER, 0L);
        long rejectedCount = statusCount.getOrDefault(ApplicationStatus.REJECTED, 0L);
        long wishlistCount = statusCount.getOrDefault(ApplicationStatus.WISHLIST, 0L);

        List<DashboardVO.CountItem> distribution = new ArrayList<>();
        for (String status : ApplicationStatus.ALL) {
            distribution.add(new DashboardVO.CountItem(status, ApplicationStatus.label(status),
                    statusCount.getOrDefault(status, 0L)));
        }

        return new DashboardVO(
                total,
                interviewCount,
                offerCount,
                rejectedCount,
                wishlistCount,
                count(companyMapper.selectCount(new LambdaQueryWrapper<Company>().eq(Company::getUserId, userId))),
                count(jobMapper.selectCount(new LambdaQueryWrapper<Job>().eq(Job::getUserId, userId))),
                count(resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId))),
                count(questionMapper.selectCount(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getUserId, userId))),
                rate(interviewCount, total),
                rate(offerCount, total),
                distribution,
                loadTrend(userId, trendDays),
                loadTopCompanies(userId));
    }

    private Map<String, Long> loadStatusCount(Long userId) {
        Map<String, Long> statusCount = new HashMap<>();
        for (Map<String, Object> row : applicationMapper.countByStatus(userId)) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status != null && cnt instanceof Number number) {
                statusCount.put(status.toString(), number.longValue());
            }
        }
        return statusCount;
    }

    private List<DashboardVO.TrendItem> loadTrend(Long userId, int trendDays) {
        int days = Math.min(Math.max(trendDays, MIN_TREND_DAYS), MAX_TREND_DAYS);
        LocalDate start = LocalDate.now().minusDays(days - 1L);

        Map<String, Long> byDay = new HashMap<>();
        for (Map<String, Object> row : applicationMapper.countByDay(userId, start)) {
            String day = normalizeDay(row.get("day"));
            Object cnt = row.get("cnt");
            if (day != null && cnt instanceof Number number) {
                byDay.put(day, number.longValue());
            }
        }

        List<DashboardVO.TrendItem> trend = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String day = start.plusDays(i).toString();
            trend.add(new DashboardVO.TrendItem(day, byDay.getOrDefault(day, 0L)));
        }
        return trend;
    }

    private List<DashboardVO.CountItem> loadTopCompanies(Long userId) {
        List<DashboardVO.CountItem> items = new ArrayList<>();
        for (Map<String, Object> row : applicationMapper.topCompanies(userId, 5)) {
            Object name = row.get("companyName");
            Object cnt = row.get("cnt");
            items.add(new DashboardVO.CountItem(
                    name == null ? "未关联公司" : name.toString(),
                    name == null ? "未关联公司" : name.toString(),
                    cnt instanceof Number number ? number.longValue() : 0L));
        }
        return items;
    }

    /** JDBC 驱动可能把 DATE 返回成 java.sql.Date / LocalDate / LocalDateTime，这里统一成 yyyy-MM-dd */
    private String normalizeDay(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate().toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
        }
        return value.toString();
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    /** 聚合 SQL 里的 SUM/COUNT 在不同驱动下可能是 BigInteger 等类型，统一转 long */
    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /** 百分比保留一位小数 */
    private double rate(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
