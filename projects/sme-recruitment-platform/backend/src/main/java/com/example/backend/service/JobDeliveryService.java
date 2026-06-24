/**
 * 文件速览：
 * 1. 文件职责：定义投递模块服务接口，覆盖投递提交、投递列表、状态更新与归属关系判断。
 * 2. 关键能力：本轮新增“商家与求职者是否存在真实投递关系”的复用判断。
 * 3. 主要调用方：投递控制器、举报控制器、附件简历授权控制器。
 * 4. 阅读建议：先看投递主流程方法，再看归属校验辅助方法。
 */
package com.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.entity.JobDelivery;
import com.example.backend.vo.DeliveryVO;
import com.example.backend.vo.MerchantDeliveryVO;
import java.util.List;

public interface JobDeliveryService extends IService<JobDelivery> {
    boolean submitDelivery(Long userId, Long jobId);
    List<DeliveryVO> getMyDeliveries(Long userId);

    // 商家端：分页获取候选人投递列表
    IPage<MerchantDeliveryVO> getMerchantDeliveries(Page<?> page, Long merchantId, Long jobId, Integer status, String degree);

    // 商家端：更新投递状态
    boolean updateDeliveryStatus(Long merchantId, Long deliveryId, Integer status, String feedback,
                                 java.time.LocalDateTime interviewTime, String interviewLocation,
                                 String interviewMethod, String interviewRemark);

    // 求职者端：分页获取投递记录
    IPage<DeliveryVO> getMyDeliveriesPage(Page<?> page, Long userId, Integer status, String keyword, String timeOrder);

    // 求职者端：查询职位投递状态
    Integer getDeliveryStatus(Long userId, Long jobId);

    // 归属校验：判断商家与求职者之间是否存在真实投递关系
    boolean hasDeliveryRelation(Long merchantId, Long applicantId);
}
