package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.dto.ResumeSaveDTO;
import com.example.backend.entity.*;
import com.example.backend.mapper.*;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.SysUserService;
import com.example.backend.vo.ResumeVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 求职者档案服务实现类 (Refactored: 2026-02-13)
 * 已从 JSON 存储重构为标准 1:N 关系型表存储
 * 已修正字段拼写: collage -> college
 */
@Service
public class ApplicantInfoServiceImpl extends ServiceImpl<ApplicantInfoMapper, ApplicantInfo> implements ApplicantInfoService {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private ApplicantEducationMapper educationMapper;

    @Autowired
    private ApplicantExperienceMapper experienceMapper;

    @Autowired
    private ApplicantProjectMapper projectMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ApplicantInfo getByUserId(Long userId) {
        return lambdaQuery().eq(ApplicantInfo::getUserId, userId).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateResume(Long userId, ResumeSaveDTO dto) {
        if (dto == null) {
            dto = new ResumeSaveDTO();
        }

        // 仅更新前端明确提交的分段，避免旧客户端未传字段导致静默清空
        boolean hasEducationPayload = dto.getEducation() != null;
        boolean hasExperiencePayload = dto.getExperience() != null;
        boolean hasProjectPayload = dto.getProject() != null;
        boolean hasCertificatePayload = dto.getCertificate() != null;
        boolean hasAwardPayload = dto.getAward() != null;
        boolean hasLanguagePayload = dto.getLanguage() != null;

        // 统一做数据清洗，过滤空对象/空行
        List<ResumeSaveDTO.Education> normalizedEducation = hasEducationPayload ? normalizeEducationList(dto.getEducation()) : null;
        List<ResumeSaveDTO.Experience> normalizedExperience = hasExperiencePayload ? normalizeExperienceList(dto.getExperience()) : null;
        List<ResumeSaveDTO.Project> normalizedProject = hasProjectPayload ? normalizeProjectList(dto.getProject()) : null;
        List<ResumeSaveDTO.Certificate> normalizedCertificate = hasCertificatePayload ? normalizeCertificateList(dto.getCertificate()) : null;
        List<ResumeSaveDTO.Award> normalizedAward = hasAwardPayload ? normalizeAwardList(dto.getAward()) : null;
        List<ResumeSaveDTO.Language> normalizedLanguage = hasLanguagePayload ? normalizeLanguageList(dto.getLanguage()) : null;

        // 1. 保存/更新主表 (ApplicantInfo)
        ApplicantInfo info = getByUserId(userId);
        if (info == null) {
            info = new ApplicantInfo();
            info.setUserId(userId);
        }

        ResumeSaveDTO.BasicInfo basic = dto.getBasicInfo();
        if (basic != null) {
            info.setRealName(basic.getName());
            info.setGender(basic.getGender());
            info.setAdvantage(basic.getAdvantage());
            info.setBirthday(parseDate(basic.getBirthday()));
            info.setCurrentIdentity(basic.getCurrentIdentity());
            info.setWorkYears(basic.getWorkYears());
            info.setCurrentStatus(basic.getCurrentStatus());
            info.setExpectCity(basic.getExpectCity());
            info.setExpectSalary(basic.getExpectSalary());
            info.setExpectSalaryMin(basic.getExpectSalaryMin());
            info.setExpectSalaryMax(basic.getExpectSalaryMax());
            info.setExpectJob(basic.getExpectJob());
            info.setSkills(basic.getSkills());
        }

        // 同步联系方式
        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            info.setPhone(user.getPhone());
            info.setEmail(user.getEmail());
        }

        // 更新冗余字段 (用于列表快速展示)
        if (hasEducationPayload) {
            if (normalizedEducation != null && !normalizedEducation.isEmpty()) {
                ResumeSaveDTO.Education edu = normalizedEducation.get(0);
                info.setCollege(edu.getSchool()); // Fixed: collage -> college
                info.setMajor(edu.getMajor());
                info.setDegree(edu.getDegree());
                Integer gradYear = null;
                if (edu.getTimeRange() != null && edu.getTimeRange().size() >= 2) {
                    gradYear = parseYearFromMonth(edu.getTimeRange().get(1));
                }
                info.setGradYear(gradYear);
            } else {
                // 用户显式清空教育经历时，同步清空冗余学历字段
                info.setCollege(null);
                info.setMajor(null);
                info.setDegree(null);
                info.setGradYear(null);
            }
        }

        // 关键兼容：同步维护 JSON 字段，保障商家侧/聊天侧旧读取链路不回归
        if (hasEducationPayload) {
            info.setEducationJson(writeJsonSafely(normalizedEducation));
        }
        if (hasExperiencePayload) {
            info.setExperienceJson(writeJsonSafely(normalizedExperience));
        }
        if (hasProjectPayload) {
            info.setProjectJson(writeJsonSafely(normalizedProject));
        }
        if (hasCertificatePayload) {
            info.setCertificateJson(writeJsonSafely(normalizedCertificate));
        }
        if (hasAwardPayload) {
            info.setAwardJson(writeJsonSafely(normalizedAward));
        }
        if (hasLanguagePayload) {
            info.setLanguageJson(writeJsonSafely(normalizedLanguage));
        }

        this.saveOrUpdate(info);
        // MyBatis-Plus 默认更新策略可能忽略 null 字段，这里显式落库，避免清空教育后冗余字段残留
        if (hasEducationPayload && (normalizedEducation == null || normalizedEducation.isEmpty())) {
            this.lambdaUpdate()
                    .eq(ApplicantInfo::getUserId, userId)
                    .setSql("college = NULL, major = NULL, degree = NULL, grad_year = NULL")
                    .update();
        }

        // 2. 更新子表：教育经历 (策略：先删除旧数据，再插入新数据)
        if (hasEducationPayload) {
            educationMapper.delete(new LambdaQueryWrapper<ApplicantEducation>().eq(ApplicantEducation::getUserId, userId));
            for (ResumeSaveDTO.Education e : normalizedEducation) {
                ApplicantEducation entity = new ApplicantEducation();
                entity.setUserId(userId);
                entity.setSchool(e.getSchool());
                entity.setMajor(e.getMajor());
                entity.setDegree(e.getDegree());
                if (e.getTimeRange() != null && e.getTimeRange().size() >= 2) {
                    entity.setStartDate(e.getTimeRange().get(0));
                    entity.setEndDate(e.getTimeRange().get(1));
                }
                educationMapper.insert(entity);
            }
        }

        // 3. 更新子表：工作经历
        if (hasExperiencePayload) {
            experienceMapper.delete(new LambdaQueryWrapper<ApplicantExperience>().eq(ApplicantExperience::getUserId, userId));
            for (ResumeSaveDTO.Experience e : normalizedExperience) {
                ApplicantExperience entity = new ApplicantExperience();
                entity.setUserId(userId);
                entity.setCompany(e.getCompany());
                entity.setPosition(e.getPosition());
                entity.setContent(e.getContent());
                if (e.getTimeRange() != null && e.getTimeRange().size() >= 2) {
                    entity.setStartDate(e.getTimeRange().get(0));
                    entity.setEndDate(e.getTimeRange().get(1));
                }
                experienceMapper.insert(entity);
            }
        }

        // 4. 更新子表：项目经验
        if (hasProjectPayload) {
            projectMapper.delete(new LambdaQueryWrapper<ApplicantProject>().eq(ApplicantProject::getUserId, userId));
            for (ResumeSaveDTO.Project p : normalizedProject) {
                ApplicantProject entity = new ApplicantProject();
                entity.setUserId(userId);
                entity.setName(p.getName());
                entity.setRole(p.getRole());
                entity.setDescription(p.getDescription());
                if (p.getTimeRange() != null && p.getTimeRange().size() >= 2) {
                    entity.setStartDate(p.getTimeRange().get(0));
                    entity.setEndDate(p.getTimeRange().get(1));
                }
                projectMapper.insert(entity);
            }
        }

        return true;
    }

