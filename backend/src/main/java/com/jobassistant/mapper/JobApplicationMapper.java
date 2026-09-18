package com.jobassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jobassistant.entity.JobApplication;
import com.jobassistant.vo.ApplicationVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 投递记录 Mapper，看板统计用注解 SQL 直接聚合，避免把全表数据捞到内存里算。
 */
public interface JobApplicationMapper extends BaseMapper<JobApplication> {

    /** 按状态统计投递数量 */
    @Select("""
            SELECT application_status AS status, COUNT(*) AS cnt
            FROM application
            WHERE user_id = #{userId} AND deleted = 0
            GROUP BY application_status
            """)
    List<Map<String, Object>> countByStatus(@Param("userId") Long userId);

    /** 按天统计投递趋势（只统计有投递时间的记录） */
    @Select("""
            SELECT DATE(apply_time) AS day, COUNT(*) AS cnt
            FROM application
            WHERE user_id = #{userId} AND deleted = 0
              AND apply_time IS NOT NULL AND apply_time >= #{startDate}
            GROUP BY DATE(apply_time)
            ORDER BY day
            """)
    List<Map<String, Object>> countByDay(@Param("userId") Long userId,
                                         @Param("startDate") LocalDate startDate);

    /** 统计投递数量最多的公司 Top N */
    @Select("""
            SELECT c.name AS companyName, COUNT(*) AS cnt
            FROM application a
            JOIN job j ON j.id = a.job_id AND j.deleted = 0
            LEFT JOIN company c ON c.id = j.company_id AND c.deleted = 0
            WHERE a.user_id = #{userId} AND a.deleted = 0
            GROUP BY c.name
            ORDER BY cnt DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topCompanies(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 按简历版本统计投递转化，回答「哪份简历更有效」。
     * <p>面试数不能只看当前状态：一条投递面完被拒后状态会变成 REJECTED，
     * 但它确实帮用户拿到了面试机会，所以这里额外把「存在面试记录」也算进来，
     * 否则越往后投，早期简历的面试率反而会越来越低。</p>
     */
    @Select("""
            <script>
            SELECT a.resume_id        AS resumeId,
                   r.title            AS resumeTitle,
                   COUNT(*)           AS applicationCount,
                   SUM(CASE WHEN a.application_status IN ('INTERVIEW', 'OFFER')
                             OR EXISTS (SELECT 1 FROM interview i
                                         WHERE i.application_id = a.id AND i.deleted = 0)
                            THEN 1 ELSE 0 END) AS interviewCount,
                   SUM(CASE WHEN a.application_status = 'OFFER' THEN 1 ELSE 0 END) AS offerCount
            FROM application a
            LEFT JOIN resume r ON r.id = a.resume_id AND r.deleted = 0
            WHERE a.user_id = #{userId} AND a.deleted = 0
              AND a.application_status IN ('APPLIED', 'WRITTEN_TEST', 'INTERVIEW', 'OFFER', 'REJECTED', 'CLOSED')
            <if test="from != null">
                AND a.apply_time IS NOT NULL AND a.apply_time >= #{from}
            </if>
            GROUP BY a.resume_id, r.title
            ORDER BY applicationCount DESC, interviewCount DESC
            </script>
            """)
    List<Map<String, Object>> countByResume(@Param("userId") Long userId,
                                            @Param("from") LocalDateTime from);

    /** 投递列表：一次 join 出职位、公司、简历和面试轮次，避免前端 N+1 查询 */
    @Select("""
            <script>
            SELECT a.id,
                   a.job_id      AS jobId,
                   j.job_name    AS jobName,
                   j.company_id  AS companyId,
                   c.name        AS companyName,
                   a.resume_id   AS resumeId,
                   r.title       AS resumeTitle,
                   a.application_status AS applicationStatus,
                   a.apply_time  AS applyTime,
                   a.source,
                   a.remark,
                   a.created_at  AS createdAt,
                   a.updated_at  AS updatedAt,
                   (SELECT COUNT(*) FROM interview i
                     WHERE i.application_id = a.id AND i.deleted = 0) AS interviewCount,
                   (SELECT MIN(i.interview_time) FROM interview i
                     WHERE i.application_id = a.id AND i.deleted = 0
                       AND i.interview_time IS NOT NULL
                       AND i.interview_time >= NOW()) AS nextInterviewTime,
                   (SELECT an.score FROM ai_analysis an
                     WHERE an.user_id = a.user_id AND an.deleted = 0
                       AND an.analysis_type = 'RESUME_MATCH'
                       AND an.job_id = a.job_id
                     ORDER BY an.created_at DESC, an.id DESC LIMIT 1) AS matchScore
            FROM application a
            LEFT JOIN job j     ON j.id = a.job_id AND j.deleted = 0
            LEFT JOIN company c ON c.id = j.company_id AND c.deleted = 0
            LEFT JOIN resume r  ON r.id = a.resume_id AND r.deleted = 0
            WHERE a.user_id = #{userId} AND a.deleted = 0
            <if test="status != null and status != ''">
                AND a.application_status = #{status}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (j.job_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY a.updated_at DESC, a.id DESC
            </script>
            """)
    IPage<ApplicationVO> selectApplicationPage(IPage<ApplicationVO> page,
                                               @Param("userId") Long userId,
                                               @Param("status") String status,
                                               @Param("keyword") String keyword);

    /** 单条投递详情，SQL 与列表保持一致 */
    @Select("""
            SELECT a.id,
                   a.job_id      AS jobId,
                   j.job_name    AS jobName,
                   j.company_id  AS companyId,
                   c.name        AS companyName,
                   a.resume_id   AS resumeId,
                   r.title       AS resumeTitle,
                   a.application_status AS applicationStatus,
                   a.apply_time  AS applyTime,
                   a.source,
                   a.remark,
                   a.created_at  AS createdAt,
                   a.updated_at  AS updatedAt,
                   (SELECT COUNT(*) FROM interview i
                     WHERE i.application_id = a.id AND i.deleted = 0) AS interviewCount
            FROM application a
            LEFT JOIN job j     ON j.id = a.job_id AND j.deleted = 0
            LEFT JOIN company c ON c.id = j.company_id AND c.deleted = 0
            LEFT JOIN resume r  ON r.id = a.resume_id AND r.deleted = 0
            WHERE a.id = #{id} AND a.user_id = #{userId} AND a.deleted = 0
            """)
    ApplicationVO selectApplicationDetail(@Param("id") Long id, @Param("userId") Long userId);
}
