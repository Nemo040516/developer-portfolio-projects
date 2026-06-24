<!--
文件速览：
1. 文件职责：求职者在线简历编辑器，负责侧栏导航、分段编辑、附件上传与附件预览。
2. 页面入口：求职者“我的简历/在线简历编辑器”页面。
3. 关键结构：workspace-header、workspace-sidebar、hero-facts、section-guide、previewDialog。
4. 阅读建议：先看左侧进度与导航，再看基础资料/求职意向摘要面板，最后看时间轴区块与附件预览。
-->
<template>
  <div class="resume-workspace">
    <!-- 1. 顶部控制栏 (Apple Flat Header) -->
    <header class="workspace-header">
      <div class="header-left">
        <h2 class="workspace-title">在线简历编辑器</h2>
        <div class="sync-status">
          <el-icon :color="hasAnyEditing ? '#ff9f0a' : '#34c759'"><CircleCheck /></el-icon>
          <span>{{ hasAnyEditing ? '正在编辑中，记得保存到云端' : '云端数据已同步' }}</span>
        </div>
      </div>
      <div class="header-right">
        <div class="completeness-pill">
          <span class="dot"></span>
          简历完善度 {{ resumeCompleteness }}%
        </div>
        <el-button type="primary" round class="sync-btn" @click="saveResume">保存到云端</el-button>
      </div>
    </header>

    <div class="workspace-main" ref="contentWrap" @scroll="handleScroll">
      <!-- 2. 左侧快捷导航 -->
      <aside class="workspace-sidebar">
        <div class="nav-list">
          <div 
            v-for="item in navItems" 
            :key="item.id"
            :class="['nav-item', { active: activeSection === item.id }]"
            @click="scrollToSection(item.id)"
          >
            <div class="nav-icon"><el-icon><component :is="item.icon" /></el-icon></div>
            <span>{{ item.label }}</span>
          </div>
        </div>
        <div class="sidebar-progress-card">
          <div class="sidebar-progress-head">
            <span>当前进度</span>
            <strong>{{ resumeCompleteness }}%</strong>
          </div>
          <div class="sidebar-progress-meta">已完成 {{ completedSectionCount }}/{{ resumeSectionChecklist.length }} 个模块</div>
          <el-progress :percentage="resumeCompleteness" :stroke-width="8" :show-text="false" />
          <div class="sidebar-progress-copy">
            {{ primaryResumeTask ? primaryResumeTask.desc : '主要模块已经补齐，可以继续润色内容表达。' }}
          </div>
          <el-button
            v-if="primaryResumeTask"
            type="primary"
            plain
            class="sidebar-progress-btn"
            @click="jumpToPrimarySection"
          >
            {{ primaryResumeTask.actionLabel }}
          </el-button>
          <div class="sidebar-checklist">
            <button
              v-for="item in resumeSectionChecklist"
              :key="`check-${item.id}`"
              type="button"
              :class="['sidebar-check-item', { done: item.done, active: activeSection === item.id }]"
              @click="focusResumeSection(item.id)"
            >
              <span class="check-dot"></span>
              <span class="check-label">{{ item.label }}</span>
            </button>
          </div>
        </div>
      </aside>

      <!-- 3. 右侧内容流 -->
      <main class="workspace-content">
        <!-- 基础资料 -->
        <section id="basic" class="resume-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('basic').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">基础资料</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('basic').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('basic').tagType">{{ getSectionStatus('basic').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('basic').hint }}</span>
                </div>
              </div>
              <el-button link type="primary" @click="toggleEdit('basic')">{{ isEditing.basic ? '完成编辑' : '编辑' }}</el-button>
            </div>
            
            <div v-if="!isEditing.basic" class="read-view hero-block">
              <div class="hero-panel">
                <el-avatar :size="72" :src="userStore.userInfo.avatar" class="avatar-shadow" />
                <div class="hero-info">
                  <h3>{{ resume.basicInfo.name || '姓名未填' }}</h3>
                  <p class="meta-line">{{ currentIdentityLabel }} · {{ resume.basicInfo.workYears || '经验待补充' }}</p>
                  <div class="contact-tags">
                    <span class="c-tag"><el-icon><Phone /></el-icon>{{ resume.basicInfo.phone || '手机号待补充' }}</span>
                    <span class="c-tag"><el-icon><Message /></el-icon>{{ resume.basicInfo.email || '邮箱待补充' }}</span>
                  </div>
                  <div class="hero-badge-row">
                    <span v-for="item in basicSummaryBadges" :key="item.label" class="hero-badge">{{ item.label }} · {{ item.value }}</span>
                  </div>
                </div>
              </div>
              <div class="hero-facts">
                <div v-for="item in basicOverviewItems" :key="item.label" class="hero-fact-card">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>
            </div>
            
            <div v-else class="edit-view">
              <el-form :model="resume.basicInfo" label-position="top">
                <el-row :gutter="24">
                  <el-col :span="8"><el-form-item label="真实姓名"><el-input v-model="resume.basicInfo.name" /></el-form-item></el-col>
                  <el-col :span="8">
                    <el-form-item label="性别">
                      <el-radio-group v-model="resume.basicInfo.gender">
                        <el-radio :label="1">男</el-radio><el-radio :label="2">女</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                  <el-col :span="8"><el-form-item label="出生日期"><el-date-picker v-model="resume.basicInfo.birthday" type="date" style="width:100%" value-format="YYYY-MM-DD" /></el-form-item></el-col>
                </el-row>
                <el-row :gutter="24">
                  <el-col :span="8"><el-form-item label="当前身份"><el-select v-model="resume.basicInfo.currentIdentity" style="width:100%"><el-option label="在校生" value="STUDENT" /><el-option label="应届生" value="FRESH_GRAD" /><el-option label="职场人士" value="WORKER" /></el-select></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="工作年限"><el-input v-model="resume.basicInfo.workYears" placeholder="如: 3年 / 在校生" /></el-form-item></el-col>
                </el-row>
              </el-form>
            </div>
          </div>
        </section>

        <!-- 求职意向 -->
        <section id="intent" class="resume-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('intent').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">求职意向</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('intent').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('intent').tagType">{{ getSectionStatus('intent').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('intent').hint }}</span>
                </div>
              </div>
              <el-button link type="primary" @click="toggleEdit('intent')">{{ isEditing.intent ? '完成编辑' : '编辑' }}</el-button>
            </div>
            <div v-if="!isEditing.intent" class="read-view intent-read">
              <div class="intent-banner">
                <div class="intent-banner-title">当前求职画像</div>
                <div class="intent-banner-desc">{{ intentHeadline }}</div>
              </div>
              <div class="field-grid intent-grid">
                <div
                  v-for="item in intentOverviewItems"
                  :key="item.label"
                  :class="['field-box', 'intent-box', { 'is-filled': item.value !== '未填' }]"
                >
                  <label>{{ item.label }}</label>
                  <span>{{ item.value }}</span>
                </div>
              </div>
            </div>
            <div v-else class="edit-view">
              <el-form :model="resume.basicInfo" label-position="top">
                <el-row :gutter="24">
                  <el-col :span="12"><el-form-item label="期望职位"><el-input v-model="resume.basicInfo.expectJob" /></el-form-item></el-col>
                  <el-col :span="12">
                    <el-form-item label="期望薪资 (k)">
                      <div style="display:flex; gap:8px; align-items:center;">
                        <el-input-number v-model="resume.basicInfo.expectSalaryMin" :min="1" :max="99" style="width:100%" placeholder="最低" controls-position="right" />
                        <span style="color:#86868b">-</span>
                        <el-input-number v-model="resume.basicInfo.expectSalaryMax" :min="1" :max="99" style="width:100%" placeholder="最高" controls-position="right" />
                      </div>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="24">
                  <el-col :span="12"><el-form-item label="期望城市"><el-input v-model="resume.basicInfo.expectCity" /></el-form-item></el-col>
                  <el-col :span="12">
                    <el-form-item label="求职状态">
                      <el-select v-model="resume.basicInfo.currentStatus" placeholder="请选择当前状态" style="width:100%">
                        <el-option v-for="opt in JOB_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>
          </div>
        </section>

        <!-- 专业技能 -->
        <section id="skills" class="resume-section skills-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('skills').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">专业技能</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('skills').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('skills').tagType">{{ getSectionStatus('skills').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('skills').hint }}</span>
                </div>
              </div>
              <el-button link type="primary" @click="toggleEdit('skills')">{{ isEditing.skills ? '完成编辑' : '编辑' }}</el-button>
            </div>
            <div v-if="!isEditing.skills" class="read-view skills-read">
              <div class="skills-summary">
                <strong>{{ skillTags.length }}</strong>
                <span>{{ skillTags.length ? '个技能关键词已整理，可继续补充岗位专属词。' : '建议先整理一组岗位关键词，提高简历被检索命中的机会。' }}</span>
              </div>
              <div v-if="skillTags.length" class="skills-cloud">
                <span v-for="skill in skillTags" :key="skill" class="skill-chip">{{ skill }}</span>
              </div>
              <el-empty v-else :image-size="40" description="还没有整理技能关键词" />
              <div class="skills-tip">建议优先保留与你目标岗位最相关的 6 到 10 个关键词。</div>
            </div>
            <div v-else class="edit-view">
              <el-form :model="resume.basicInfo" label-position="top">
                <el-form-item label="技能关键词">
                  <el-input
                    v-model="resume.basicInfo.skills"
                    type="textarea"
                    :rows="4"
                    placeholder="请用逗号、顿号或换行分隔，例如：门店运营、用户沟通、排班管理、数据分析"
                  />
                </el-form-item>
              </el-form>
              <div v-if="skillTags.length" class="skills-preview">
                <div class="skills-preview-label">实时预览</div>
                <div class="skills-cloud">
                  <span v-for="skill in skillTags" :key="`preview-${skill}`" class="skill-chip is-preview">{{ skill }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 教育经历 -->
        <section id="education" class="resume-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('education').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">教育经历</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('education').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('education').tagType">{{ getSectionStatus('education').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('education').hint }}</span>
                </div>
              </div>
              <el-button link type="primary" icon="Plus" @click="addItem('education')">添加教育</el-button>
            </div>
            <div v-if="resume.education.length" class="timeline-wrap">
              <div v-for="(edu, index) in resume.education" :key="index" class="timeline-node">
                <div class="node-marker"></div>
                <div class="node-main">
                  <!-- 阅读态 -->
                  <template v-if="!isEditing[`edu_${index}`]">
                    <div class="node-header">
                      <h4>{{ edu.school || '未填学校' }}</h4>
                      <div class="node-ops">
                        <el-button link type="primary" @click="toggleEdit(`edu_${index}`)">编辑</el-button>
                        <el-button link type="danger" icon="Delete" @click="removeItem('education', index)" />
                      </div>
                    </div>
                    <div class="node-body">
                      <p>
                        {{ edu.major || '专业未填' }} · {{ edu.degree || '学历未填' }}
                        <el-tag v-if="getDegreeRank(edu.degree) >= 4" size="small" effect="dark" type="warning" style="margin-left:8px">高学历</el-tag>
                      </p>
                      <small>{{ formatRange(edu.timeRange) }}</small>
                    </div>
                  </template>
                  
                  <!-- 编辑态 -->
                  <div v-else class="edit-form-box">
                    <div class="node-header" style="margin-bottom:16px; border-bottom:1px solid #eee; padding-bottom:8px;">
                      <span style="font-size:14px; font-weight:600; color:#007aff;">编辑教育经历</span>
                      <el-button link type="primary" @click="toggleEdit(`edu_${index}`)">完成编辑</el-button>
                    </div>
                    <el-form label-position="top">
                      <el-row :gutter="20">
                        <el-col :span="12"><el-form-item label="学校名称"><el-input v-model="edu.school" placeholder="请输入学校" /></el-form-item></el-col>
                        <el-col :span="12">
                          <el-form-item label="学历学位">
                            <el-select v-model="edu.degree" style="width:100%">
                              <el-option v-for="d in DEGREE_LEVELS" :key="d.value" :label="d.label" :value="d.value" />
                            </el-select>
                          </el-form-item>
                        </el-col>
                      </el-row>
                      <el-row :gutter="20">
                        <el-col :span="12"><el-form-item label="主修专业"><el-input v-model="edu.major" /></el-form-item></el-col>
                        <el-col :span="12">
                          <el-form-item label="就读时间">
                            <el-date-picker 
                              v-model="edu.timeRange" 
                              type="monthrange" 
                              value-format="YYYY-MM" 
                              style="width:100%" 
                            />
                          </el-form-item>
                        </el-col>
                      </el-row>
                    </el-form>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else :image-size="40" description="暂无教育经历" />
          </div>
        </section>

        <!-- 工作经历 -->
        <section id="experience" class="resume-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('experience').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">工作经历</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('experience').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('experience').tagType">{{ getSectionStatus('experience').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('experience').hint }}</span>
                </div>
              </div>
              <el-button link type="primary" icon="Plus" @click="addItem('experience')">添加经历</el-button>
            </div>
            <div v-if="resume.experience.length" class="timeline-wrap">
              <div v-for="(exp, index) in resume.experience" :key="index" class="timeline-node">
                <div class="node-marker"></div>
                <div class="node-main">
                  <!-- 阅读态 -->
                  <template v-if="!isEditing[`exp_${index}`]">
                    <div class="node-header">
                      <h4>{{ exp.company || '未填公司' }}</h4>
                      <div class="node-ops">
                        <el-button link type="primary" @click="toggleEdit(`exp_${index}`)">编辑</el-button>
                        <el-button link type="danger" icon="Delete" @click="removeItem('experience', index)" />
                      </div>
                    </div>
                    <div class="node-body">
                      <p>{{ exp.position || '职位未填' }}</p>
                      <small>{{ formatRange(exp.timeRange) }}</small>
                      <p class="content-text">{{ exp.content || '工作内容未填' }}</p>
                    </div>
                  </template>

                  <!-- 编辑态 -->
                  <div v-else class="edit-form-box">
                    <div class="node-header" style="margin-bottom:16px; border-bottom:1px solid #eee; padding-bottom:8px;">
                      <span style="font-size:14px; font-weight:600; color:#007aff;">编辑工作经历</span>
                      <el-button link type="primary" @click="toggleEdit(`exp_${index}`)">完成编辑</el-button>
                    </div>
                    <el-form label-position="top">
                      <el-row :gutter="20">
                        <el-col :span="12"><el-form-item label="公司名称"><el-input v-model="exp.company" /></el-form-item></el-col>
                        <el-col :span="12"><el-form-item label="担任职位"><el-input v-model="exp.position" /></el-form-item></el-col>
                      </el-row>
                      <el-row :gutter="20">
                        <el-col :span="12">
                          <el-form-item label="在职时间">
                            <el-date-picker 
                              v-model="exp.timeRange" 
                              type="monthrange" 
                              value-format="YYYY-MM" 
                              style="width:100%" 
                            />
                          </el-form-item>
                        </el-col>
                      </el-row>
                      <el-form-item label="工作内容">
                        <el-input v-model="exp.content" type="textarea" :rows="4" placeholder="描述您的主要工作职责和业绩..." />
                      </el-form-item>
                    </el-form>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else :image-size="40" description="暂无工作经历" />
          </div>
        </section>

        <!-- 项目经验 -->
        <section id="project" class="resume-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('project').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">项目经验</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('project').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('project').tagType">{{ getSectionStatus('project').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('project').hint }}</span>
                </div>
              </div>
              <el-button link type="primary" icon="Plus" @click="addItem('project')">添加项目</el-button>
            </div>
            <div v-if="resume.project.length" class="timeline-wrap">
              <div v-for="(proj, index) in resume.project" :key="index" class="timeline-node">
                <div class="node-marker"></div>
                <div class="node-main">
                  <!-- 阅读态 -->
                  <template v-if="!isEditing[`pro_${index}`]">
                    <div class="node-header">
                      <h4>{{ proj.name || '未填项目' }}</h4>
                      <div class="node-ops">
                        <el-button link type="primary" @click="toggleEdit(`pro_${index}`)">编辑</el-button>
                        <el-button link type="danger" icon="Delete" @click="removeItem('project', index)" />
                      </div>
                    </div>
                    <div class="node-body">
                      <p><strong>角色：</strong>{{ proj.role || '未填' }}</p>
                      <small v-if="proj.timeRange">{{ formatRange(proj.timeRange) }}</small>
                      <p class="content-text">{{ proj.description || '项目描述未填' }}</p>
                    </div>
                  </template>

                  <!-- 编辑态 -->
                  <div v-else class="edit-form-box">
                    <div class="node-header" style="margin-bottom:16px; border-bottom:1px solid #eee; padding-bottom:8px;">
                      <span style="font-size:14px; font-weight:600; color:#007aff;">编辑项目经验</span>
                      <el-button link type="primary" @click="toggleEdit(`pro_${index}`)">完成编辑</el-button>
                    </div>
                    <el-form label-position="top">
                      <el-row :gutter="20">
                        <el-col :span="12"><el-form-item label="项目名称"><el-input v-model="proj.name" /></el-form-item></el-col>
                        <el-col :span="12"><el-form-item label="担任角色"><el-input v-model="proj.role" /></el-form-item></el-col>
                      </el-row>
                      <el-row :gutter="20">
                        <el-col :span="12">
                          <el-form-item label="项目时间">
                            <el-date-picker 
                              v-model="proj.timeRange" 
                              type="monthrange" 
                              value-format="YYYY-MM" 
                              style="width:100%" 
                            />
                          </el-form-item>
                        </el-col>
                      </el-row>
                      <el-form-item label="项目描述">
                        <el-input v-model="proj.description" type="textarea" :rows="4" placeholder="描述项目背景、您的职责及成果..." />
                      </el-form-item>
                    </el-form>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else :image-size="40" description="暂无项目经验" />
          </div>
        </section>

        <!-- 附件简历 -->
        <section id="attachment" class="resume-section">
          <div class="apple-card" :class="{ 'is-pending': !getSectionStatus('attachment').done }">
            <div class="card-header">
              <div class="card-header-main">
                <span class="card-title">附件简历</span>
                <div class="section-guide" :class="{ pending: !getSectionStatus('attachment').done }">
                  <el-tag size="small" effect="plain" :type="getSectionStatus('attachment').tagType">{{ getSectionStatus('attachment').label }}</el-tag>
                  <span class="section-guide-text">{{ getSectionStatus('attachment').hint }}</span>
                </div>
              </div>
            </div>
            <div class="attachment-upload">
              <div v-if="resumeAttachment.url" class="attachment-info-card">
                <div class="file-icon-box">
                  <el-icon :size="32" color="#0a84ff" v-if="isPdf(resumeAttachment.name)"><Document /></el-icon>
                  <el-icon :size="32" color="#2563eb" v-else-if="isWord(resumeAttachment.name)"><DocumentCopy /></el-icon>
                  <el-icon :size="32" color="#0a84ff" v-else><Files /></el-icon>
                </div>
                <div class="file-details">
                  <div class="file-name">{{ resumeAttachment.name }}</div>
                  <div class="file-meta">支持在线查看（PDF/图片）与下载</div>
                </div>
                <div class="file-actions">
                  <el-button round size="small" @click="handlePreview">
                    <el-icon style="margin-right:4px"><View /></el-icon>预览
                  </el-button>
                  <el-button round size="small" plain type="primary" @click="downloadCurrentAttachment">
                    <el-icon style="margin-right:4px"><Download /></el-icon>下载
                  </el-button>
                  <el-button round size="small" type="danger" plain @click="removeAttachment">
                    <el-icon style="margin-right:4px"><Delete /></el-icon>删除
                  </el-button>
                </div>
              </div>
              
              <el-upload
                v-else
                class="ios-upload-area"
                drag
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleFileUpload"
              >
                <div class="upload-content">
                  <div class="upload-icon-circle">
                    <el-icon :size="24" color="#007aff"><UploadFilled /></el-icon>
                  </div>
                  <div class="upload-text">
                    <h4>点击或拖拽上传简历</h4>
                    <p>支持 PDF、Word 格式，最大 10MB</p>
                  </div>
                </div>
              </el-upload>
            </div>
          </div>
        </section>
      </main>
    </div>

    <!-- 附件预览弹窗 -->
    <el-dialog
      v-model="previewDialog.visible"
      :title="previewDialog.title"
      width="min(960px, 94vw)"
      class="preview-dialog"
      align-center
      destroy-on-close
    >
      <div class="preview-container" v-loading="previewDialog.loading">
        <!-- PDF 预览 -->
        <iframe 
          v-if="previewDialog.type === 'pdf'" 
          :src="previewDialog.url" 
          type="application/pdf"
          class="preview-iframe"
        ></iframe>

        <!-- 图片预览 -->
        <img 
          v-else-if="previewDialog.type === 'image'" 
          :src="previewDialog.url" 
          class="preview-image"
        />

        <!-- Word/其他格式 不支持预览 -->
        <div v-else class="preview-placeholder">
          <div class="placeholder-icon">
            <el-icon :size="64" color="#86868b"><DocumentDelete /></el-icon>
          </div>
          <h3>该文件格式暂不支持在线预览</h3>
          <p>Word 文档受限于浏览器安全策略，请下载到本地查看。</p>
          <el-button type="primary" round @click="downloadFile(previewDialog.url, previewDialog.title)">
            <el-icon style="margin-right:6px"><Download /></el-icon> 下载文件
          </el-button>
        </div>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  CircleCheck, User, Suitcase, School, Star, Connection, Files, Phone, Message, 
  Plus, Delete, UploadFilled, View, Document, DocumentCopy, DocumentDelete, Download 
} from '@element-plus/icons-vue'
import { getMyResume, saveMyResume, getResumeAttachment, uploadResumeAttachment, deleteResumeAttachment } from '@/api/applicant'
import { useUserStore } from '@/stores/user'
import { formatFileUrl, canPreviewFile, getFilePreviewType, getFileName, downloadFileByUrl } from '@/utils/file'

