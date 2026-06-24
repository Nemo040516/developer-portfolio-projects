package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.JobInfo;
import com.example.backend.vo.AdminJobAuditVO;
import com.example.backend.vo.JobDetailVO;
import com.example.backend.vo.JobInfoVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobInfoMapper extends BaseMapper<JobInfo> {
    
    IPage<JobInfoVO> selectPublicList(Page<?> page, 
                                      @Param("keyword") String keyword,
                                      @Param("degree") String degree,
                                      @Param("experience") String experience,
                                      @Param("location") String location,
                                      @Param("categoryId") Long categoryId,
                                      @Param("minSalary") Integer minSalary,
                                      @Param("maxSalary") Integer maxSalary,
                                      @Param("city") String city,
                                      @Param("district") String district,
                                      @Param("industry") String industry,
                                      @Param("scale") String scale,
                                      @Param("financing") String financing,
                                      @Param("sort") String sort,
                                      @Param("smartDegreeCap") String smartDegreeCap,
                                      @Param("smartExperienceCap") String smartExperienceCap,
                                      @Param("smartMinSalary") Integer smartMinSalary);

    JobDetailVO selectPublicDetail(@Param("id") Long id);

    // 新增：商家端列表查询，包含分类名称
    IPage<JobInfo> selectMerchantJobList(Page<?> page,
                                         @Param("merchantId") Long merchantId,
                                         @Param("status") Integer status,
                                         @Param("auditStatus") Integer auditStatus);

    // 管理员：职位审核列表
    IPage<AdminJobAuditVO> selectAdminAuditList(Page<?> page,
                                                @Param("keyword") String keyword,
                                                @Param("status") Integer status,
                                                @Param("sortField") String sortField,
                                                @Param("sortOrder") String sortOrder,
                                                @Param("timeOrder") String timeOrder);
}