    @Override
    public ResumeVO getResume(Long userId) {
        ApplicantInfo info = getByUserId(userId);
        ResumeVO vo = new ResumeVO();
        vo.setBasicInfo(new ResumeVO.BasicInfo());
        vo.setEducation(new ArrayList<>());
        vo.setExperience(new ArrayList<>());
        vo.setProject(new ArrayList<>());
        vo.setCertificate(new ArrayList<>());
        vo.setAward(new ArrayList<>());
        vo.setLanguage(new ArrayList<>());

        if (info == null) {
            return vo;
        }

        // 1. 组装基本信息
        ResumeVO.BasicInfo basic = vo.getBasicInfo();
        basic.setName(info.getRealName());
        basic.setGender(info.getGender());
        basic.setAdvantage(info.getAdvantage());
        basic.setCollege(info.getCollege());
        basic.setCollage(info.getCollege());
        basic.setMajor(info.getMajor());
        basic.setDegree(info.getDegree());
        basic.setBirthday(formatDate(info.getBirthday()));
        basic.setCurrentIdentity(info.getCurrentIdentity());
        basic.setWorkYears(info.getWorkYears());
        basic.setCurrentStatus(info.getCurrentStatus());
        basic.setExpectCity(info.getExpectCity());
        basic.setExpectSalary(info.getExpectSalary());
        basic.setExpectSalaryMin(info.getExpectSalaryMin());
        basic.setExpectSalaryMax(info.getExpectSalaryMax());
        basic.setExpectJob(info.getExpectJob());
        basic.setSkills(info.getSkills());
        basic.setPhone(info.getPhone());
        basic.setEmail(info.getEmail());

        // 2. 查询子表：教育经历
        List<ApplicantEducation> eduList = educationMapper.selectList(
            new LambdaQueryWrapper<ApplicantEducation>().eq(ApplicantEducation::getUserId, userId)
        );
        vo.setEducation(eduList.stream().map(e -> {
            ResumeVO.Education ve = new ResumeVO.Education();
            ve.setSchool(e.getSchool());
            ve.setMajor(e.getMajor());
            ve.setDegree(e.getDegree());
            List<String> range = new ArrayList<>();
            if (e.getStartDate() != null) range.add(e.getStartDate());
            if (e.getEndDate() != null) range.add(e.getEndDate());
            ve.setTimeRange(range);
            return ve;
        }).collect(Collectors.toList()));

        // --- 核心修复：兼容平铺字段数据 (Legacy Data Fallback) ---
        // 如果子表没查到数据，但主表里有大学信息，则手动构造一条
        if (vo.getEducation().isEmpty() && (StringUtils.hasText(info.getCollege()) || StringUtils.hasText(info.getMajor()))) {
            ResumeVO.Education legacyEdu = new ResumeVO.Education();
            legacyEdu.setSchool(info.getCollege()); // Fixed: collage -> college
            legacyEdu.setMajor(info.getMajor());
            legacyEdu.setDegree(info.getDegree());
            // 尝试构建时间段
            List<String> range = new ArrayList<>();
            if (info.getGradYear() != null) {
                range.add((info.getGradYear() - 4) + "-09");
                range.add(info.getGradYear() + "-06");
            }
            legacyEdu.setTimeRange(range);
            vo.getEducation().add(legacyEdu);
        }

        // 3. 查询子表：工作经历
        List<ApplicantExperience> expList = experienceMapper.selectList(
            new LambdaQueryWrapper<ApplicantExperience>().eq(ApplicantExperience::getUserId, userId)
        );
        vo.setExperience(expList.stream().map(e -> {
            ResumeVO.Experience ve = new ResumeVO.Experience();
            ve.setCompany(e.getCompany());
            ve.setPosition(e.getPosition());
            ve.setContent(e.getContent());
            List<String> range = new ArrayList<>();
            if (StringUtils.hasText(e.getStartDate())) range.add(e.getStartDate());
            if (StringUtils.hasText(e.getEndDate())) range.add(e.getEndDate());
            ve.setTimeRange(range);
            return ve;
        }).collect(Collectors.toList()));

        // 4. 查询子表：项目经验
        List<ApplicantProject> projList = projectMapper.selectList(
            new LambdaQueryWrapper<ApplicantProject>().eq(ApplicantProject::getUserId, userId)
        );
        vo.setProject(projList.stream().map(p -> {
            ResumeVO.Project vp = new ResumeVO.Project();
            vp.setName(p.getName());
            vp.setRole(p.getRole());
            vp.setDescription(p.getDescription());
            List<String> range = new ArrayList<>();
            if (StringUtils.hasText(p.getStartDate())) range.add(p.getStartDate());
            if (StringUtils.hasText(p.getEndDate())) range.add(p.getEndDate());
            vp.setTimeRange(range);
            return vp;
        }).collect(Collectors.toList()));

        // 5. 证书/奖项/语言能力当前仍以 JSON 字段持久化
        vo.setCertificate(buildCertificateFromJson(info.getCertificateJson()));
        vo.setAward(buildAwardFromJson(info.getAwardJson()));
        vo.setLanguage(buildLanguageFromJson(info.getLanguageJson()));

        // 6. 兼容兜底：子表为空时，回退读取历史 JSON 字段
        if (vo.getEducation().isEmpty()) {
            vo.setEducation(buildEducationFromJson(info.getEducationJson()));
        }
        if (vo.getExperience().isEmpty()) {
            vo.setExperience(buildExperienceFromJson(info.getExperienceJson()));
        }
        if (vo.getProject().isEmpty()) {
            vo.setProject(buildProjectFromJson(info.getProjectJson()));
        }

        return vo;
    }