const userStore = useUserStore()
const activeSection = ref('basic')
const contentWrap = ref(null)

const SECTION_SCROLL_OFFSET = 12
const SECTION_ACTIVE_OFFSET = 96
let sectionSwitchTimer = null
let scrollRafId = null
let sectionObserver = null

const navItems = [
  { id: 'basic', label: '基础资料', icon: 'User' },
  { id: 'intent', label: '求职意向', icon: 'Connection' },
  { id: 'skills', label: '专业技能', icon: 'Star' },
  { id: 'education', label: '教育经历', icon: 'School' },
  { id: 'experience', label: '工作经历', icon: 'Suitcase' },
  { id: 'project', label: '项目经验', icon: 'Star' },
  { id: 'attachment', label: '附件简历', icon: 'Files' }
]

const isEditing = reactive({ basic: false, intent: false, skills: false })
const toggleEdit = (key) => isEditing[key] = !isEditing[key]
const hasAnyEditing = computed(() => Object.values(isEditing).some(Boolean))

const resume = reactive({
  basicInfo: { name: '', gender: 1, phone: '', email: '', collage: '', college: '', major: '', degree: '', birthday: '', currentIdentity: 'WORKER', workYears: '', currentStatus: '', expectCity: '', expectSalary: '', expectSalaryMin: undefined, expectSalaryMax: undefined, expectJob: '', skills: '' },
  education: [], experience: [], project: []
})

