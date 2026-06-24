package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.entity.ApplicantVerification;
import com.example.backend.mapper.ApplicantVerificationMapper;
import com.example.backend.service.ApplicantVerificationService;
import org.springframework.stereotype.Service;

@Service
public class ApplicantVerificationServiceImpl extends ServiceImpl<ApplicantVerificationMapper, ApplicantVerification> implements ApplicantVerificationService {
}