    @Override
    public boolean updateResumeUrl(Long userId, String resumeUrl) {
        ApplicantInfo info = getByUserId(userId);
        if (info == null) {
            if (resumeUrl == null) {
                return true;
            }
            info = new ApplicantInfo();
            info.setUserId(userId);
            info.setResumeUrl(resumeUrl);
            return this.save(info);
        }
        if (resumeUrl == null) {
            // 显式走 update 语句，避免 saveOrUpdate 的空值策略吞掉清空动作。
            return this.lambdaUpdate()
                    .eq(ApplicantInfo::getUserId, userId)
                    .set(ApplicantInfo::getResumeUrl, null)
                    .update();
        }
        info.setResumeUrl(resumeUrl);
        return this.updateById(info);
    }

    @Override
    public String getResumeUrl(Long userId) {
        ApplicantInfo info = getByUserId(userId);
        return info == null ? null : info.getResumeUrl();
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        try { return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE); } catch (Exception e) { return null; }
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 统一序列化 JSON（失败时返回空数组，避免写入非法字符串）
     */
    private String writeJsonSafely(Object value) {
        try {
            Object target = value == null ? Collections.emptyList() : value;
            return objectMapper.writeValueAsString(target);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<ResumeSaveDTO.Education> normalizeEducationList(List<ResumeSaveDTO.Education> source) {
        if (source == null) return new ArrayList<>();
        return source.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    ResumeSaveDTO.Education target = new ResumeSaveDTO.Education();
                    target.setSchool(trimToNull(item.getSchool()));
                    target.setMajor(trimToNull(item.getMajor()));
                    target.setDegree(trimToNull(item.getDegree()));
                    target.setTimeRange(normalizeTimeRange(item.getTimeRange()));
                    return target;
                })
                .filter(item -> hasAnyText(item.getSchool(), item.getMajor(), item.getDegree()) || !item.getTimeRange().isEmpty())
                .collect(Collectors.toList());
    }

