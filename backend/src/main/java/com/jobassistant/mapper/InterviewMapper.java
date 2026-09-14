package com.jobassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jobassistant.entity.Interview;
import com.jobassistant.vo.InterviewVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewMapper extends BaseMapper<Interview> {

    /** 面试列表：带上对应的职位与公司 */
    @Select("""
            <script>
            SELECT i.id,
                   i.application_id AS applicationId,
                   a.job_id         AS jobId,
                   j.job_name       AS jobName,
                   c.name           AS companyName,
                   i.round_no       AS roundNo,
                   i.round_name     AS roundName,
                   i.interview_type AS interviewType,
                   i.interview_time AS interviewTime,
                   i.interviewer,
                   i.location,
                   i.meeting_url    AS meetingUrl,
                   i.result,
                   i.review,
                   i.created_at     AS createdAt
            FROM interview i
            LEFT JOIN application a ON a.id = i.application_id AND a.deleted = 0
            LEFT JOIN job j         ON j.id = a.job_id AND j.deleted = 0
            LEFT JOIN company c     ON c.id = j.company_id AND c.deleted = 0
            WHERE i.user_id = #{userId} AND i.deleted = 0
            <if test="result != null and result != ''">
                AND i.result = #{result}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (j.job_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.name LIKE CONCAT('%', #{keyword}, '%')
                     OR i.interviewer LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY i.interview_time IS NULL, i.interview_time ASC, i.id DESC
            </script>
            """)
    IPage<InterviewVO> selectInterviewPage(IPage<InterviewVO> page,
                                           @Param("userId") Long userId,
                                           @Param("result") String result,
                                           @Param("keyword") String keyword);

    /** 即将到来的面试（用于首页提醒） */
    @Select("""
            SELECT i.id,
                   i.application_id AS applicationId,
                   a.job_id         AS jobId,
                   j.job_name       AS jobName,
                   c.name           AS companyName,
                   i.round_no       AS roundNo,
                   i.round_name     AS roundName,
                   i.interview_type AS interviewType,
                   i.interview_time AS interviewTime,
                   i.interviewer,
                   i.location,
                   i.meeting_url    AS meetingUrl,
                   i.result,
                   i.review,
                   i.created_at     AS createdAt
            FROM interview i
            LEFT JOIN application a ON a.id = i.application_id AND a.deleted = 0
            LEFT JOIN job j         ON j.id = a.job_id AND j.deleted = 0
            LEFT JOIN company c     ON c.id = j.company_id AND c.deleted = 0
            WHERE i.user_id = #{userId} AND i.deleted = 0
              AND i.interview_time IS NOT NULL
              AND i.interview_time >= #{from}
              AND i.interview_time < #{to}
              AND i.result = 'PENDING'
            ORDER BY i.interview_time ASC
            LIMIT #{limit}
            """)
    List<InterviewVO> selectUpcoming(@Param("userId") Long userId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("limit") int limit);
}
