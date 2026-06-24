<!--
文件速览：
1. 文件职责：商家候选人处理台，负责筛选投递记录、查看简历、发起面试和附件预览。
2. 页面入口：商家“候选人管理/简历处理台”页面。
3. 关键结构：query、filter-panel、resumeDialogVisible、previewVisible、resume-preview-frame。
4. 阅读建议：先看顶部筛选区，再看简历抽屉与附件预览弹层，最后看底部断点样式。
-->
<template>
  <div class="resume-page">
    <div class="page-toolbar">
      <div>
        <div class="toolbar-title">简历处理台</div>
        <div class="toolbar-desc">集中处理投递简历，反馈结果会同步给求职者</div>
      </div>
      <div class="toolbar-actions">
        <el-button @click="$router.push('/merchant/interviews')">面试日程</el-button>
        <el-button @click="$router.push('/merchant/jobs')">职位管理</el-button>
      </div>
    </div>

    <div class="filter-panel">
      <div class="filter-fields adaptive-filter-row">
        <el-select v-model="query.jobId" placeholder="投递职位" clearable class="filter-control adaptive-filter-control adaptive-filter-control--sm" @change="handleFilterChange">
          <el-option v-for="job in jobOptions" :key="job.id" :label="job.title" :value="job.id" />
        </el-select>

        <el-select v-model="query.status" placeholder="投递状态" clearable class="filter-control adaptive-filter-control adaptive-filter-control--sm" @change="handleFilterChange">
          <el-option label="已投递" :value="0" />
          <el-option label="已初筛" :value="1" />
          <el-option label="面试邀约" :value="2" />
          <el-option label="不合适" :value="3" />
        </el-select>

        <el-select v-model="query.degree" placeholder="学历要求" clearable class="filter-control adaptive-filter-control adaptive-filter-control--sm" @change="handleFilterChange">
          <el-option label="本科" value="本科" />
          <el-option label="硕士" value="硕士" />
        </el-select>

      </div>
      <div class="filter-actions adaptive-filter-actions">
        <el-button type="primary" icon="Search" @click="handleFilterChange">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <el-table
      v-if="candidates.length > 0"
      :data="candidates"
      v-loading="loading"
      stripe
      :row-class-name="getRowClass"
      @row-click="handleRowClick"
      @row-mouseenter="handleRowEnter"
      @row-mouseleave="handleRowLeave"
    >
      <el-table-column label="候选人" min-width="200" align="left" header-align="left">
        <template #default="{ row }">
          <div class="flex items-center gap-3">
            <el-avatar :size="40" :src="row.applicant?.avatar">{{ getApplicantName(row.applicant)?.charAt(0) }}</el-avatar>
            <div>
              <div class="font-bold text-gray-800">{{ getApplicantName(row.applicant) }}</div>
              <div class="text-xs text-gray-500">
                {{ formatGender(row.applicant?.gender) }} /
                {{ row.applicant?.age || '-' }}岁 /
                {{ row.applicant?.workYears || '-' }}
              </div>
              <div v-if="hoveredRowId === row.id" class="row-hint">点击查看简历</div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="applicant.collage" label="毕业院校" width="180" align="center" header-align="center">
        <template #default="{ row }">
          <div>{{ row.applicant?.collage || row.applicant?.college || '-' }}</div>
          <el-tag size="small" type="info" class="mt-1">{{ row.applicant?.degree || '未知' }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="jobName" label="投递职位" width="180" align="center" header-align="center" />

      <el-table-column label="投递时间" width="120" align="center" header-align="center">
        <template #default="{ row }">
          <span class="text-gray-500 text-xs">{{ formatDate(row.createTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="当前状态" width="120" align="center" header-align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTag(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="附件" width="120" align="center" header-align="center">
        <template #default="{ row }">
          <div class="attach-cell">
            <template v-if="row.hasResumeAttachment">
              <el-tooltip v-if="canViewAttachment(row)" content="点击在线查看附件简历" placement="top">
                <el-button
                  size="small"
                  text
                  class="attach-btn"
                  :icon="Paperclip"
                  @click.stop="openResumeAttachment(row.resumeUrl, row.resumeName)"
                >在线查看</el-button>
              </el-tooltip>
              <div v-else class="attach-pending">
                <el-button size="small" text class="attach-btn" @click.stop="requestAttachment(row)">申请查看</el-button>
                <el-tag v-if="isAttachmentPending(row)" size="small" type="warning">待同意</el-tag>
              </div>
            </template>
            <span v-else class="attach-empty">无</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="340" fixed="right" class-name="op-col" align="center" header-align="center">
        <template #default="{ row }">
          <div class="op-actions">
            <el-button
              v-if="row.status === 0"
              size="small"
              type="primary"
              class="action-btn"
              :icon="View"
              @click.stop="handleStatus(row, 1)"
            >标记已初筛</el-button>
            <el-button
              v-else-if="row.status === 1"
              size="small"
              type="success"
              class="action-btn"
              :icon="Calendar"
              @click.stop="openInterview(row)"
            >发起面试</el-button>
            <el-tooltip v-else-if="row.status === 2" content="已邀约，状态不可修改" placement="top">
              <span>
                <el-button size="small" type="danger" class="action-btn" :icon="CircleClose" disabled>标记不合适</el-button>
              </span>
            </el-tooltip>
            <el-button
              v-if="row.status === 2"
              size="small"
              type="warning"
              class="action-btn"
              :icon="Calendar"
              @click.stop="openInterview(row, true)"
            >追加面试</el-button>
            <el-tooltip v-else content="已标记不合适，状态不可修改" placement="top">
              <span>
                <el-button size="small" type="info" class="action-btn" :icon="RefreshRight" disabled>恢复已投递</el-button>
              </span>
            </el-tooltip>

            <el-button
              size="small"
              type="primary"
              plain
              class="action-btn"
              :icon="ChatLineRound"
              @click.stop="goChat(row)"
            >沟通</el-button>

            <el-dropdown
              trigger="click"
              @command="(cmd) => handleDropdownCommand(row, cmd)"
            >
              <el-button size="small" plain class="action-btn" :icon="MoreFilled" @click.stop>调整状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="0" :disabled="!canUpdateStatus(row.status, 0)">已投递</el-dropdown-item>
                  <el-dropdown-item :command="1" :disabled="!canUpdateStatus(row.status, 1) || row.status === 2 || row.status === 3">已初筛</el-dropdown-item>
                  <el-dropdown-item :command="2" :disabled="!canUpdateStatus(row.status, 2)">面试邀约</el-dropdown-item>
                  <el-dropdown-item :command="3" :disabled="!canUpdateStatus(row.status, 3)">不合适</el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 2 || row.status === 3"
                    divided
                    command="revert_viewed"
                  >撤回为已初筛</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else-if="!loading" description="暂无投递记录" class="empty-block">
      <el-button type="primary" @click="$router.push('/merchant/jobs')">发布职位</el-button>
    </el-empty>

    <div class="mt-6 adaptive-pagination">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchData"
      />
    </div>

    <el-dialog v-model="interviewDialogVisible" title="发起面试邀约" width="min(500px, 92vw)">
      <el-form :model="interviewForm">
        <el-form-item label="面试时间">
          <el-date-picker
            v-model="interviewForm.time"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择时间"
            :disabled-date="disablePastInterviewDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="面试方式">
          <el-radio-group v-model="interviewForm.mode">
            <el-radio value="ONLINE">线上面试</el-radio>
            <el-radio value="OFFLINE">线下面试</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="interviewForm.mode === 'ONLINE' ? '会议链接' : '面试地址'">
          <el-input
            v-model="interviewForm.location"
            :placeholder="interviewForm.mode === 'ONLINE' ? '例如：腾讯会议/Zoom 链接' : '例如：深圳 · 南山科技园'"
          />
        </el-form-item>
        <el-form-item label="面试备注">
          <el-input type="textarea" v-model="interviewForm.remark" placeholder="例如：请携带作品集" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="interviewDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInterview">发送邀约</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="resumeDialogVisible" title="简历详情" size="min(520px, 92vw)">
      <div v-if="currentResume" class="resume-panel">
        <div class="resume-header">
          <div class="resume-header-left">
            <el-avatar :size="56" :src="currentResume.applicant?.avatar">
              {{ getApplicantName(currentResume.applicant)?.charAt(0) }}
            </el-avatar>
            <div class="resume-base">
              <div class="resume-name">{{ getApplicantName(currentResume.applicant) }}</div>
              <div class="resume-meta">
                {{ formatGender(currentResume.applicant?.gender) }} /
                {{ currentResume.applicant?.age || '-' }}岁 /
                {{ currentResume.applicant?.workYears || '-' }}
              </div>
            </div>
          </div>
          <el-button
            size="small"
            type="danger"
            plain
            class="report-btn"
            @click="openReportDialog(currentResume)"
          >举报该求职者</el-button>
        </div>

        <div class="resume-layout">
          <div class="resume-left">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="投递职位">{{ currentResume.jobName }}</el-descriptions-item>
              <el-descriptions-item v-if="currentResume.status === 2" label="面试时间">
                {{ formatDateTime(currentResume.interviewTime) || '-' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="currentResume.status === 2" label="面试方式">
                <el-tag size="small" :type="getInterviewModeTag(currentResume.interviewMethod)">
                  {{ getInterviewModeLabel(currentResume.interviewMethod) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item
                v-if="currentResume.status === 2"
                :label="isOnlineInterview(currentResume.interviewMethod) ? '会议链接' : '面试地址'"
              >
                {{ currentResume.interviewLocation || '待补充' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="currentResume.status === 2" label="面试备注">
                {{ currentResume.interviewRemark || currentResume.feedback || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="毕业院校">{{ currentResume.applicant?.collage || currentResume.applicant?.college || '-' }}</el-descriptions-item>
              <el-descriptions-item label="学历">{{ currentResume.applicant?.degree || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ currentResume.applicant?.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ currentResume.applicant?.email || '-' }}</el-descriptions-item>
              <el-descriptions-item label="当前身份">{{ formatIdentity(currentResume.applicant?.currentIdentity) }}</el-descriptions-item>
              <el-descriptions-item label="求职状态">{{ currentResume.applicant?.currentStatus || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望城市">{{ currentResume.applicant?.expectCity || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望职位">{{ currentResume.applicant?.expectJob || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望薪资">{{ currentResume.applicant?.expectSalary || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="resume-right">
            <el-collapse v-model="resumeCollapseActive" class="resume-collapse">
              <el-collapse-item name="skills">
                <template #title>技能标签</template>
                <div class="resume-block">
                  <div class="resume-tags">
                    <el-tag v-for="tag in parseSkills(currentResume.applicant?.skills)" :key="tag" size="small" type="info">
                      {{ tag }}
                    </el-tag>
                    <span v-if="parseSkills(currentResume.applicant?.skills).length === 0" class="resume-empty">暂无</span>
                  </div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="advantage">
                <template #title>个人优势</template>
                <div class="resume-block">
                  <div class="resume-text">{{ currentResume.applicant?.advantage || '暂无' }}</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="education">
                <template #title>教育经历</template>
                <div class="resume-block">
                  <div v-if="currentResume.applicant?.educationList?.length" class="resume-list">
                    <div v-for="(edu, index) in currentResume.applicant.educationList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildEduTitle(edu) }}</div>
                      <div class="resume-item-meta">{{ formatRange(edu.timeRange) }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="experience">
                <template #title>工作/实习经历</template>
                <div class="resume-block">
                  <div v-if="currentResume.applicant?.experienceList?.length" class="resume-list">
                    <div v-for="(exp, index) in currentResume.applicant.experienceList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildExpTitle(exp) }}</div>
                      <div class="resume-item-text">{{ exp.content || '暂无描述' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="project">
                <template #title>项目经历</template>
                <div class="resume-block">
                  <div v-if="currentResume.applicant?.projectList?.length" class="resume-list">
                    <div v-for="(proj, index) in currentResume.applicant.projectList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildProjectTitle(proj) }}</div>
                      <div class="resume-item-meta">{{ formatRange(proj.timeRange) }}</div>
                      <div class="resume-item-text">{{ proj.description || '暂无描述' }}</div>
                      <div v-if="proj.techStack" class="resume-item-meta">技术栈：{{ proj.techStack }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="certificate">
                <template #title>证书</template>
                <div class="resume-block">
                  <div v-if="currentResume.applicant?.certificateList?.length" class="resume-list">
                    <div v-for="(cert, index) in currentResume.applicant.certificateList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildCertTitle(cert) }}</div>
                      <div class="resume-item-meta">{{ cert.date || '-' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="award">
                <template #title>奖项</template>
                <div class="resume-block">
                  <div v-if="currentResume.applicant?.awardList?.length" class="resume-list">
                    <div v-for="(award, index) in currentResume.applicant.awardList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildAwardTitle(award) }}</div>
                      <div class="resume-item-meta">{{ award.date || '-' }}</div>
                      <div class="resume-item-text">{{ award.description || '暂无描述' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="language">
                <template #title>语言能力</template>
                <div class="resume-block">
                  <div v-if="currentResume.applicant?.languageList?.length" class="resume-list">
                    <div v-for="(lang, index) in currentResume.applicant.languageList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildLangTitle(lang) }}</div>
                      <div class="resume-item-meta">{{ lang.score || '-' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="feedback">
                <template #title>商家反馈</template>
                <div class="resume-block">
                  <div class="resume-text">{{ currentResume.feedback || '尚未反馈' }}</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="interview">
                <template #title>面试记录</template>
                <div class="resume-block">
                  <el-skeleton v-if="interviewLoading" animated :rows="2" />
                  <div v-else-if="interviewList.length" class="interview-list">
                    <div v-for="item in interviewList" :key="item.id" class="interview-item">
                      <div class="interview-header">
                        <span class="interview-round">第{{ item.roundNo }}轮</span>
                        <el-tag size="small" :type="getInterviewStatusTag(item.status)">
                          {{ getInterviewStatusText(item.status) }}
                        </el-tag>
                      </div>
                      <div class="interview-meta">时间：{{ formatDateTime(item.scheduleTime) || '-' }}</div>
                      <div class="interview-meta">
                        方式：
                        <el-tag size="small" :type="getInterviewModeTag(item.method)">
                          {{ getInterviewModeLabel(item.method) }}
                        </el-tag>
                      </div>
                      <div class="interview-meta">
                        {{ isOnlineInterview(item.method) ? '会议链接' : '面试地址' }}：{{ item.location || '待补充' }}
                      </div>
                      <div class="interview-meta">备注：{{ item.remark || '-' }}</div>
                      <div class="interview-actions">
                        <el-button
                          v-if="item.status === 0 || item.status === 1"
                          size="small"
                          type="info"
                          plain
                          @click="handleInterviewStatus(item, 3)"
                        >取消面试</el-button>
                        <el-button
                          v-if="item.status === 1"
                          size="small"
                          type="primary"
                          plain
                          @click="handleInterviewStatus(item, 4)"
                        >标记完成</el-button>
                      </div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无面试记录</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="attachment">
                <template #title>附件简历</template>
                <div class="resume-block">
                  <div class="resume-attachment">
                    <div v-if="currentResume.hasResumeAttachment" class="resume-attachment-actions">
                      <span class="resume-file-name">{{ currentResume.resumeName || '附件简历' }}</span>
                      <div v-if="currentResume.resumeUrl" class="resume-attachment-buttons">
                        <el-button size="small" type="primary" @click="openResumeAttachment(currentResume.resumeUrl, currentResume.resumeName)">在线预览</el-button>
                        <el-button size="small" plain @click="downloadResumeAttachment(currentResume.resumeUrl, currentResume.resumeName)">下载附件</el-button>
                      </div>
                      <div v-else class="resume-attachment-buttons">
                        <el-button size="small" text @click="requestAttachment(currentResume)">申请查看</el-button>
                        <el-tag v-if="isAttachmentPending(currentResume)" size="small" type="warning">待同意</el-tag>
                        <el-tag v-else size="small" type="info">未授权</el-tag>
                      </div>
                    </div>
                    <div v-else class="resume-empty">未上传附件简历</div>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="reportDialogVisible" title="举报求职者" width="min(480px, 92vw)">
      <el-form :model="reportForm" label-width="90px">
        <el-form-item label="举报原因">
          <el-select v-model="reportForm.reasonType" placeholder="请选择原因" style="width: 100%">
            <el-option
              v-for="option in reportReasonOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="详细说明">
          <el-input
            v-model="reportForm.detail"
            type="textarea"
            rows="4"
            maxlength="300"
            show-word-limit
            placeholder="可补充聊天记录、面试爽约等情况说明"
          />
        </el-form-item>
        <el-form-item label="证据附件">
          <el-upload
            class="report-upload"
            :file-list="reportUploadList"
            :http-request="handleReportUpload"
            :on-remove="handleReportRemove"
            :limit="3"
            multiple
            accept=".jpg,.jpeg,.png,.pdf"
            list-type="text"
          >
            <el-button size="small" type="primary">上传证据</el-button>
            <template #tip>
              <div class="report-upload-tip">支持 JPG/PNG/PDF，最多 3 个文件，单个不超过 5MB，至少上传 1 个证据文件。</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <div class="report-tip">提交后将进入管理员处理队列，请确保信息真实有效。举报账号需提供证据截图。</div>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReportForm">提交举报</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      width="92%"
      top="3vh"
      :show-close="false"
      destroy-on-close
      @closed="cleanupPreview"
    >
      <template #header>
        <div class="resume-preview-header">
          <span class="resume-preview-title">{{ previewName }}</span>
          <div class="resume-preview-toolbar">
            <el-button
              size="small"
              :type="previewFitMode === 'width' ? 'primary' : 'default'"
              @click="fitWidth"
            >适配宽度</el-button>
            <el-button
              size="small"
              :type="previewFitMode === 'height' ? 'primary' : 'default'"
              @click="fitHeight"
            >适配高度</el-button>
            <el-button size="small" :disabled="previewScale <= previewMinScale" @click="zoomOut">缩小</el-button>
            <el-slider
              v-model="previewScale"
              class="resume-preview-slider"
              :min="previewMinScale"
              :max="previewMaxScale"
              :step="0.1"
              :format-tooltip="formatZoomTooltip"
              @input="handleZoomInput"
            />
            <el-button size="small" @click="resetZoom">100%</el-button>
            <el-button size="small" :disabled="previewScale >= previewMaxScale" @click="zoomIn">放大</el-button>
            <span class="resume-preview-zoom">{{ Math.round(previewScale * 100) }}%</span>
            <el-button
              size="small"
              plain
              @click="downloadResumeAttachment(previewSourceUrl, previewName)"
            >下载附件</el-button>
            <el-button
              size="small"
              type="danger"
              class="resume-preview-close-btn"
              :icon="Close"
              @click="previewVisible = false"
            >关闭</el-button>
          </div>
        </div>
      </template>
      <div ref="previewFrameRef" class="resume-preview-frame" @wheel="handleWheelZoom">
        <div class="resume-preview-zoom-layer" :style="{ zoom: previewScale }">
          <iframe v-if="previewUrl" :src="previewUrl" frameborder="0"></iframe>
        </div>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { Calendar, CircleClose, MoreFilled, Paperclip, RefreshRight, View, ChatLineRound, Close } from '@element-plus/icons-vue'
import { getMerchantJobs } from '@/api/job'
import { getMerchantDeliveryList, updateDeliveryStatus } from '@/api/delivery'
import { getInterviewList, updateInterviewStatus } from '@/api/interview'
import { submitReport, uploadReportEvidence } from '@/api/report'
import { formatFileUrl, getFilePreviewType, getFileName, downloadFileByUrl } from '@/utils/file'

const router = useRouter()
const loading = ref(false)
const hoveredRowId = ref(null)
const interviewDialogVisible = ref(false)
const resumeDialogVisible = ref(false)
const reportDialogVisible = ref(false)
const resumeCollapseActive = ref(['skills', 'advantage', 'education', 'experience', 'project'])
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')
const previewSourceUrl = ref('')
const previewObjectUrl = ref('')
const previewScale = ref(1)
const previewMinScale = 0.5
const previewMaxScale = 2.5
const previewFitMode = ref('none')
const previewFrameRef = ref(null)
const previewBaseSize = { width: 794, height: 1123 } // A4 纸张在 96dpi 下的近似尺寸
let previewObserver = null

// 统一处理“可能为空”的状态值（默认 0）
const normalizeStatus = (status) => {
  if (status === null || status === undefined || status === '') return 0
  const num = Number(status)
  return Number.isNaN(num) ? 0 : num
}
const interviewForm = reactive({ time: '', mode: 'ONLINE', location: '', remark: '' })
const parseInterviewFormTime = (value) => {
  const raw = String(value || '').trim()
  if (!raw) return null
  const normalized = raw.replace('T', ' ').replace(/-/g, '/')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}
const disablePastInterviewDate = (date) => {
  if (!(date instanceof Date)) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}
const reportForm = reactive({ reasonType: '', detail: '' })
const reportTarget = ref(null)
const reportEvidenceList = ref([])
const reportUploadList = ref([])
const reportReasonOptions = [
  { label: '辱骂/骚扰', value: 'ABUSE' },
  { label: '诈骗/虚假信息', value: 'FRAUD' },
  { label: '恶意爽约', value: 'NO_SHOW' },
  { label: '冒用他人信息', value: 'IMPERSONATE' },
  { label: '其他违规行为', value: 'OTHER' }
]

// 查询条件 + 分页
const query = reactive({ jobId: null, status: null, degree: null, current: 1, size: 10 })

const candidates = ref([])
const total = ref(0)
const jobOptions = ref([])
const selectedRow = ref(null)
const currentResume = ref(null)
const interviewList = ref([])
const interviewLoading = ref(false)
const FILTER_STORAGE_KEY = 'merchant_resume_filters'

// 状态字典
const getStatusText = (status) => {
  const map = { 0: '已投递', 1: '已初筛', 2: '面试邀约', 3: '不合适' }
  return map[status]
}
const getStatusTag = (status) => {
  const map = { 0: 'info', 1: 'primary', 2: 'success', 3: 'danger' }
  return map[status]
}

// 面试状态字典
const getInterviewStatusText = (status) => {
  const map = { 0: '待确认', 1: '已确认', 2: '已拒绝', 3: '已取消', 4: '已完成' }
  return map[status] || '未知'
}
const getInterviewStatusTag = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info', 4: 'primary' }
  return map[status] || 'info'
}

// 统一判断面试方式，明确区分线上/线下
const getInterviewMode = (method) => {
  if (!method) return 'UNKNOWN'
  const text = String(method)
  if (text.includes('线下')) return 'OFFLINE'
  if (text.includes('线上')) return 'ONLINE'
  return 'UNKNOWN'
}
const getInterviewModeLabel = (method) => {
  const mode = getInterviewMode(method)
  if (mode === 'ONLINE') return '线上面试'
  if (mode === 'OFFLINE') return '线下面试'
  return '未标注'
}
const getInterviewModeTag = (method) => {
  const mode = getInterviewMode(method)
  if (mode === 'ONLINE') return 'success'
  if (mode === 'OFFLINE') return 'warning'
  return 'info'
}
const isOnlineInterview = (method) => getInterviewMode(method) === 'ONLINE'

// 更新面试状态（商家侧：取消/完成）
const handleInterviewStatus = async (item, status) => {
  if (!item?.id) return
  try {
    await updateInterviewStatus({ id: item.id, status })
    ElMessage.success('面试状态已更新')
    await loadInterviewList(currentResume.value?.id)
  } catch (error) {
    console.error('更新面试状态失败:', error)
    ElMessage.error('更新面试状态失败')
  }
}

const formatGender = (gender) => {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '保密'
}
const getApplicantName = (applicant) => {
  if (!applicant) return '匿名'
  return applicant.realName || applicant.nickname || '匿名'
}

// 简单状态机校验：允许从邀约/不合适撤回到“已初筛”（通过下拉执行）
const canUpdateStatus = (current, target) => {
  if (current === 2 || current === 3) return target === 1 || target === current
  if (current === 1) return target === 2 || target === 3 || target === 1
  return target === 0 || target === 1 || target === 2 || target === 3
}

const parseSkills = (skills) => {
  if (!skills) return []
  if (Array.isArray(skills)) return skills
  const text = String(skills).trim()
  if (!text) {
    return []
  }
  if (text.startsWith('[') && text.endsWith(']')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) {
        return parsed.map(item => String(item || '').trim()).filter(Boolean)
      }
    } catch (error) {
      // JSON 解析失败时回退分隔符拆分
    }
  }
  if (text.startsWith('{')) {
    return []
  }
  return text.split(',').map(item => item.trim()).filter(Boolean)
}

const isNonEmptyValue = (value) => {
  if (value === null || value === undefined) return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.values(value).some(isNonEmptyValue)
  return String(value).trim() !== ''
}

const isValidResumeItem = (item) => {
  if (!item || typeof item !== 'object') return false
  return Object.values(item).some(isNonEmptyValue)
}

const parseJsonList = (json) => {
  if (!json) return []
  if (Array.isArray(json)) return json.filter(isValidResumeItem)
  try {
    const data = JSON.parse(json)
    return Array.isArray(data) ? data.filter(isValidResumeItem) : []
  } catch (error) {
    return []
  }
}

const formatRange = (range) => {
  if (!Array.isArray(range) || range.length === 0) return '-'
  if (range.length === 1) return range[0] || '-'
  return `${range[0] || '-'} 至 ${range[1] || '-'}`
}

const buildEduTitle = (edu) => {
  if (!edu) return '未填写'
  const parts = [edu.school, edu.major, edu.degree].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildExpTitle = (exp) => {
  if (!exp) return '未填写'
  const parts = [exp.company, exp.position].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildProjectTitle = (proj) => {
  if (!proj) return '未填写'
  const parts = [proj.name, proj.role].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildCertTitle = (cert) => {
  if (!cert) return '未填写'
  const parts = [cert.name, cert.issuer].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildAwardTitle = (award) => {
  if (!award) return '未填写'
  const parts = [award.name, award.level].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildLangTitle = (lang) => {
  if (!lang) return '未填写'
  const parts = [lang.name, lang.level].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const formatIdentity = (value) => {
  if (value === 'STUDENT') return '在校生'
  if (value === 'FRESH_GRAD') return '应届生'
  if (value === 'WORKER') return '职场人士'
  return value || '-'
}

const handleStatus = async (row, newStatus) => {
  if (!canUpdateStatus(row.status, newStatus)) {
    ElMessage.warning('当前状态不允许变更')
    return
  }

  try {
    await updateDeliveryStatus({ id: row.id, status: newStatus })
    ElMessage.success('状态更新成功')
    fetchData()
  } catch (error) {
    console.error('更新状态失败:', error)
    ElMessage.error('状态更新失败')
  }
}

const openInterview = (row, reset = false) => {
  selectedRow.value = row
  if (reset) {
    interviewForm.time = ''
    interviewForm.mode = 'ONLINE'
    interviewForm.location = ''
    interviewForm.remark = ''
  } else {
    // 回填已有面试信息时，未知方式默认按线上面试处理。
    interviewForm.time = row?.interviewTime || ''
    interviewForm.mode = getInterviewMode(row?.interviewMethod) === 'OFFLINE' ? 'OFFLINE' : 'ONLINE'
    interviewForm.location = row?.interviewLocation || ''
    interviewForm.remark = row?.interviewRemark || row?.feedback || ''
  }
  interviewDialogVisible.value = true
}

const submitInterview = async () => {
  if (!selectedRow.value) {
    interviewDialogVisible.value = false
    return
  }
  if (!interviewForm.time) {
    ElMessage.warning('请先选择面试时间')
    return
  }
  const parsedInterviewTime = parseInterviewFormTime(interviewForm.time)
  if (!parsedInterviewTime) {
    ElMessage.warning('面试时间格式有误，请重新选择')
    return
  }
  if (parsedInterviewTime.getTime() <= Date.now()) {
    ElMessage.warning('面试时间需晚于当前时间')
    return
  }
  if (!interviewForm.location) {
    ElMessage.warning(interviewForm.mode === 'ONLINE' ? '请填写会议链接/方式' : '请填写面试地址')
    return
  }
  try {
    const methodLabel = interviewForm.mode === 'OFFLINE' ? '线下面试' : '线上面试'
    const payload = {
      id: selectedRow.value.id,
      status: 2,
      feedback: interviewForm.remark || '',
      interviewTime: interviewForm.time,
      interviewLocation: interviewForm.location || '',
      interviewMethod: methodLabel,
      interviewRemark: interviewForm.remark || ''
    }
    await updateDeliveryStatus(payload)
    ElMessage.success('面试邀约已发送')
    fetchData()
  } catch (error) {
    console.error('面试邀约发送失败:', error)
    ElMessage.error('面试邀约发送失败')
  } finally {
    interviewDialogVisible.value = false
  }
}

const openResume = async (row) => {
  try {
    // 二次确认，避免误触导致状态变更
    await ElMessageBox.confirm(
      '确认查看该简历吗？查看后状态将标记为“已初筛”。',
      '确认查看',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }

  currentResume.value = {
    ...row,
    applicant: {
      ...row.applicant,
      educationList: parseJsonList(row.applicant?.educationJson),
      experienceList: parseJsonList(row.applicant?.experienceJson),
      projectList: parseJsonList(row.applicant?.projectJson),
      certificateList: parseJsonList(row.applicant?.certificateJson),
      awardList: parseJsonList(row.applicant?.awardJson),
      languageList: parseJsonList(row.applicant?.languageJson)
    }
  }
  resumeDialogVisible.value = true
  await loadInterviewList(row.id)

  // 进入简历详情视为“已初筛”
  if (row.status === 0) {
    await handleStatus(row, 1)
  }
}

const handleDropdownCommand = async (row, cmd) => {
  if (cmd === 'revert_viewed') {
    try {
      await ElMessageBox.confirm(
        '确认将状态撤回为“已初筛”吗？',
        '确认撤回',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch (error) {
      return
    }
    await handleStatus(row, 1)
    return
  }
  if (cmd === 2) {
    openInterview(row)
    return
  }
  await handleStatus(row, cmd)
}

// 统一生成沟通页跳转参数，避免不同入口重复拼接。
const goChat = (row, { autoRequest = 0, includeAvatar = true } = {}) => {
  const targetId = row?.applicant?.userId
  if (!targetId) {
    ElMessage.warning('未获取到候选人账号信息')
    return
  }
  const query = {
    targetId,
    targetName: getApplicantName(row.applicant),
    jobTitle: row.jobName || '',
    jobId: row.jobId || ''
  }
  if (includeAvatar) {
    query.targetAvatar = row.applicant?.avatar || ''
  }
  if (autoRequest) {
    query.autoRequest = autoRequest
  }
  router.push({
    path: '/merchant/chat',
    query
  })
}

// 打开举报弹窗（商家举报求职者）
const openReportDialog = (row) => {
  const targetId = row?.applicant?.userId
  if (!targetId) {
    ElMessage.warning('未获取到候选人账号信息')
    return
  }
  reportTarget.value = row
  reportForm.reasonType = reportReasonOptions[0]?.value || ''
  reportForm.detail = ''
  reportEvidenceList.value = []
  reportUploadList.value = []
  reportDialogVisible.value = true
}

// 举报证据上传
const handleReportUpload = async (options) => {
  const file = options?.file
  if (!file) {
    options?.onError?.(new Error('未获取到文件'))
    return
  }
  try {
    const res = await uploadReportEvidence(file)
    const url = res.data
    if (url) {
      reportEvidenceList.value.push(url)
      reportUploadList.value = [
        ...reportUploadList.value,
        { name: file.name, url }
      ]
      options?.onSuccess?.(res, file)
    } else {
      options?.onError?.(new Error('上传失败'))
    }
  } catch (error) {
    options?.onError?.(error)
  }
}

const handleReportRemove = (file) => {
  if (!file) return
  const url = file.url || ''
  reportEvidenceList.value = reportEvidenceList.value.filter(item => item !== url)
  reportUploadList.value = reportUploadList.value.filter(item => item.url !== url)
}

// 拼接举报原因（包含必要说明与关联职位）
const buildReportReason = () => {
  const reasonLabel = reportReasonOptions.find((item) => item.value === reportForm.reasonType)?.label || ''
  const detail = (reportForm.detail || '').trim()
  const jobName = reportTarget.value?.jobName || ''
  const parts = [reasonLabel, detail, jobName ? `关联职位：${jobName}` : ''].filter(Boolean)
  return parts.join('；')
}

// 提交举报
const submitReportForm = async () => {
  if (!reportTarget.value) {
    reportDialogVisible.value = false
    return
  }
  if (!reportForm.reasonType) {
    ElMessage.warning('请选择举报原因')
    return
  }
  const reason = buildReportReason()
  if (!reason) {
    ElMessage.warning('请补充举报说明')
    return
  }
  if (!reportEvidenceList.value.length) {
    ElMessage.warning('请至少上传1份证据附件（截图或 PDF）')
    return
  }
  try {
    await ElMessageBox.confirm('确认提交举报吗？', '提交确认', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  try {
    await submitReport({
      type: 'USER',
      targetId: reportTarget.value?.applicant?.userId,
      reason,
      jobId: reportTarget.value?.jobId || null,
      evidenceList: reportEvidenceList.value
    })
    ElMessage.success('举报已提交')
    reportDialogVisible.value = false
  } catch (error) {
    console.error('举报提交失败:', error)
    ElMessage.error('举报提交失败，请稍后重试')
  }
}

const cleanupPreview = () => {
  if (previewObjectUrl.value) {
    URL.revokeObjectURL(previewObjectUrl.value)
    previewObjectUrl.value = ''
  }
  previewUrl.value = ''
  previewSourceUrl.value = ''
  previewScale.value = 1
  previewFitMode.value = 'none'
  stopPreviewObserver()
}

const startPreviewObserver = () => {
  if (!previewFrameRef.value || previewObserver) return
  previewObserver = new ResizeObserver(() => {
    if (previewFitMode.value !== 'none') {
      updateFitScale()
    }
  })
  previewObserver.observe(previewFrameRef.value)
}

const stopPreviewObserver = () => {
  if (previewObserver) {
    previewObserver.disconnect()
    previewObserver = null
  }
}

const setPreviewScale = (value) => {
  const next = Math.min(previewMaxScale, Math.max(previewMinScale, value))
  previewScale.value = Number(next.toFixed(2))
}

const zoomIn = () => {
  previewFitMode.value = 'none'
  setPreviewScale(previewScale.value + 0.1)
}

const zoomOut = () => {
  previewFitMode.value = 'none'
  setPreviewScale(previewScale.value - 0.1)
}

const resetZoom = () => {
  previewFitMode.value = 'none'
  setPreviewScale(1)
}

const formatZoomTooltip = (value) => {
  return `${Math.round(value * 100)}%`
}

const handleZoomInput = () => {
  previewFitMode.value = 'none'
}

const updateFitScale = () => {
  const frame = previewFrameRef.value
  if (!frame) return
  const width = frame.clientWidth || 0
  const height = frame.clientHeight || 0
  if (!width || !height) return
  if (previewFitMode.value === 'width') {
    setPreviewScale(width / previewBaseSize.width)
    return
  }
  if (previewFitMode.value === 'height') {
    setPreviewScale(height / previewBaseSize.height)
  }
}

const fitWidth = () => {
  previewFitMode.value = 'width'
  updateFitScale()
}

const fitHeight = () => {
  previewFitMode.value = 'height'
  updateFitScale()
}

const handleWheelZoom = (event) => {
  if (!(event.ctrlKey || event.metaKey)) return
  event.preventDefault()
  previewFitMode.value = 'none'
  const step = event.deltaY > 0 ? -0.1 : 0.1
  setPreviewScale(previewScale.value + step)
}

const handlePreviewKeydown = (event) => {
  if (!previewVisible.value) return
  if (!(event.ctrlKey || event.metaKey)) return
  const key = event.key
  if (key === '=' || key === '+') {
    event.preventDefault()
    zoomIn()
    return
  }
  if (key === '-') {
    event.preventDefault()
    zoomOut()
    return
  }
  if (key === '0') {
    event.preventDefault()
    resetZoom()
  }
}

const fetchPdfPreview = async (url, name) => {
  try {
    const res = await axios.get(url, { responseType: 'blob' })
    const blobUrl = URL.createObjectURL(res.data)
    previewObjectUrl.value = blobUrl
    previewUrl.value = blobUrl
    previewSourceUrl.value = url
    previewName.value = name || getFileName(url) || '附件简历'
    previewVisible.value = true
  } catch (error) {
    console.error('在线预览失败:', error)
    // 兜底：直接加载原始链接，尽量保证可查看
    previewSourceUrl.value = url
    previewUrl.value = url
    previewName.value = name || getFileName(url) || '附件简历'
    previewVisible.value = true
    ElMessage.warning('预览通道异常，已尝试直接加载附件')
  }
}

const openResumeAttachment = (url, name) => {
  if (!url) {
    ElMessage.warning('暂无附件简历')
    return
  }
  const fullUrl = formatFileUrl(url)
  const previewType = getFilePreviewType(name || url)
  if (previewType !== 'pdf') {
    ElMessage.info('在线预览仅支持 PDF，DOC/DOCX 请下载查看')
    return
  }
  cleanupPreview()
  fetchPdfPreview(fullUrl, name)
}

// 附件简历：下载
const downloadResumeAttachment = async (url, name = '') => {
  if (!url) {
    ElMessage.warning('暂无附件简历')
    return
  }
  try {
    await downloadFileByUrl(url, name || getFileName(url))
    ElMessage.success('开始下载附件')
  } catch (error) {
    console.error('下载附件失败:', error)
    const fallbackUrl = formatFileUrl(url)
    window.open(fallbackUrl, '_blank')
    ElMessage.warning('下载通道异常，已尝试在新窗口打开附件')
  }
}

// 表格行点击即视为“查看简历”，但操作按钮区域除外
const handleRowClick = (row, column, event) => {
  if (!event || !event.target) {
    openResume(row)
    return
  }
  const target = event.target
  if (target.closest && (target.closest('.op-actions') || target.closest('.op-col') || target.closest('.attach-cell'))) {
    return
  }
  openResume(row)
}

const handleRowEnter = (row) => {
  hoveredRowId.value = row?.id ?? null
}

const handleRowLeave = () => {
  hoveredRowId.value = null
}

const getRowClass = ({ row }) => {
  return hoveredRowId.value === row?.id ? 'row-hovered' : ''
}

// 拉取商家职位下拉选项
const fetchJobs = async () => {
  try {
    const res = await getMerchantJobs({ current: 1, size: 200 })
    jobOptions.value = res.data?.records || []
  } catch (error) {
    console.error('加载职位失败:', error)
  }
}

// 拉取候选人列表
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMerchantDeliveryList({
      current: query.current,
      size: query.size,
      jobId: query.jobId,
      status: query.status,
      degree: query.degree
    })
    const records = res.data?.records || []
    candidates.value = records.map((item) => ({
      ...item,
      status: normalizeStatus(item.status),
      hasResumeAttachment: Number(item.hasResumeAttachment || 0) === 1,
      attachmentStatus: item.attachmentStatus ?? null
    }))
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取候选人失败:', error)
    ElMessage.error('获取候选人失败')
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => {
  query.current = 1
  fetchData()
}

const handleReset = () => {
  query.jobId = null
  query.status = null
  query.degree = null
  query.current = 1
  sessionStorage.removeItem(FILTER_STORAGE_KEY)
  fetchData()
}

// 统一预处理日期文本，避免不同展示函数重复做空值和分隔符兼容。
const normalizeDateText = (str) => {
  if (!str) return ''
  const value = String(str).trim()
  if (!value) return ''
  return value.replace('T', ' ')
}

// 统一日期展示（仅到日期）
const formatDate = (str) => {
  const normalized = normalizeDateText(str)
  if (!normalized) return ''
  return normalized.substring(0, 10)
}

// 拉取面试记录（多轮）
const loadInterviewList = async (deliveryId) => {
  if (!deliveryId) {
    interviewList.value = []
    return
  }
  interviewLoading.value = true
  try {
    const res = await getInterviewList(deliveryId)
    const list = res.data || []
    interviewList.value = list.slice().sort((a, b) => Number(a.roundNo) - Number(b.roundNo))
  } catch (error) {
    console.error('获取面试记录失败:', error)
    interviewList.value = []
  } finally {
    interviewLoading.value = false
  }
}

// 统一日期时间展示（到分钟）
const formatDateTime = (str) => {
  const normalized = normalizeDateText(str)
  if (!normalized) return ''
  return normalized.length >= 16 ? normalized.substring(0, 16) : normalized
}

// 判断是否已授权查看附件简历
const canViewAttachment = (row) => {
  return row?.attachmentStatus === 1 && !!row?.resumeUrl
}

const isAttachmentPending = (row) => {
  return row?.attachmentStatus === 0
}

// 申请查看附件简历：先落库申请，再发起沟通话术
const requestAttachment = (row) => {
  if (!row?.hasResumeAttachment) {
    ElMessage.warning('对方未上传附件简历')
    return
  }
  if (isAttachmentPending(row)) {
    ElMessage.info('附件申请已提交，请等待对方同意')
  } else {
    ElMessage.info('已进入沟通，请确认岗位后发起附件申请')
  }
  goChat(row, { autoRequest: 1, includeAvatar: false })
}

onMounted(async () => {
  const stored = sessionStorage.getItem(FILTER_STORAGE_KEY)
  if (stored) {
    try {
      const data = JSON.parse(stored)
      query.jobId = data.jobId ?? null
      query.status = data.status ?? null
      query.degree = data.degree ?? null
      query.current = data.current ?? 1
      query.size = data.size ?? 10
    } catch (error) {
      console.error('筛选条件解析失败:', error)
    }
  }
  await fetchJobs()
  fetchData()
  window.addEventListener('keydown', handlePreviewKeydown)
})

watch(
  () => ({ ...query }),
  (val) => {
    sessionStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(val))
  },
  { deep: true }
)

watch(
  () => previewVisible.value,
  async (val) => {
    if (val) {
      await nextTick()
      startPreviewObserver()
      if (previewFitMode.value !== 'none') {
        updateFitScale()
      }
      return
    }
    stopPreviewObserver()
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handlePreviewKeydown)
  stopPreviewObserver()
})
</script>

<style scoped>
/* 页面布局 */
.resume-page {
  background: var(--ui-surface);
  border-radius: 16px;
  border: 1px solid var(--ui-border);
  width: min(100%, var(--ui-shell-max-width));
  margin: 0 auto;
  padding: 16px;
  box-shadow: var(--ui-shadow-sm);
  min-height: calc(100vh - 140px);
  box-sizing: border-box;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.toolbar-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ui-text);
}

.toolbar-desc {
  font-size: 12px;
  color: var(--ui-muted);
  margin-top: 4px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--ui-surface-muted);
  border-radius: 12px;
  border: 1px solid var(--ui-border);
}

.filter-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.filter-control {
  width: auto;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 辅助类 */
.bg-white {
  background-color: var(--ui-surface);
  border: 1px solid var(--ui-border);
}
.p-6 { padding: 1.5rem; }
.rounded { border-radius: 16px; }
.shadow-sm { box-shadow: none; }
.min-h-screen { min-height: 100vh; }
.flex { display: flex; }
.flex-wrap { flex-wrap: wrap; }
.gap-4 { gap: 1rem; }
.gap-3 { gap: 0.75rem; }
.mb-6 { margin-bottom: 1.5rem; }
.p-4 { padding: 1rem; }
.bg-gray-50 { background-color: var(--ui-surface-muted); }
.border { border-width: 1px; }
.border-gray-100 { border-color: var(--ui-border); }
.items-center { align-items: center; }
.font-bold { font-weight: 700; }
.text-gray-800 { color: var(--ui-text); }
.text-xs { font-size: 0.75rem; }
.text-gray-500 { color: var(--ui-muted); }
.text-gray-400 { color: #8e8e93; }
.row-hint { font-size: 0.75rem; color: #8e8e93; margin-top: 2px; }
.attach-cell { display: flex; align-items: center; justify-content: center; }
.attach-empty { color: #8e8e93; font-size: 0.75rem; }
.attach-btn { font-size: 0.75rem; padding: 0; }
.attach-pending {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}
.mt-1 { margin-top: 0.25rem; }
.mt-6 { margin-top: 1.5rem; }
.justify-end { justify-content: flex-end; }
.ml-2 { margin-left: 0.5rem; }
.op-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(96px, 1fr));
  gap: 8px;
  justify-items: stretch;
  align-items: center;
}
.op-actions :deep(.el-button) { margin-left: 0; width: 100%; }
.op-actions :deep(.el-dropdown) { width: 100%; }
.op-actions :deep(.el-dropdown .el-button) { width: 100%; }
.resume-page :deep(.op-col .cell) { white-space: normal; }
.resume-page :deep(.el-table__row) { cursor: pointer; }
.resume-page :deep(.row-hovered td) { background: var(--ui-accent-light); }
.resume-page :deep(.op-actions .el-button) { cursor: pointer; }
.action-btn {
  width: 100%;
  height: 30px;
  line-height: 30px;
  justify-content: center;
  padding: 0 8px;
  white-space: nowrap;
}
.action-btn :deep(.el-icon) { margin-right: 4px; }

.resume-panel { display: flex; flex-direction: column; gap: 1rem; }
.resume-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}
.resume-header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1 1 280px;
  min-width: 0;
}
.report-btn {
  border-radius: 999px;
  flex-shrink: 0;
}
.resume-name { font-size: 1.2rem; font-weight: 600; color: var(--ui-text); }
.resume-meta { color: var(--ui-muted); font-size: 0.85rem; }
.resume-layout {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: clamp(12px, 2vw, 20px);
}
.resume-left { min-width: 0; }
.resume-right { display: flex; flex-direction: column; gap: 1rem; }
.resume-right .resume-block { background: var(--ui-surface-muted); border-radius: 12px; padding: 12px; }
.resume-block { display: flex; flex-direction: column; gap: 0.5rem; }
.resume-title { font-weight: 600; color: var(--ui-text); }
.resume-text { color: var(--ui-muted-strong); line-height: 1.6; }
.resume-tags { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.resume-empty { color: #8e8e93; font-size: 0.85rem; }
.report-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #8e8e93;
}
.report-upload-tip {
  font-size: 12px;
  color: #8e8e93;
  margin-top: 6px;
}
.resume-attachment { display: flex; flex-direction: column; gap: 0.5rem; }
.resume-attachment-actions { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
.resume-attachment-buttons { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.resume-preview-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.resume-preview-slider {
  width: min(180px, 48vw);
  flex: 0 1 180px;
}

.resume-preview-zoom {
  font-size: 12px;
  color: var(--ui-muted);
  min-width: 44px;
  text-align: right;
}

.resume-preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.resume-preview-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ui-text);
  max-width: min(100%, 420px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-preview-close-btn {
  font-weight: 600;
  background: #e11d48;
  border-color: #e11d48;
  box-shadow: 0 6px 16px rgba(225, 29, 72, 0.25);
}

.resume-preview-close-btn:hover,
.resume-preview-close-btn:focus {
  background: #be123c;
  border-color: #be123c;
}

.resume-preview-frame {
  width: 100%;
  height: min(80vh, 820px);
  border-radius: 12px;
  background: var(--ui-surface-muted);
  border: 1px solid var(--ui-border);
  overflow: auto;
}

.resume-preview-zoom-layer {
  width: 100%;
  height: 100%;
}

.resume-preview-frame iframe {
  width: 100%;
  height: 100%;
}
.interview-list { display: flex; flex-direction: column; gap: 0.75rem; }
.interview-item { padding: 10px 12px; background: var(--ui-surface-muted); border: 1px solid var(--ui-border); border-radius: 12px; }
.interview-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.interview-round { font-weight: 600; color: var(--ui-text); font-size: 13px; }
.interview-meta { font-size: 12px; color: var(--ui-muted-strong); margin-top: 2px; }
.interview-actions { margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap; }
.resume-file-name { font-size: 0.9rem; color: var(--ui-text); font-weight: 600; }
.resume-list { display: flex; flex-direction: column; gap: 0.5rem; }
.resume-list-item { display: flex; flex-direction: column; gap: 0.25rem; padding: 8px 10px; background: var(--ui-surface); border-radius: 10px; border: 1px solid var(--ui-border); }
.resume-item-title { font-weight: 600; color: var(--ui-text); }
.resume-item-meta { color: var(--ui-muted); font-size: 0.82rem; }
.resume-item-text { color: var(--ui-muted-strong); font-size: 0.9rem; line-height: 1.5; }
.resume-collapse :deep(.el-collapse-item__header) {
  font-weight: 600;
  color: var(--ui-text);
}
.resume-collapse :deep(.el-collapse-item__content) {
  padding: 6px 0 12px;
}

.resume-page :deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

@media (max-width: 900px) {
  .resume-layout {
    grid-template-columns: 1fr;
  }

  .resume-preview-slider {
    width: 100%;
    flex-basis: 100%;
  }

  .resume-preview-toolbar {
    justify-content: flex-start;
  }

  .resume-preview-frame {
    height: min(72vh, 680px);
  }
}

@media (max-width: 1200px) {
  .op-actions {
    grid-template-columns: repeat(2, minmax(96px, 1fr));
  }
}

@media (max-width: 900px) {
  .op-actions {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .filter-control {
    width: 100%;
  }
  .filter-panel {
    align-items: stretch;
  }
  .filter-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .filter-actions > * {
    flex: 1 1 120px;
  }
}
</style>
