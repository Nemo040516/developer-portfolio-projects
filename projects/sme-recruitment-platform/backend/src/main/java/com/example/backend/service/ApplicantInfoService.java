package com.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.dto.ResumeSaveDTO;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.vo.ResumeVO;

public interface ApplicantInfoService extends IService<ApplicantInfo> {
    ApplicantInfo getByUserId(Long userId);

    boolean saveOrUpdateResume(Long userId, ResumeSaveDTO dto);

    ResumeVO getResume(Long userId);

    boolean updateResumeUrl(Long userId, String resumeUrl);

    String getResumeUrl(Long userId);
}
