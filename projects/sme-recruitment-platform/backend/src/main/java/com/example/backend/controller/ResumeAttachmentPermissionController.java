/**
 * 文件速览：
 * 1. 文件职责：处理附件简历授权申请、授权确认与状态查询。
 * 2. 关键升级：三类接口均要求商家与求职者之间存在真实投递关系，避免越权探测。
 * 3. 关键入口：/resume-attachment/permission/request、/grant、/status。
 * 4. 阅读建议：先看 request/grant/status 的主体校验，再看 buildVO 状态映射。
 */
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.ResumeAttachmentGrantDTO;
import com.example.backend.dto.ResumeAttachmentRequestDTO;
import com.example.backend.entity.ResumeAttachmentPermission;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.ResumeAttachmentPermissionService;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.ResumeAttachmentPermissionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 附件简历授权接口
 */
@RestController
@Validated
@RequestMapping("/resume-attachment/permission")
public class ResumeAttachmentPermissionController {

    @Autowired
    private ResumeAttachmentPermissionService permissionService;

    @Autowired
    private JobDeliveryService jobDeliveryService;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_GRANTED = 1;
    private static final int STATUS_REJECTED = 2;

    private Long requireMerchant(String forbiddenMessage) {
        return ControllerAccessUtils.requireMerchant(forbiddenMessage);
    }

    private Long requireApplicant(String forbiddenMessage) {
        return ControllerAccessUtils.requireApplicant(forbiddenMessage);
    }

    @PostMapping("/request")
    public Result<ResumeAttachmentPermissionVO> requestPermission(@RequestBody @Valid ResumeAttachmentRequestDTO dto) {
        Long merchantId = requireMerchant("仅商家可申请查看附件简历");
        if (!jobDeliveryService.hasDeliveryRelation(merchantId, dto.getApplicantId())) {
            return Result.error(403, "仅可向有投递关系的求职者申请附件简历");
        }

        ResumeAttachmentPermission record = permissionService.requestPermission(
                dto.getApplicantId(),
                merchantId,
                dto.getExpireTime()
        );
        return Result.success(buildVO(record));
    }

    @PostMapping("/grant")
    public Result<ResumeAttachmentPermissionVO> grantPermission(@RequestBody @Valid ResumeAttachmentGrantDTO dto) {
        Long applicantId = requireApplicant("仅求职者可授权附件简历");
        if (!jobDeliveryService.hasDeliveryRelation(dto.getMerchantId(), applicantId)) {
            return Result.error(403, "仅可向有投递关系的商家授权附件简历");
        }

        ResumeAttachmentPermission record = permissionService.grantPermission(
                applicantId,
                dto.getMerchantId(),
                dto.getExpireTime()
        );
        return Result.success(buildVO(record));
    }

    @GetMapping("/status")
    public Result<ResumeAttachmentPermissionVO> getStatus(
            @RequestParam @Positive(message = "求职者ID必须为正数") Long applicantId,
            @RequestParam @Positive(message = "商家ID必须为正数") Long merchantId
    ) {
        Long userId = ControllerAccessUtils.requireLogin();
        if (!userId.equals(applicantId) && !userId.equals(merchantId)) {
            return Result.error(403, "无权查看该授权状态");
        }
        if (!jobDeliveryService.hasDeliveryRelation(merchantId, applicantId)) {
            return Result.error(403, "当前双方不存在投递关系");
        }

        ResumeAttachmentPermission record = permissionService.getPermission(applicantId, merchantId);
        if (record == null) {
            ResumeAttachmentPermissionVO empty = new ResumeAttachmentPermissionVO();
            empty.setApplicantId(applicantId);
            empty.setMerchantId(merchantId);
            empty.setStatus("NONE");
            return Result.success(empty);
        }
        return Result.success(buildVO(record));
    }

    private ResumeAttachmentPermissionVO buildVO(ResumeAttachmentPermission record) {
        ResumeAttachmentPermissionVO vo = new ResumeAttachmentPermissionVO();
        if (record == null) {
            vo.setStatus("NONE");
            return vo;
        }
        vo.setApplicantId(record.getApplicantId());
        vo.setMerchantId(record.getMerchantId());
        vo.setExpireTime(record.getExpireTime());
        vo.setStatus(mapStatus(record.getStatus()));
        return vo;
    }

    private String mapStatus(Integer status) {
        if (status == null) {
            return "NONE";
        }
        if (status == STATUS_PENDING) {
            return "PENDING";
        }
        if (status == STATUS_GRANTED) {
            return "GRANTED";
        }
        if (status == STATUS_REJECTED) {
            return "REJECTED";
        }
        return "NONE";
    }
}