const resumeAttachment = reactive({ url: '', name: '' })

// 预览弹窗状态
const previewDialog = reactive({
  visible: false,
  title: '简历预览',
  url: '',
  type: 'pdf', // pdf, image, other
  loading: false
})

// --- 核心逻辑 ---

// 判断文件类型（统一复用全局工具）
const getFileType = (fileName) => getFilePreviewType(fileName)

const isPdf = (name) => getFileType(name) === 'pdf'
const isWord = (name) => {
  if (!name) return false
  const ext = name.split('.').pop().toLowerCase()
  return ['doc', 'docx'].includes(ext)
}

const handlePreview = () => {
  if (!resumeAttachment.url) return
  const fileIdentity = resumeAttachment.name || resumeAttachment.url
  if (!canPreviewFile(fileIdentity)) {
    ElMessage.info('该文件格式暂不支持在线查看，请使用下载按钮')
    return
  }
  // 统一使用全局文件地址补全逻辑，避免环境变量不一致导致预览失效
  previewDialog.url = formatFileUrl(resumeAttachment.url)
  previewDialog.title = resumeAttachment.name || '附件简历'
  previewDialog.type = getFileType(fileIdentity)
  previewDialog.visible = true
}

const downloadFile = async (url, fileName = '') => {
  if (!url) return
  try {
    await downloadFileByUrl(url, fileName || getFileName(url))
    ElMessage.success('开始下载附件')
  } catch (error) {
    console.error('下载附件失败:', error)
    window.open(formatFileUrl(url), '_blank')
    ElMessage.warning('下载通道异常，已尝试在新窗口打开附件')
  }
}

