/*
 * 文件速览：
 * 1. 文件职责：实现商家候选人库的分页列表与候选人详情查询。
 * 2. 关键升级：列表与详情返回前按求职者隐私设置自动脱敏联系方式与结构化简历字段。
 * 3. 关键依赖：ApplicantInfoMapper、SysUserMapper、ApplicantPrivacyGuard。
 * 4. 阅读建议：先看 getTalentPage / getTalentDetail，再看 toCandidateVO 和隐私收口逻辑。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.TalentPoolService;
import com.example.backend.support.ApplicantPrivacyGuard;
import com.example.backend.vo.TalentCandidateVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDate;
import java.time.Period;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TalentPoolServiceImpl implements TalentPoolService {

    @Autowired
    private ApplicantInfoMapper applicantInfoMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicantPrivacyGuard applicantPrivacyGuard;

    @Override
    public IPage<TalentCandidateVO> getTalentPage(Page<?> page, Long merchantId, String keyword, String expectJob, String city) {
        long current = page != null ? page.getCurrent() : 1;
        long size = page != null ? page.getSize() : 12;
        Page<ApplicantInfo> mpPage = new Page<>(current, size);

        LambdaQueryWrapper<ApplicantInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(expectJob)) {
            wrapper.eq(ApplicantInfo::getExpectJob, expectJob);
        }
        if (StringUtils.hasText(city)) {
            wrapper.eq(ApplicantInfo::getExpectCity, city);
        }
        if (StringUtils.hasText(keyword)) {
            String safeKeyword = keyword.trim();
            List<Long> matchUserIds = fetchApplicantUserIds(safeKeyword);
            wrapper.and(inner -> {
                inner.like(ApplicantInfo::getRealName, safeKeyword)
                        .or()
                        .like(ApplicantInfo::getSkills, safeKeyword)
                        .or()
                        .like(ApplicantInfo::getExpectJob, safeKeyword)
                        .or()
                        .like(ApplicantInfo::getAdvantage, safeKeyword);
                if (!CollectionUtils.isEmpty(matchUserIds)) {
                    inner.or().in(ApplicantInfo::getUserId, matchUserIds);
                }
            });
        }
        wrapper.orderByDesc(ApplicantInfo::getUpdateTime);

        IPage<ApplicantInfo> infoPage = applicantInfoMapper.selectPage(mpPage, wrapper);
        List<ApplicantInfo> records = infoPage.getRecords();

        Page<TalentCandidateVO> result = new Page<>(infoPage.getCurrent(), infoPage.getSize(), infoPage.getTotal());
        if (CollectionUtils.isEmpty(records)) {
            result.setRecords(new ArrayList<>());
            return result;
        }

        List<Long> userIds = records.stream()
                .map(ApplicantInfo::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<TalentCandidateVO> voList = records.stream()
                .map(info -> toCandidateVO(info, userMap.get(info.getUserId()), merchantId))
                .collect(Collectors.toList());

        result.setRecords(voList);
        return result;
    }

    @Override
    public TalentCandidateVO getTalentDetail(Long merchantId, Long userId) {
        if (merchantId == null || userId == null) {
            return null;
        }
        ApplicantInfo info = applicantInfoMapper.selectOne(new LambdaQueryWrapper<ApplicantInfo>()
                .eq(ApplicantInfo::getUserId, userId));
        if (info == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return toCandidateVO(info, user, merchantId);
    }

    private TalentCandidateVO toCandidateVO(ApplicantInfo info, SysUser user, Long merchantId) {
        TalentCandidateVO vo = new TalentCandidateVO();
        vo.setUserId(info.getUserId());
        vo.setName(resolveName(info, user));
        vo.setAvatar(user != null ? user.getAvatar() : null);
        vo.setGender(info.getGender());
        vo.setAge(calcAge(info.getBirthday()));
        vo.setPhone(info.getPhone());
        vo.setEmail(info.getEmail());
        vo.setCurrentIdentity(info.getCurrentIdentity());
        vo.setCurrentStatus(info.getCurrentStatus());
        vo.setDegree(info.getDegree());
        vo.setWorkYears(info.getWorkYears());
        vo.setCollege(info.getCollege());
        vo.setCollage(info.getCollege());
        vo.setMajor(info.getMajor());
        vo.setGradYear(info.getGradYear());
        vo.setCity(info.getExpectCity());
        vo.setExpectJob(info.getExpectJob());
        vo.setExpectSalary(info.getExpectSalary());
        vo.setSkills(splitSkills(info.getSkills()));
        vo.setSummary(info.getAdvantage());
        vo.setEducationJson(info.getEducationJson());
        vo.setExperienceJson(info.getExperienceJson());
        vo.setProjectJson(info.getProjectJson());
        vo.setCertificateJson(info.getCertificateJson());
        vo.setAwardJson(info.getAwardJson());
        vo.setLanguageJson(info.getLanguageJson());
        if (!applicantPrivacyGuard.canMerchantViewContact(merchantId, info.getUserId())) {
            applicantPrivacyGuard.maskTalentCandidate(vo);
        }
        return vo;
    }

    private String resolveName(ApplicantInfo info, SysUser user) {
        if (info != null && StringUtils.hasText(info.getRealName())) {
            return info.getRealName();
        }
        if (user != null && StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (user != null && StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "候选人";
    }

    private List<Long> fetchApplicantUserIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, Arrays.asList("APPLICANT", "STUDENT"))
                .and(wrapper -> wrapper.like(SysUser::getNickname, keyword)
                        .or()
                        .like(SysUser::getUsername, keyword)));
        if (CollectionUtils.isEmpty(users)) {
            return new ArrayList<>();
        }
        return users.stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> splitSkills(String skills) {
        if (!StringUtils.hasText(skills)) {
            return new ArrayList<>();
        }
        String trimmed = skills.trim();
        // 兼容历史 JSON 数组格式
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                List<String> parsed = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                if (!CollectionUtils.isEmpty(parsed)) {
                    return parsed.stream().filter(StringUtils::hasText).collect(Collectors.toList());
                }
            } catch (Exception ignored) {
                // 解析失败时继续走逗号拆分逻辑
            }
        }
        if (trimmed.startsWith("{")) {
            return new ArrayList<>();
        }
        return Arrays.stream(trimmed.split("[,，、]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 由出生日期计算年龄
     */
    private Integer calcAge(LocalDate birthday) {
        if (birthday == null) {
            return null;
        }
        try {
            return Period.between(birthday, LocalDate.now()).getYears();
        } catch (Exception ignored) {
            return null;
        }
    }
}