    private List<ResumeSaveDTO.Experience> normalizeExperienceList(List<ResumeSaveDTO.Experience> source) {
        if (source == null) return new ArrayList<>();
        return source.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    ResumeSaveDTO.Experience target = new ResumeSaveDTO.Experience();
                    target.setCompany(trimToNull(item.getCompany()));
                    target.setPosition(trimToNull(item.getPosition()));
                    target.setContent(trimToNull(item.getContent()));
                    target.setTimeRange(normalizeTimeRange(item.getTimeRange()));
                    return target;
                })
                .filter(item -> hasAnyText(item.getCompany(), item.getPosition(), item.getContent()) || !item.getTimeRange().isEmpty())
                .collect(Collectors.toList());
    }

    private List<ResumeSaveDTO.Project> normalizeProjectList(List<ResumeSaveDTO.Project> source) {
        if (source == null) return new ArrayList<>();
        return source.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    ResumeSaveDTO.Project target = new ResumeSaveDTO.Project();
                    target.setName(trimToNull(item.getName()));
                    target.setRole(trimToNull(item.getRole()));
                    target.setDescription(trimToNull(item.getDescription()));
                    target.setTechStack(trimToNull(item.getTechStack()));
                    target.setTimeRange(normalizeTimeRange(item.getTimeRange()));
                    return target;
                })
                .filter(item -> hasAnyText(item.getName(), item.getRole(), item.getDescription(), item.getTechStack()) || !item.getTimeRange().isEmpty())
                .collect(Collectors.toList());
    }

    private List<ResumeSaveDTO.Certificate> normalizeCertificateList(List<ResumeSaveDTO.Certificate> source) {
        if (source == null) return new ArrayList<>();
        return source.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    ResumeSaveDTO.Certificate target = new ResumeSaveDTO.Certificate();
                    target.setName(trimToNull(item.getName()));
                    target.setIssuer(trimToNull(item.getIssuer()));
                    target.setDate(trimToNull(item.getDate()));
                    return target;
                })
                .filter(item -> hasAnyText(item.getName(), item.getIssuer(), item.getDate()))
                .collect(Collectors.toList());
    }

    private List<ResumeSaveDTO.Award> normalizeAwardList(List<ResumeSaveDTO.Award> source) {
        if (source == null) return new ArrayList<>();
        return source.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    ResumeSaveDTO.Award target = new ResumeSaveDTO.Award();
                    target.setName(trimToNull(item.getName()));
                    target.setLevel(trimToNull(item.getLevel()));
                    target.setDate(trimToNull(item.getDate()));
                    target.setDescription(trimToNull(item.getDescription()));
                    return target;
                })
                .filter(item -> hasAnyText(item.getName(), item.getLevel(), item.getDate(), item.getDescription()))
                .collect(Collectors.toList());
    }

    private List<ResumeSaveDTO.Language> normalizeLanguageList(List<ResumeSaveDTO.Language> source) {
        if (source == null) return new ArrayList<>();
        return source.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    ResumeSaveDTO.Language target = new ResumeSaveDTO.Language();
                    target.setName(trimToNull(item.getName()));
                    target.setLevel(trimToNull(item.getLevel()));
                    target.setScore(trimToNull(item.getScore()));
                    return target;
                })
                .filter(item -> hasAnyText(item.getName(), item.getLevel(), item.getScore()))
                .collect(Collectors.toList());
    }

    private List<String> normalizeTimeRange(List<String> range) {
        if (range == null || range.isEmpty()) {
            return new ArrayList<>();
        }
        return range.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .limit(2)
                .collect(Collectors.toList());
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if ("null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String asText(Object raw) {
        if (raw == null) {
            return null;
        }
        return trimToNull(String.valueOf(raw));
    }

    private boolean hasAnyText(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> parseRawJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            return rows == null ? new ArrayList<>() : rows;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> extractTimeRange(Object raw) {
        List<String> range = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String v = item == null ? null : String.valueOf(item).trim();
                if (StringUtils.hasText(v)) {
                    range.add(v);
                }
                if (range.size() >= 2) {
                    break;
                }
            }
            return range;
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            String normalized = text.trim();
            if (normalized.contains("~")) {
                String[] parts = normalized.split("~");
                for (String part : parts) {
                    String value = trimToNull(part);
                    if (value != null) range.add(value);
                    if (range.size() >= 2) break;
                }
            } else if (normalized.contains("至")) {
                String[] parts = normalized.split("至");
                for (String part : parts) {
                    String value = trimToNull(part);
                    if (value != null) range.add(value);
                    if (range.size() >= 2) break;
                }
            } else {
                range.add(normalized);
            }
        }
        return range;
    }

    private List<ResumeVO.Education> buildEducationFromJson(String json) {
        List<ResumeVO.Education> list = new ArrayList<>();
        for (Map<String, Object> row : parseRawJsonList(json)) {
            ResumeVO.Education item = new ResumeVO.Education();
            item.setSchool(asText(row.get("school")));
            item.setMajor(asText(row.get("major")));
            item.setDegree(asText(row.get("degree")));
            item.setTimeRange(extractTimeRange(row.get("timeRange")));
            if (hasAnyText(item.getSchool(), item.getMajor(), item.getDegree()) || !item.getTimeRange().isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    private List<ResumeVO.Experience> buildExperienceFromJson(String json) {
        List<ResumeVO.Experience> list = new ArrayList<>();
        for (Map<String, Object> row : parseRawJsonList(json)) {
            ResumeVO.Experience item = new ResumeVO.Experience();
            item.setCompany(asText(row.get("company")));
            String position = asText(row.get("position"));
            if (!StringUtils.hasText(position)) {
                position = asText(row.get("title"));
            }
            item.setPosition(position);
            item.setContent(asText(row.get("content")));
            item.setTimeRange(extractTimeRange(row.get("timeRange")));
            if (hasAnyText(item.getCompany(), item.getPosition(), item.getContent()) || !item.getTimeRange().isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    private List<ResumeVO.Project> buildProjectFromJson(String json) {
        List<ResumeVO.Project> list = new ArrayList<>();
        for (Map<String, Object> row : parseRawJsonList(json)) {
            ResumeVO.Project item = new ResumeVO.Project();
            item.setName(asText(row.get("name")));
            item.setRole(asText(row.get("role")));
            item.setDescription(asText(row.get("description")));
            item.setTechStack(asText(row.get("techStack")));
            item.setTimeRange(extractTimeRange(row.get("timeRange")));
            if (hasAnyText(item.getName(), item.getRole(), item.getDescription(), item.getTechStack()) || !item.getTimeRange().isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    private List<ResumeVO.Certificate> buildCertificateFromJson(String json) {
        List<ResumeVO.Certificate> list = new ArrayList<>();
        for (Map<String, Object> row : parseRawJsonList(json)) {
            ResumeVO.Certificate item = new ResumeVO.Certificate();
            item.setName(asText(row.get("name")));
            item.setIssuer(asText(row.get("issuer")));
            item.setDate(asText(row.get("date")));
            if (hasAnyText(item.getName(), item.getIssuer(), item.getDate())) {
                list.add(item);
            }
        }
        return list;
    }

    private List<ResumeVO.Award> buildAwardFromJson(String json) {
        List<ResumeVO.Award> list = new ArrayList<>();
        for (Map<String, Object> row : parseRawJsonList(json)) {
            ResumeVO.Award item = new ResumeVO.Award();
            item.setName(asText(row.get("name")));
            item.setLevel(asText(row.get("level")));
            item.setDate(asText(row.get("date")));
            item.setDescription(asText(row.get("description")));
            if (hasAnyText(item.getName(), item.getLevel(), item.getDate(), item.getDescription())) {
                list.add(item);
            }
        }
        return list;
    }

    private List<ResumeVO.Language> buildLanguageFromJson(String json) {
        List<ResumeVO.Language> list = new ArrayList<>();
        for (Map<String, Object> row : parseRawJsonList(json)) {
            ResumeVO.Language item = new ResumeVO.Language();
            item.setName(asText(row.get("name")));
            item.setLevel(asText(row.get("level")));
            item.setScore(asText(row.get("score")));
            if (hasAnyText(item.getName(), item.getLevel(), item.getScore())) {
                list.add(item);
            }
        }
        return list;
    }

    private Integer parseYearFromMonth(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            if (text.length() >= 4) {
                return Integer.parseInt(text.substring(0, 4));
            }
            return Integer.parseInt(text);
        } catch (Exception e) {
            return null;
        }
    }
}