const downloadCurrentAttachment = () => {
  if (!resumeAttachment.url) {
    ElMessage.warning('暂无附件简历')
    return
  }
  downloadFile(resumeAttachment.url, resumeAttachment.name || getFileName(resumeAttachment.url))
}

// ... (原有 loadResume 等逻辑保持不变)
// --- 核心逻辑：加载并自愈数据 ---
const loadResume = async () => {
  try {
    const res = await getMyResume()
    if (res.data) {
      Object.assign(resume.basicInfo, res.data.basicInfo || {})
      
      let edus = (res.data.education || []).filter(e => e.school || e.major)
      const collegeValue = res.data.basicInfo?.college || res.data.basicInfo?.collage
      if (edus.length === 0 && (collegeValue || res.data.basicInfo.major)) {
        edus = [{
          school: collegeValue,
          major: res.data.basicInfo.major,
          degree: res.data.basicInfo.degree,
          timeRange: []
        }]
      }
      resume.education = edus
      resume.experience = (res.data.experience || []).filter(e => e.company || e.position || e.content)
      resume.project = (res.data.project || []).filter(p => p.name || p.role)
    }

    const attachRes = await getResumeAttachment()
    if (attachRes.data) {
      resumeAttachment.url = attachRes.data.fileUrl
      resumeAttachment.name = attachRes.data.fileName
    }
  } catch (e) { console.error('加载失败', e) }
}

