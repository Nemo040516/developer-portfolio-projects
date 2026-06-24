package com.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.dto.JobPostDTO;
import com.example.backend.entity.JobInfo;
import com.example.backend.vo.JobDetailVO;
import com.example.backend.vo.JobInfoVO;

public interface JobInfoService extends IService<JobInfo> {
    void createJob(JobPostDTO dto, Long merchantId);
    void updateJob(JobPostDTO dto, Long merchantId);
    
    // 修改为 updateStatus，且只接收 id 和 status
    void updateStatus(Long id, Integer status);

    IPage<JobInfo> getMerchantJobList(Page<JobInfo> page, Long merchantId, Integer status, Integer auditStatus);

    // 商家端：提交复审（将职位重新置为待审核）
    void resubmitAudit(Long jobId, Long merchantId);

    // 公开接口：分页查询职位列表 (新增 location 参数)
    IPage<JobInfoVO> getPublicJobList(Page<JobInfoVO> page, String keyword, String degree, String experience, String location,
                                      Long categoryId, Integer minSalary, Integer maxSalary, String city, String district,
                                      String industry, String scale, String financing, String sort,
                                      String smartDegreeCap, String smartExperienceCap, Integer smartMinSalary);

    // 公开接口：查询职位详情
    JobDetailVO getPublicJobDetail(Long id);
}
