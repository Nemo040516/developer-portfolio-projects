/**
 * 文件速览：
 * 1. 文件职责：定义投递模块数据库访问方法，包含求职者/商家列表、状态更新与关系校验查询。
 * 2. 关键能力：本轮新增商家与求职者投递关系统计查询，用于敏感接口归属校验。
 * 3. 主要表：job_apply、job_post、job_interview。
 * 4. 阅读建议：先看分页查询，再看 update 与 count 类方法。
 */
package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.JobDelivery;
import com.example.backend.vo.ChatJobTitleRow;
import com.example.backend.vo.DeliveryVO;
import com.example.backend.vo.MerchantDeliveryVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface JobDeliveryMapper extends BaseMapper<JobDelivery> {

    // 关联 job_post 和 merchant_profile 查询投递详情
    @Select("SELECT d.id, d.job_id, COALESCE(d.status, 0) AS status, d.feedback, d.create_time, " +
            "i.schedule_time AS interviewTime, i.location AS interviewLocation, " +
            "i.method AS interviewMethod, i.remark AS interviewRemark, i.status AS interviewStatus, " +
            "j.title as jobTitle, j.work_location as workLocation, " +
            "j.min_salary as minSalary, j.max_salary as maxSalary, " +
            "CONCAT(j.min_salary, 'k-', j.max_salary, 'k') as salaryRange, " +
            "m.company_name as companyName, " +
            "j.status AS jobStatus, j.audit_status AS jobAuditStatus " +
            "FROM job_apply d " +
            "LEFT JOIN job_post j ON d.job_id = j.id " +
            "LEFT JOIN merchant_profile m ON j.merchant_id = m.user_id " +
            "LEFT JOIN (" +
            "  SELECT s1.* FROM job_interview s1 " +
            "  JOIN (" +
            "    SELECT delivery_id, MAX(round_no) AS max_round " +
            "    FROM job_interview GROUP BY delivery_id" +
            "  ) s2 ON s1.delivery_id = s2.delivery_id AND s1.round_no = s2.max_round" +
            ") i ON i.delivery_id = d.id " +
            "WHERE d.applicant_id = #{userId} " +
            "ORDER BY d.create_time DESC")
    List<DeliveryVO> selectMyDeliveryList(Long userId);

    // 求职者端：分页查询投递记录
    IPage<DeliveryVO> selectMyDeliveryPage(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            @Param("timeOrder") String timeOrder
    );

    // 求职者端：查询某职位的投递状态
    @Select("SELECT COALESCE(d.status, 0) FROM job_apply d " +
            "WHERE d.applicant_id = #{userId} AND d.job_id = #{jobId} " +
            "LIMIT 1")
    Integer selectDeliveryStatus(@Param("userId") Long userId, @Param("jobId") Long jobId);

    // 商家端：分页查询候选人投递列表
    IPage<MerchantDeliveryVO> selectMerchantDeliveryPage(
            Page<?> page,
            @Param("merchantId") Long merchantId,
            @Param("jobId") Long jobId,
            @Param("status") Integer status,
            @Param("degree") String degree
    );

    // 商家端：更新投递状态（带权限限制）
    @Update("UPDATE job_apply d " +
            "JOIN job_post j ON d.job_id = j.id " +
            "SET d.status = #{status}, " +
            "d.feedback = #{feedback}, " +
            "d.update_time = NOW() " +
            "WHERE d.id = #{deliveryId} AND j.merchant_id = #{merchantId}")
    int updateDeliveryStatus(@Param("deliveryId") Long deliveryId,
                             @Param("status") Integer status,
                             @Param("feedback") String feedback,
                             @Param("merchantId") Long merchantId);

    /**
     * 归属校验：统计商家与求职者之间是否存在真实投递关系。
     */
    @Select("SELECT COUNT(1) " +
            "FROM job_apply d " +
            "JOIN job_post j ON j.id = d.job_id " +
            "WHERE j.merchant_id = #{merchantId} AND d.applicant_id = #{applicantId}")
    long countMerchantApplicantRelation(@Param("merchantId") Long merchantId,
                                        @Param("applicantId") Long applicantId);

    /**
     * 商家侧：查询每位求职者最新投递的职位名称（用于聊天会话关联职位）
     */
    @Select({
            "<script>",
            "SELECT d.applicant_id AS applicantId, j.title AS jobTitle",
            "FROM job_apply d",
            "JOIN job_post j ON j.id = d.job_id",
            "JOIN (",
            "  SELECT d2.applicant_id AS applicant_id, MAX(d2.id) AS max_id",
            "  FROM job_apply d2",
            "  JOIN job_post j2 ON j2.id = d2.job_id",
            "  WHERE j2.merchant_id = #{merchantId}",
            "  <if test='applicantIds != null and applicantIds.size > 0'>",
            "    AND d2.applicant_id IN",
            "    <foreach collection='applicantIds' item='id' open='(' separator=',' close=')'>",
            "      #{id}",
            "    </foreach>",
            "  </if>",
            "  GROUP BY d2.applicant_id",
            ") t ON t.max_id = d.id",
            "</script>"
    })
    List<ChatJobTitleRow> selectLatestJobTitleByApplicants(@Param("merchantId") Long merchantId,
                                                           @Param("applicantIds") List<Long> applicantIds);

    /**
     * 求职者侧：查询每家商家最新投递的职位名称（用于聊天会话关联职位）
     */
    @Select({
            "<script>",
            "SELECT j.merchant_id AS merchantId, j.title AS jobTitle",
            "FROM job_apply d",
            "JOIN job_post j ON j.id = d.job_id",
            "JOIN (",
            "  SELECT j2.merchant_id AS merchant_id, MAX(d2.id) AS max_id",
            "  FROM job_apply d2",
            "  JOIN job_post j2 ON j2.id = d2.job_id",
            "  WHERE d2.applicant_id = #{applicantId}",
            "  <if test='merchantIds != null and merchantIds.size > 0'>",
            "    AND j2.merchant_id IN",
            "    <foreach collection='merchantIds' item='id' open='(' separator=',' close=')'>",
            "      #{id}",
            "    </foreach>",
            "  </if>",
            "  GROUP BY j2.merchant_id",
            ") t ON t.max_id = d.id",
            "</script>"
    })
    List<ChatJobTitleRow> selectLatestJobTitleByMerchants(@Param("applicantId") Long applicantId,
                                                          @Param("merchantIds") List<Long> merchantIds);
}