const handleFileUpload = async (file) => {
  try {
    const res = await uploadResumeAttachment(file.raw)
    resumeAttachment.url = res.data.fileUrl
    resumeAttachment.name = res.data.fileName
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

const removeAttachment = async () => {
  try {
    await deleteResumeAttachment()
    resumeAttachment.url = ''
    resumeAttachment.name = ''
    ElMessage.success('删除成功')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

// downloadAttachment 函数已被 handlePreview 替代

const formatRange = (r) => {
  if (typeof r === 'string') return r
  return (Array.isArray(r) && r.length >= 2) ? `${r[0]} ~ ${r[1]}` : '时间未填'
}

const resumeCompleteness = computed(() => {
  let score = 0; const info = resume.basicInfo
  if (info.name) score += 20; if (info.expectJob) score += 20
  if (resume.education.length) score += 20; if (resume.experience.length) score += 20; if (info.skills) score += 20
  return Math.min(score, 100)
})

const skillTags = computed(() => {
  const raw = String(resume.basicInfo.skills || '')
  return [...new Set(
    raw
      .split(/[\n,，、/]/)
      .map(item => item.trim())
      .filter(Boolean)
  )]
})

// 简历结构状态：用于侧栏进度与优先任务引导
const resumeSectionChecklist = computed(() => ([
  {
    id: 'basic',
    label: '基础资料',
    done: Boolean(resume.basicInfo.name && (resume.basicInfo.phone || resume.basicInfo.email)),
    desc: '先补齐姓名与联系方式，确保企业能形成最基础的人物画像。',
    actionLabel: '去补基础资料'
  },
  {
    id: 'intent',
    label: '求职意向',
    done: Boolean(
      resume.basicInfo.expectJob
      && resume.basicInfo.expectCity
      && (resume.basicInfo.expectSalary || (resume.basicInfo.expectSalaryMin && resume.basicInfo.expectSalaryMax))
    ),
    desc: '岗位、城市和薪资不清晰时，推荐与投递都会变钝。',
    actionLabel: '去补求职意向'
  },
  {
    id: 'skills',
    label: '专业技能',
    done: skillTags.value.length > 0,
    desc: '把技能关键词写出来，企业检索和人工浏览都会更快抓到重点。',
    actionLabel: '去补技能'
  },
  {
    id: 'education',
    label: '教育经历',
    done: resume.education.length > 0,
    desc: '教育经历是判断基础背景的第一层信息，建议完整保留。',
    actionLabel: '去补教育经历'
  },
  {
    id: 'experience',
    label: '工作经历',
    done: resume.experience.length > 0,
    desc: '工作经历是简历里最直接的说服力，没有的话至少补项目或实习。',
    actionLabel: '去补工作经历'
  },
  {
    id: 'project',
    label: '项目经验',
    done: resume.project.length > 0,
    desc: '项目不是硬性项，但有代表性案例时，建议写出来放大能力。',
    actionLabel: '去补项目经验'
  },
  {
    id: 'attachment',
    label: '附件简历',
    done: Boolean(resumeAttachment.url),
    desc: '附件简历方便企业下载流转，建议在线版和附件版同时维护。',
    actionLabel: '去上传附件'
  }
]))

const completedSectionCount = computed(() => resumeSectionChecklist.value.filter(item => item.done).length)
const primaryResumeTask = computed(() => resumeSectionChecklist.value.find(item => !item.done) || null)

const resumeSectionStatusMap = computed(() => {
  return resumeSectionChecklist.value.reduce((map, item) => {
    let label = item.done ? '已完成' : '待完善'
    let tagType = item.done ? 'success' : 'warning'
    let hint = item.desc

    if (item.id === 'basic' && item.done) {
      hint = '姓名与联系方式已具备基础可读性。'
    } else if (item.id === 'intent' && item.done) {
      hint = '岗位、城市和薪资意向已经明确。'
    } else if (item.id === 'skills') {
      if (item.done) {
        hint = `已整理 ${skillTags.value.length} 个技能关键词。`
      }
    } else if (item.id === 'education' && item.done) {
      hint = `已填写 ${resume.education.length} 段教育经历。`
    } else if (item.id === 'experience' && item.done) {
      hint = `已填写 ${resume.experience.length} 段工作经历。`
    } else if (item.id === 'project') {
      if (item.done) {
        hint = `已填写 ${resume.project.length} 个项目案例。`
      } else {
        label = '可补充'
        tagType = 'info'
        hint = '如果有代表性项目，建议补充放大能力。'
      }
    } else if (item.id === 'attachment') {
      if (item.done) {
        hint = '附件简历已上传，可直接预览和下载。'
      }
    }

    map[item.id] = {
      done: item.done,
      label,
      tagType,
      hint
    }
    return map
  }, {})
})

const getSectionStatus = (id) => {
  return resumeSectionStatusMap.value[id] || {
    done: false,
    label: '待完善',
    tagType: 'warning',
    hint: ''
  }
}

// 学历等级定义
const DEGREE_LEVELS = [
  { label: '高中/中专', value: '高中', rank: 1 },
  { label: '大专', value: '大专', rank: 2 },
  { label: '本科', value: '本科', rank: 3 },
  { label: '硕士', value: '硕士', rank: 4 },
  { label: '博士', value: '博士', rank: 5 }
]

// 求职状态常量
const JOB_STATUS_OPTIONS = [
  { label: '离职-随时到岗', value: '离职-随时到岗' },
  { label: '在职-月内到岗', value: '在职-月内到岗' },
  { label: '在职-考虑机会', value: '在职-考虑机会' },
  { label: '应届毕业生', value: '应届毕业生' }
]

const IDENTITY_OPTIONS = [
  { label: '在校生', value: 'STUDENT' },
  { label: '应届生', value: 'FRESH_GRAD' },
  { label: '职场人士', value: 'WORKER' }
]

const currentIdentityLabel = computed(() => {
  return IDENTITY_OPTIONS.find(item => item.value === resume.basicInfo.currentIdentity)?.label
    || resume.basicInfo.currentIdentity
    || '身份待补充'
})

const degreeLabel = computed(() => {
  const found = DEGREE_LEVELS.find(d => d.value === resume.basicInfo.degree || d.label === resume.basicInfo.degree)
  return found?.label || resume.basicInfo.degree || '学历待补充'
})

const educationSummaryText = computed(() => {
  const school = resume.basicInfo.college || resume.basicInfo.collage || resume.education[0]?.school || ''
  const major = resume.basicInfo.major || resume.education[0]?.major || ''
  if (school && major) return `${school} · ${major}`
  return school || major || '院校专业待补充'
})

const expectSalaryText = computed(() => {
  if (resume.basicInfo.expectSalaryMin && resume.basicInfo.expectSalaryMax) {
    return `${resume.basicInfo.expectSalaryMin}k - ${resume.basicInfo.expectSalaryMax}k`
  }
  return resume.basicInfo.expectSalary || '未填'
})

const basicOverviewItems = computed(() => ([
  { label: '当前身份', value: currentIdentityLabel.value },
  { label: '工作年限', value: resume.basicInfo.workYears || '待补充' },
  { label: '最高学历', value: degreeLabel.value },
  { label: '院校 / 专业', value: educationSummaryText.value }
]))

const basicSummaryBadges = computed(() => ([
  { label: '求职状态', value: resume.basicInfo.currentStatus || '待补充' },
  { label: '出生日期', value: resume.basicInfo.birthday || '待补充' }
]))

const intentOverviewItems = computed(() => ([
  { label: '期望职位', value: resume.basicInfo.expectJob || '未填' },
  { label: '期望薪资', value: expectSalaryText.value },
  { label: '期望城市', value: resume.basicInfo.expectCity || '未填' },
  { label: '求职状态', value: resume.basicInfo.currentStatus || '未填' }
]))

const intentHeadline = computed(() => {
  const job = resume.basicInfo.expectJob || '目标岗位待补充'
  const city = resume.basicInfo.expectCity || '目标城市待补充'
  return `正在面向 ${city} 的 ${job} 机会，期望薪资 ${expectSalaryText.value}。`
})

const getDegreeRank = (degreeName) => {
  const found = DEGREE_LEVELS.find(d => d.value === degreeName || d.label === degreeName)
  return found ? found.rank : 0
}

const setActiveSection = (sectionId) => {
  if (!sectionId || activeSection.value === sectionId) return
  activeSection.value = sectionId
}

const getSectionElements = () => navItems
  .map(item => document.getElementById(item.id))
  .filter(Boolean)

const getSectionOffsetTop = (sectionEl, containerEl) => {
  const containerRect = containerEl.getBoundingClientRect()
  const sectionRect = sectionEl.getBoundingClientRect()
  return sectionRect.top - containerRect.top + containerEl.scrollTop
}

const updateActiveSectionByScroll = () => {
  const sections = getSectionElements()
  if (!sections.length) return

  const container = contentWrap.value
  const canUseContainerScroll = !!container && container.scrollHeight > container.clientHeight + 2

  if (!canUseContainerScroll) {
    const viewportAnchor = Math.max(120, SECTION_ACTIVE_OFFSET + 72)
    let currentSection = sections[0].id
    for (const sectionEl of sections) {
      if (sectionEl.getBoundingClientRect().top <= viewportAnchor) {
        currentSection = sectionEl.id
      }
    }
    setActiveSection(currentSection)
    return
  }

  const scrollTop = container.scrollTop
  let currentSection = sections[0].id
  for (const sectionEl of sections) {
    const sectionTop = getSectionOffsetTop(sectionEl, container)
    if (scrollTop + SECTION_ACTIVE_OFFSET >= sectionTop) {
      currentSection = sectionEl.id
    }
  }

  // 滚动到底部时强制落到最后一节，避免高亮停留在上一个区块
  if (scrollTop + container.clientHeight >= container.scrollHeight - 8) {
    currentSection = sections[sections.length - 1].id
  }
  setActiveSection(currentSection)
}

const handleScroll = () => {
  if (scrollRafId) {
    cancelAnimationFrame(scrollRafId)
  }
  scrollRafId = requestAnimationFrame(() => {
    updateActiveSectionByScroll()
    scrollRafId = null
  })
}

const setupSectionObserver = () => {
  if (sectionObserver) {
    sectionObserver.disconnect()
    sectionObserver = null
  }

  const sections = getSectionElements()
  if (!sections.length) return

  const rootEl = contentWrap.value && contentWrap.value.scrollHeight > contentWrap.value.clientHeight + 2
    ? contentWrap.value
    : null

  sectionObserver = new IntersectionObserver((entries) => {
    const visibles = entries
      .filter(entry => entry.isIntersecting)
      .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)

    if (visibles.length > 0) {
      setActiveSection(visibles[0].target.id)
      return
    }
    // 兜底：当所有区块均不满足阈值时，回退到滚动计算
    updateActiveSectionByScroll()
  }, {
    root: rootEl,
    rootMargin: rootEl ? `-${SECTION_ACTIVE_OFFSET}px 0px -55% 0px` : '-140px 0px -55% 0px',
    threshold: [0.05, 0.15, 0.3, 0.45, 0.6]
  })

  sections.forEach((sectionEl) => sectionObserver.observe(sectionEl))
}

const handleWindowScroll = () => {
  // 当实际滚动发生在页面而不是容器时，也可触发高亮同步
  handleScroll()
}

const scrollToSection = (id) => {
  const container = contentWrap.value
  const target = document.getElementById(id)
  if (!target) return

  setActiveSection(id)
  const canUseContainerScroll = !!container && container.scrollHeight > container.clientHeight + 2
  if (canUseContainerScroll) {
    const targetTop = getSectionOffsetTop(target, container) - SECTION_SCROLL_OFFSET
    container.scrollTo({
      top: Math.max(targetTop, 0),
      behavior: 'smooth'
    })
  } else {
    target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  if (sectionSwitchTimer) {
    clearTimeout(sectionSwitchTimer)
  }
  sectionSwitchTimer = window.setTimeout(() => {
    updateActiveSectionByScroll()
    sectionSwitchTimer = null
  }, 380)
}

const focusResumeSection = (id) => {
  scrollToSection(id)

  if (id === 'basic' || id === 'intent' || id === 'skills') {
    isEditing[id] = true
    return
  }

  if (id === 'education' && !resume.education.length) {
    addItem('education')
    return
  }

  if (id === 'experience' && !resume.experience.length) {
    addItem('experience')
    return
  }

  if (id === 'project' && !resume.project.length) {
    addItem('project')
  }
}

const jumpToPrimarySection = () => {
  if (!primaryResumeTask.value) return
  focusResumeSection(primaryResumeTask.value.id)
}

const addItem = (type) => {
  const tpl = { 
    education: { school: '', major: '', degree: '', timeRange: [] },
    experience: { company: '', position: '', content: '', timeRange: [] },
    project: { name: '', role: '', description: '', timeRange: [] }
  }
  resume[type].push({ ...tpl[type] })
  const key = `${type.slice(0,3)}_${resume[type].length - 1}`
  nextTick(() => isEditing[key] = true)
}
const removeItem = (type, index) => resume[type].splice(index, 1)

const saveResume = async () => {
  try {
    const educationList = resume.education.filter(e => e.school && e.degree)
    const ranks = educationList.map(e => getDegreeRank(e.degree))
    const maxRank = ranks.length > 0 ? Math.max(...ranks) : 0

    if (maxRank >= 4) {
      const hasBachelor = ranks.includes(3)
      if (!hasBachelor) {
        ElMessage.warning('系统检测到您填写了研究生学历，请务必同时补充本科学历信息，以符合教育逻辑。')
        scrollToSection('education')
        return
      }
    }

    if (resume.basicInfo.expectSalaryMin && resume.basicInfo.expectSalaryMax) {
      if (resume.basicInfo.expectSalaryMin > resume.basicInfo.expectSalaryMax) {
        ElMessage.warning('期望薪资的最低值不能大于最高值')
        return
      }
      resume.basicInfo.expectSalary = `${resume.basicInfo.expectSalaryMin}k-${resume.basicInfo.expectSalaryMax}k`
    } else {
      // 仅填写一端或清空区间时，清理字符串薪资，避免提交历史脏值
      resume.basicInfo.expectSalary = ''
    }

    await saveMyResume(resume, { skipBusinessErrorMessage: true })
    ElMessage.success('同步成功')
    Object.keys(isEditing).forEach(k => isEditing[k] = false)
    loadResume()
  } catch (e) { ElMessage.error(e?.message || '同步失败') }
}

onMounted(async () => {
  await loadResume()
  await nextTick()
  setupSectionObserver()
  updateActiveSectionByScroll()
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
  window.addEventListener('resize', setupSectionObserver)
})

onBeforeUnmount(() => {
  if (sectionSwitchTimer) {
    clearTimeout(sectionSwitchTimer)
  }
  if (scrollRafId) {
    cancelAnimationFrame(scrollRafId)
  }
  if (sectionObserver) {
    sectionObserver.disconnect()
  }
  window.removeEventListener('scroll', handleWindowScroll)
  window.removeEventListener('resize', setupSectionObserver)
})
</script>

<style scoped>
/* =========================================
   1. 全局布局容器 (Layout)
   ========================================= */
.resume-workspace {
  /* 跟随主布局内容区高度，避免写死顶部导航高度导致重叠 */
  height: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width-wide));
  background:
    linear-gradient(180deg, rgba(248, 250, 255, 0.94), rgba(239, 244, 255, 0.86));
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", Helvetica, Arial, sans-serif;
  color: #1d1d1f;
  overflow: visible;
  margin: 8px auto 0;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  position: relative;
}

.resume-workspace::before,
.resume-workspace::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(8px);
  z-index: 0;
}

.resume-workspace::before {
  top: 18px;
  left: 2%;
  width: min(24vw, 240px);
  height: min(24vw, 240px);
  background: radial-gradient(circle, rgba(10, 132, 255, 0.12), rgba(10, 132, 255, 0) 72%);
}

.resume-workspace::after {
  top: 180px;
  right: 2%;
  width: min(22vw, 220px);
  height: min(22vw, 220px);
  background: radial-gradient(circle, rgba(147, 197, 253, 0.11), rgba(147, 197, 253, 0) 72%);
}

/* =========================================
   2. 顶部工具栏 (Header) - 纯色极简风格
   ========================================= */
.workspace-header {
  min-height: 60px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(250, 252, 255, 0.8)),
    linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0.03) 48%, rgba(191, 219, 254, 0.12));
  border-bottom: 1px solid rgba(255, 255, 255, 0.82);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px clamp(16px, 3vw, 32px);
  gap: 12px;
  flex-wrap: wrap;
  flex-shrink: 0;
  z-index: 10;
  position: relative;
  backdrop-filter: blur(16px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
}

.workspace-title {
  font-size: 18px;
  font-weight: 600;
  color: #1d1d1f;
  letter-spacing: -0.01em;
}

.sync-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #86868b;
  margin-left: 16px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-left: auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.completeness-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  background: rgba(10, 132, 255, 0.08);
  border: 1px solid rgba(10, 132, 255, 0.12);
  padding: 6px 14px;
  border-radius: 99px;
  color: #0f172a;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0a84ff;
}

/* =========================================
   3. 主体分栏区域 (Main)
   ========================================= */
.workspace-main {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  gap: clamp(18px, 1.8vw, 30px);
  overflow: visible;
  width: 100%;
  padding: 0 clamp(16px, 2vw, 24px) clamp(24px, 4vw, 40px);
  box-sizing: border-box;
  position: relative;
  z-index: 1;
}

/* 左侧导航 */
.workspace-sidebar {
  width: clamp(190px, 15vw, 240px);
  padding: 24px 0;
  flex-shrink: 0;
  align-self: flex-start;
  position: sticky;
  top: 16px;
  background: transparent;
  border-right: none;
  overflow: visible;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 249, 255, 0.9));
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 14px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.sidebar-progress-card {
  margin-top: 14px;
  padding: 16px 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(240, 246, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.68);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.sidebar-progress-head strong {
  font-size: 22px;
  line-height: 1;
}

.sidebar-progress-meta {
  font-size: 12px;
  color: #64748b;
}

.sidebar-progress-copy {
  font-size: 12px;
  line-height: 1.7;
  color: #56718f;
}

.sidebar-progress-btn {
  width: 100%;
}

.sidebar-checklist {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar-check-item {
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  color: #64748b;
  cursor: pointer;
  font-size: 13px;
}

.sidebar-check-item .check-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid rgba(148, 163, 184, 0.72);
  background: #ffffff;
  flex-shrink: 0;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.sidebar-check-item .check-label {
  line-height: 1.5;
}

.sidebar-check-item:hover,
.sidebar-check-item.active {
  color: #0f172a;
}

.sidebar-check-item.done .check-dot {
  border-color: #0071e3;
  background: #0071e3;
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.12);
}

.sidebar-check-item.active .check-dot {
  border-color: #60a5fa;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #6e6e73;
  transition: background-color 0.15s ease;
}

.nav-item:hover {
  background: rgba(10, 132, 255, 0.06);
  color: #1d1d1f;
}

.nav-item.active {
  background: #ffffff;
  color: #0071e3;
  box-shadow: 0 10px 18px rgba(10, 132, 255, 0.1);
  font-weight: 600;
}

/* 右侧内容流 - 宽版居中单列 */
.workspace-content {
  flex: 1;
  min-height: 0;
  overflow: visible;
  padding: 32px 0 100px; /* 底部留白 */
  scroll-behavior: smooth;
}

@media (max-width: 1200px) {
  .workspace-header {
    padding: 10px 16px;
  }

  .workspace-main {
    padding: 0 16px 28px;
  }

  .workspace-content {
    padding: 24px 0 80px;
  }

  .sync-status {
    display: none;
  }
}

@media (max-width: 900px) {
  .workspace-main {
    flex-direction: column;
    gap: 0;
    padding: 0 12px 24px;
  }

  .workspace-sidebar {
    width: 100%;
    padding: 10px 0;
    border-bottom: 1px solid rgba(0,0,0,0.05);
    align-self: auto;
    position: static;
    overflow: visible;
  }

  .nav-list {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
    padding: 0;
    border: none;
    box-shadow: none;
    border-radius: 0;
    background: transparent;
  }

  .sidebar-progress-card {
    margin-top: 12px;
  }

  .workspace-content {
    padding: 20px 16px 80px;
  }

  .header-right {
    width: 100%;
    gap: 12px;
  }
}

.resume-section {
  margin-bottom: 32px;
  max-width: 1120px; /* 大屏下释放更多横向空间，减少编辑区留白 */
  margin-left: auto;
  margin-right: auto;
  width: 100%;
}

@media (min-width: 1500px) {
  .resume-section {
    max-width: 1180px;
  }
}

/* 移除之前的 Grid 分栏逻辑 */
#education, #experience, #project,
#basic, #intent, #attachment {
  grid-column: auto;
}

/* =========================================
   4. 卡片样式 (Cards) - 紧凑优化
   ========================================= */
.apple-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 250, 255, 0.92));
  border-radius: 16px;
  padding: 24px; /* 紧凑内边距 */
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.apple-card.is-pending {
  border-color: rgba(147, 197, 253, 0.72);
  box-shadow:
    0 16px 34px rgba(15, 23, 42, 0.07),
    0 0 0 1px rgba(191, 219, 254, 0.32);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
  gap: 16px;
  flex-wrap: wrap;
}

.card-header-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: #1d1d1f;
}

.section-guide {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.section-guide.pending {
  color: #56718f;
}

.section-guide-text {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

/* =========================================
   5. 内容排版 (Typography & Components)
   ========================================= */

/* Hero Block (头像+信息) */
.hero-block {
  display: flex;
  align-items: stretch;
  gap: 18px;
  flex-wrap: wrap;
}

.hero-panel {
  flex: 1.1;
  min-width: 280px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(252, 253, 255, 0.98), rgba(242, 247, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.hero-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.hero-info h3 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px 0;
  color: #1d1d1f;
}
.meta-line {
  font-size: 14px;
  color: #86868b;
  margin: 0 0 12px 0;
}
.contact-tags {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-badge-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.08);
  border: 1px solid rgba(191, 219, 254, 0.66);
  color: #4b6380;
  font-size: 12px;
}

.c-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #1d1d1f;
  background: rgba(244, 248, 255, 0.96);
  border: 1px solid rgba(191, 219, 254, 0.58);
  padding: 6px 12px;
  border-radius: 10px;
}

.hero-facts {
  flex: 0.9;
  min-width: 260px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.hero-fact-card {
  padding: 16px 16px 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hero-fact-card span {
  font-size: 12px;
  color: #6b7280;
}

.hero-fact-card strong {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #0f172a;
}

.intent-banner {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(0, 113, 227, 0.08), rgba(191, 219, 254, 0.18));
  border: 1px solid rgba(147, 197, 253, 0.56);
}

.intent-banner-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.intent-banner-desc {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.75;
  color: #56718f;
}

/* 字段展示 Grid */
.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px 20px;
}
.field-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 94px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(251, 252, 255, 0.98), rgba(242, 247, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.56);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}
.field-box label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}
.field-box span {
  font-size: 15px;
  color: #0f172a;
  font-weight: 500;
  line-height: 1.55;
}

.intent-box.is-filled {
  border-color: rgba(147, 197, 253, 0.78);
}

.skills-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(251, 252, 255, 0.98), rgba(242, 247, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.58);
  color: #56718f;
  font-size: 13px;
}

.skills-summary strong {
  font-size: 24px;
  line-height: 1;
  color: #0f172a;
}

.skills-read {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skills-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(246, 250, 255, 0.96), rgba(236, 244, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.7);
  color: #24507a;
  font-size: 13px;
  font-weight: 600;
}

.skill-chip.is-preview {
  background: linear-gradient(180deg, rgba(234, 244, 255, 0.96), rgba(223, 237, 255, 0.9));
}

.skills-tip,
.skills-preview-label {
  font-size: 12px;
  color: #64748b;
}

.skills-preview {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 时间轴 */
.timeline-wrap {
  position: relative;
  padding-left: 18px;
}
.timeline-node {
  position: relative;
  padding-bottom: 24px;
  display: flex;
  gap: 16px;
}
.node-marker {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ffffff;
  border: 3px solid #0071e3;
  margin-top: 6px;
  z-index: 2;
  flex-shrink: 0;
}
.timeline-node::before {
  content: '';
  position: absolute;
  left: 4px; /* marker width/2 - 1 */
  top: 10px;
  bottom: 0;
  width: 2px;
  background: #e5e5ea;
}
.timeline-node:last-child::before {
  display: none;
}
.node-main {
  flex: 1;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(243, 247, 255, 0.92));
  border: 1px solid rgba(191, 219, 254, 0.54);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}
.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.node-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1d1d1f;
}
.node-body {
  font-size: 14px;
  color: #48484a;
  margin-top: 8px;
  line-height: 1.65;
}
.content-text {
  white-space: pre-wrap;
  color: #636366;
  margin-top: 8px;
}
.node-ops {
  opacity: 0;
  transition: opacity 0.2s;
}
.node-main:hover .node-ops {
  opacity: 1;
}

/* 附件模块 */
.attachment-info-card {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 255, 0.96), rgba(242, 246, 255, 0.9));
  border: 1px solid rgba(15, 23, 42, 0.07);
  border-radius: 12px;
}
.file-icon-box {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 10px;
  border: 1px solid rgba(10, 132, 255, 0.12);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}
.file-details {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-size: 15px;
  font-weight: 500;
  color: #1d1d1f;
  margin-bottom: 2px;
}
.file-meta {
  font-size: 12px;
  color: #86868b;
}
.file-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.ios-upload-area {
  width: 100%;
}
:deep(.el-upload-dragger) {
  border-radius: 12px;
  border: 2px dashed rgba(10, 132, 255, 0.18);
  background: linear-gradient(180deg, rgba(251, 252, 255, 0.98), rgba(244, 248, 255, 0.92));
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
:deep(.el-upload-dragger:hover) {
  border-color: #0071e3;
  background: rgba(10, 132, 255, 0.05);
}
.upload-icon-circle {
  width: 40px;
  height: 40px;
  background: #eaf2ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 8px;
}
.upload-text h4 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px;
}
.upload-text p {
  font-size: 12px;
  color: #86868b;
  margin: 0;
}

/* =========================================
   6. Element UI 覆盖 (Overrides)
   ========================================= */
/* 输入框 Apple 风格化 */
:deep(.el-input__wrapper), :deep(.el-textarea__inner) {
  border-radius: 8px;
  background: rgba(244, 248, 255, 0.88);
  box-shadow: none !important;
  border: 1px solid transparent;
  padding: 6px 12px;
  transition: all 0.2s;
}
:deep(.el-input__wrapper:hover), :deep(.el-textarea__inner:hover) {
  background: rgba(239, 244, 255, 0.96);
}
:deep(.el-input__wrapper.is-focus), :deep(.el-textarea__inner:focus) {
  background: #ffffff;
  border-color: #0071e3;
  box-shadow: 0 0 0 3px rgba(0,113,227,0.15) !important;
}

:deep(.el-button:not(.el-button--primary):not(.is-link):not(.el-button--text)) {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  border-color: rgba(15, 23, 42, 0.08);
  color: #475569;
}

:deep(.el-button:not(.el-button--primary):not(.is-link):not(.el-button--text):hover) {
  background: #ffffff;
  border-color: rgba(10, 132, 255, 0.22);
  color: #0a84ff;
}

/* 按钮 Apple 风格化 */
:deep(.el-button--primary) {
  background-color: #0071e3;
  border-color: #0071e3;
  border-radius: 99px; /* Pill */
  padding: 8px 18px;
  font-weight: 500;
  box-shadow: none;
}
:deep(.el-button--primary:hover) {
  background-color: #0077ed;
  border-color: #0077ed;
}

/* 表单 Label */
:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 600;
  color: #86868b;
  text-transform: uppercase;
  padding-bottom: 4px;
}

/* 预览弹窗 */
.preview-container {
  min-height: min(60vh, 520px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
}
.preview-dialog :deep(.el-dialog__body) {
  padding: 16px 18px 20px;
}
.preview-iframe {
  width: 100%;
  height: min(70vh, 680px);
  border: none;
  border-radius: 8px;
  background: #ffffff;
}
.preview-image {
  max-width: 100%;
  max-height: min(70vh, 680px);
  border-radius: 8px;
  display: block;
  margin: 0 auto;
  object-fit: contain;
}
.preview-placeholder {
  text-align: center;
}

@media (max-width: 900px) {
  .field-grid {
    grid-template-columns: 1fr;
  }

  .card-header {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-panel,
  .hero-facts {
    min-width: 0;
    width: 100%;
  }

  .hero-facts {
    grid-template-columns: 1fr;
  }

  .attachment-info-card {
    align-items: flex-start;
  }

  .preview-container {
    min-height: min(56vh, 460px);
    padding: 12px;
  }

  .preview-iframe {
    height: min(60vh, 560px);
  }

  .preview-image {
    max-height: min(60vh, 560px);
  }
}
</style>
