<!--
文件速览：
1. 文件职责：管理员治理通知页，负责筛选治理事项、创建通知、远程搜索目标用户 / 职位 / 商家、查看详情、执行复核与关闭动作，并对封禁申诉待复核事项提供高优先入口与创建前高风险二次确认。
2. 页面入口：管理员路由 `/admin/governance`。
3. 关键结构：query、focusPresetOptions、notices、pagination、detailVisible、reviewDialogVisible、createDialogVisible、selectedRelationContext、relationSummaryCards、createReadonlyHints、createReadonlyHintOverview、selectedTargetUserLabel、targetUserOptions、relatedJobOptions、relatedMerchantOptions。
4. 阅读建议：先看顶部治理流程与高优先入口，再看创建弹窗里的远程搜索、对象摘要、一致性检查与高风险提交确认，最后看详情抽屉里的申诉说明与复核动作。
-->
<template>
  <div class="admin-page governance-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">治理通知</h2>
        <p class="page-desc">围绕整改、警告与复核建立统一闭环，先看待复核事项，再处理逾期整改。</p>
      </div>
      <div class="header-actions">
        <div class="header-meta">
          <span class="meta-label">当前筛选</span>
          <span class="meta-value">{{ pagination.total }}</span>
          <span class="meta-unit">条事项</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">创建通知</el-button>
      </div>
    </div>

    <el-card class="workflow-card">
      <div class="workflow-list">
        <div class="workflow-item">
          <span class="workflow-index">01</span>
          <div class="workflow-copy">
            <div class="workflow-title">查看平台要求</div>
            <div class="workflow-desc">明确关联对象、整改要求、截止时间与当前状态。</div>
          </div>
        </div>
        <div class="workflow-item">
          <span class="workflow-index">02</span>
          <div class="workflow-copy">
            <div class="workflow-title">等待用户反馈</div>
            <div class="workflow-desc">商家提交复核后进入待复核，管理员再做最终处理。</div>
          </div>
        </div>
        <div class="workflow-item">
          <span class="workflow-index">03</span>
          <div class="workflow-copy">
            <div class="workflow-title">完成闭环复核</div>
            <div class="workflow-desc">通过、驳回或关闭都要带说明，确保治理记录可追溯。</div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="focus-card">
      <div class="focus-card__head">
        <div>
          <div class="focus-card__title">高优先治理入口</div>
          <div class="focus-card__desc">先复核封禁申诉，再处理待复核整改与已逾期事项，避免误封和治理积压。</div>
        </div>
        <div class="focus-card__actions">
          <el-tag v-if="activeFocusPreset" type="danger" effect="light">
            当前聚焦：{{ activeFocusPreset.title }}
          </el-tag>
          <el-button v-if="activeFocusPreset" text @click="resetFilter">返回全部事项</el-button>
        </div>
      </div>
      <div class="focus-strip">
        <button
          v-for="item in focusPresetOptions"
          :key="item.key"
          type="button"
          class="focus-chip"
          :class="{ 'focus-chip--active': activeFocusKey === item.key }"
          @click="applyFocusPreset(item)"
        >
          <span class="focus-chip__eyebrow">{{ item.eyebrow }}</span>
          <span class="focus-chip__title">{{ item.title }}</span>
          <span class="focus-chip__desc">{{ item.desc }}</span>
          <span class="focus-chip__meta">{{ item.meta }}</span>
        </button>
      </div>
    </el-card>

    <el-card class="filter-card">
      <div class="filter-row adaptive-filter-row">
        <el-select v-model="query.targetRole" placeholder="目标角色" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="商家" value="MERCHANT" />
          <el-option label="求职者" value="APPLICANT" />
        </el-select>
        <el-select v-model="query.noticeType" placeholder="治理类型" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option
            v-for="item in noticeTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.status" placeholder="事项状态" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.sourceModule" placeholder="来源模块" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option
            v-for="item in sourceModuleOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <div class="adaptive-filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
      <div class="batch-row adaptive-meta-row">
        <div class="batch-hint">优先处理“待复核”和“已逾期”事项，避免治理链路停滞。</div>
        <el-checkbox v-model="query.overdueOnly" @change="applyFilter">仅看已逾期</el-checkbox>
      </div>
    </el-card>

    <el-card>
      <el-table
        :data="notices"
        stripe
        v-loading="loading"
        :row-class-name="getRowClassName"
      >
        <el-table-column label="通知编号" min-width="180">
          <template #default="{ row }">
            <div class="notice-no">
              <div class="notice-no__value">{{ row.noticeNo }}</div>
              <div class="notice-no__time">创建于 {{ formatText(row.createTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="治理事项" min-width="300">
          <template #default="{ row }">
            <div class="notice-title-cell">
              <div class="notice-title-row">
                <span class="notice-title">{{ row.title }}</span>
                <span v-if="isBanAppealPending(row)" class="focus-pill">封禁申诉待复核</span>
                <span v-if="row.overdue" class="overdue-pill">已逾期</span>
              </div>
              <div class="notice-summary">{{ row.summary || '暂无摘要' }}</div>
              <div class="notice-sub-meta">
                <span>对象：{{ row.targetUserName || '未知用户' }}</span>
                <span v-if="row.relatedJobTitle">职位：{{ row.relatedJobTitle }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-tag :type="noticeTypeTag(row.noticeType)" effect="plain">
              {{ noticeTypeText(row.noticeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="severityTag(row.severity)" effect="light">
              {{ severityText(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            {{ sourceModuleText(row.sourceModule) }}
          </template>
        </el-table-column>
        <el-table-column label="处理时限" width="180">
          <template #default="{ row }">
            <div class="due-time-cell">
              <div>{{ formatText(row.dueTime) }}</div>
              <div class="due-time-meta">{{ row.overdue ? '已超过处理期限' : '按时处理中' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-bar" :class="statusClass(row.status)"></span>
              <el-tag :type="statusTag(row.status)">
                {{ statusText(row.status) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近动作" width="180">
          <template #default="{ row }">
            {{ formatText(row.latestActionTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row adaptive-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          :page-size="pagination.size"
          :current-page="pagination.current"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="detailVisible"
      size="520px"
      title="治理事项详情"
      destroy-on-close
    >
      <div v-if="detailLoading" class="drawer-loading">
        <el-skeleton :rows="10" animated />
      </div>
      <div v-else-if="currentNotice" class="drawer-body">
        <div class="drawer-hero">
          <div class="drawer-hero__title">{{ currentNotice.title }}</div>
          <div class="drawer-hero__tags">
            <el-tag :type="noticeTypeTag(currentNotice.noticeType)" effect="plain">
              {{ noticeTypeText(currentNotice.noticeType) }}
            </el-tag>
            <el-tag :type="severityTag(currentNotice.severity)" effect="light">
              {{ severityText(currentNotice.severity) }}
            </el-tag>
            <el-tag :type="statusTag(currentNotice.status)">
              {{ statusText(currentNotice.status) }}
            </el-tag>
            <el-tag v-if="currentNotice.overdue" type="danger">已逾期</el-tag>
          </div>
        </div>

        <div class="drawer-meta-grid">
          <div class="meta-card">
            <div class="meta-card__label">目标对象</div>
            <div class="meta-card__value">{{ currentNotice.targetUserName || '未知用户' }}</div>
            <div class="meta-card__sub">{{ targetRoleText(currentNotice.targetRole) }}</div>
          </div>
          <div class="meta-card">
            <div class="meta-card__label">关联对象</div>
            <div class="meta-card__value">{{ currentNotice.relatedJobTitle || '未绑定职位' }}</div>
            <div class="meta-card__sub">来源：{{ sourceModuleText(currentNotice.sourceModule) }}</div>
          </div>
          <div class="meta-card">
            <div class="meta-card__label">最近动作</div>
            <div class="meta-card__value">{{ formatText(currentNotice.latestActionTime) }}</div>
            <div class="meta-card__sub">创建：{{ formatText(currentNotice.createTime) }}</div>
          </div>
          <div class="meta-card">
            <div class="meta-card__label">处理时限</div>
            <div class="meta-card__value">{{ formatText(currentNotice.dueTime) }}</div>
            <div class="meta-card__sub">{{ currentNotice.overdue ? '已超过时限' : '仍在时限内' }}</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">事项说明</div>
          <div class="detail-card">
            <div class="detail-line">
              <span class="detail-label">摘要</span>
              <span class="detail-value">{{ currentNotice.summary || '暂无摘要' }}</span>
            </div>
            <div class="detail-line detail-line--block">
              <span class="detail-label">详细说明</span>
              <div class="detail-rich-text">{{ currentNotice.detail || '暂无详细说明' }}</div>
            </div>
            <div class="detail-line detail-line--block">
              <span class="detail-label">平台要求</span>
              <div class="detail-rich-text">{{ currentNotice.requiredAction || '暂无要求' }}</div>
            </div>
            <div class="detail-line">
              <span class="detail-label">通知编号</span>
              <span class="detail-value">{{ currentNotice.noticeNo }}</span>
            </div>
            <div class="detail-line">
              <span class="detail-label">已读要求</span>
              <span class="detail-value">{{ currentNotice.needAck ? '需要确认已读' : '无需确认已读' }}</span>
            </div>
            <div class="detail-line">
              <span class="detail-label">反馈要求</span>
              <span class="detail-value">{{ currentNotice.needReply ? '需要用户反馈' : '无需用户反馈' }}</span>
            </div>
            <div class="detail-line">
              <span class="detail-label">已读时间</span>
              <span class="detail-value">{{ formatText(currentNotice.readTime) }}</span>
            </div>
            <div class="detail-line">
              <span class="detail-label">关闭时间</span>
              <span class="detail-value">{{ formatText(currentNotice.closedTime) }}</span>
            </div>
          </div>
        </div>

        <div v-if="latestAppealAction" class="detail-section">
          <div class="section-title">申诉说明</div>
          <div class="appeal-card">
            <div class="appeal-card__head">
              <div>
                <div class="appeal-card__title">最新封禁申诉内容</div>
                <div class="appeal-card__meta">
                  {{ actorRoleText(latestAppealAction.actorRole) }} · {{ latestAppealAction.actorName || '未知用户' }} ·
                  {{ formatText(latestAppealAction.createTime) }}
                </div>
              </div>
              <el-tag type="danger" effect="light">优先核查</el-tag>
            </div>
            <div class="appeal-card__content">
              {{ latestAppealAction.content || '用户已提交封禁申诉，但未填写补充说明。' }}
            </div>
            <div class="appeal-card__hint">
              建议先核对封禁原因、证据链与限制范围，再决定维持、解除或关闭当前事项。
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">动作时间线</div>
          <el-empty v-if="!currentNotice.actions?.length" description="暂无动作记录" />
          <el-timeline v-else class="action-timeline">
            <el-timeline-item
              v-for="item in currentNotice.actions"
              :key="item.id"
              :timestamp="formatText(item.createTime)"
            >
              <div class="timeline-item">
                <div class="timeline-head">
                  <span class="timeline-title">{{ actionTypeText(item.actionType) }}</span>
                  <span class="timeline-role">{{ actorRoleText(item.actorRole) }}</span>
                </div>
                <div class="timeline-meta">{{ item.actorName || '未知用户' }}</div>
                <div class="timeline-content">{{ item.content || '无补充说明' }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <div class="drawer-actions">
          <el-button
            v-if="canApproveReject(currentNotice)"
            type="success"
            @click="openReviewDialog('APPROVE', currentNotice)"
          >
            通过复核
          </el-button>
          <el-button
            v-if="canApproveReject(currentNotice)"
            type="danger"
            @click="openReviewDialog('REJECT', currentNotice)"
          >
            驳回复核
          </el-button>
          <el-button
            v-if="canCloseNotice(currentNotice)"
            type="warning"
            plain
            @click="openReviewDialog('CLOSE', currentNotice)"
          >
            关闭事项
          </el-button>
        </div>
      </div>
      <el-empty v-else description="请选择需要查看的治理事项" />
    </el-drawer>

    <el-dialog
      v-model="reviewDialogVisible"
      :title="reviewDialogTitle"
      width="460px"
      destroy-on-close
    >
      <div class="review-dialog-copy">{{ reviewDialogHint }}</div>
      <el-input
        v-model="reviewForm.reviewComment"
        type="textarea"
        :rows="5"
        :placeholder="reviewPlaceholder"
      />
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button :type="reviewButtonType" @click="submitReview">
          {{ reviewButtonText }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="createDialogVisible"
      title="创建治理通知"
      width="min(1180px, calc(100vw - 48px))"
      top="4vh"
      class="governance-create-dialog"
      destroy-on-close
    >
      <div class="create-dialog-copy">
        这一步用于手动创建治理事项。优先使用下方模板预填，再补充目标对象、关联对象和平台要求。
      </div>

      <div class="template-strip">
        <button
          v-for="item in createTemplateOptions"
          :key="item.value"
          type="button"
          class="template-chip"
          :data-template="item.value"
          :class="{ 'template-chip--active': createForm.templateKey === item.value }"
          @click="applyCreateTemplate(item.value)"
        >
          <span class="template-chip__title">{{ item.label }}</span>
          <span class="template-chip__desc">{{ item.desc }}</span>
        </button>
      </div>

      <div class="template-hint">
        {{ currentTemplateMeta.hint }}
      </div>

      <div class="prefill-helper">
        <div class="prefill-helper__copy">
          选择关联职位或关联商家后，系统会自动同步可判断的来源业务 ID；你也可以再点一次“按当前对象预填文案”，快速生成更贴近业务对象的标题和说明。
        </div>
        <el-button plain :disabled="!canApplyObjectPreset" @click="applyObjectPresetCopy">按当前对象预填文案</el-button>
      </div>

      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="96px"
        class="create-form"
      >
        <div class="create-form-grid">
          <el-form-item label="目标角色" prop="targetRole">
            <el-select v-model="createForm.targetRole" placeholder="请选择目标角色">
              <el-option label="商家" value="MERCHANT" />
              <el-option label="求职者" value="APPLICANT" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标用户" prop="targetUserId" class="create-form-item--span-2">
            <div
              ref="targetUserPickerRef"
              class="inline-remote-picker"
              :class="{ 'inline-remote-picker--open': targetUserPanelVisible }"
            >
              <el-input
                v-model="targetUserKeyword"
                clearable
                :placeholder="targetUserSearchPlaceholder"
                class="create-remote-select"
                @focus="openTargetUserPanel"
                @input="handleTargetUserKeywordInput"
                @clear="clearTargetUserSelection"
              />
              <div v-if="targetUserPanelVisible" class="inline-remote-picker__panel">
                <div v-if="targetUserLoading" class="inline-remote-picker__state">
                  正在搜索目标用户...
                </div>
                <div v-else-if="!targetUserOptions.length" class="inline-remote-picker__state inline-remote-picker__state--empty">
                  暂无匹配用户，试试账号、昵称、手机号或邮箱。
                </div>
                <button
                  v-for="item in targetUserOptions"
                  v-else
                  :key="item.id"
                  type="button"
                  class="inline-remote-picker__option"
                  :class="{ 'inline-remote-picker__option--active': Number(createForm.targetUserId) === Number(item.id) }"
                  @mousedown.prevent="selectTargetUserOption(item)"
                >
                  <div class="picker-option">
                    <div class="picker-option__title">{{ buildTargetUserOptionView(item).title }}</div>
                    <div class="picker-option__meta">
                      {{ buildTargetUserOptionView(item).meta }}
                    </div>
                  </div>
                </button>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="通知类型" prop="noticeType">
            <el-select v-model="createForm.noticeType" placeholder="请选择通知类型">
              <el-option
                v-for="item in noticeTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="严重级别" prop="severity">
            <el-select v-model="createForm.severity" placeholder="请选择严重级别">
              <el-option label="提示" value="INFO" />
              <el-option label="警告" value="WARNING" />
              <el-option label="高风险" value="HIGH" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源模块" prop="sourceModule">
            <el-select v-model="createForm.sourceModule" placeholder="请选择来源模块">
              <el-option
                v-for="item in sourceModuleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="来源业务ID">
            <el-input v-model="createForm.sourceId" placeholder="可选，例如审核记录 ID" />
          </el-form-item>
          <el-form-item label="关联职位" class="create-form-item--span-2">
            <el-select
              v-model="createForm.relatedJobId"
              filterable
              remote
              clearable
              reserve-keyword
              :remote-method="loadRelatedJobOptions"
              :loading="relatedJobLoading"
              :placeholder="relatedJobSearchPlaceholder"
              class="create-remote-select"
              popper-class="governance-remote-select-popper"
              @change="handleRelatedJobChange"
            >
              <el-option
                v-for="item in relatedJobOptions"
                :key="item.id"
                :label="buildRelatedJobOptionView(item).selectionLabel"
                :value="item.id"
              >
                <div class="picker-option">
                  <div class="picker-option__title">{{ buildRelatedJobOptionView(item).title }}</div>
                  <div class="picker-option__meta">
                    {{ buildRelatedJobOptionView(item).meta }}
                  </div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="关联商家" class="create-form-item--span-2">
            <el-select
              v-model="createForm.relatedMerchantId"
              filterable
              remote
              clearable
              reserve-keyword
              :remote-method="loadRelatedMerchantOptions"
              :loading="relatedMerchantLoading"
              :placeholder="relatedMerchantSearchPlaceholder"
              class="create-remote-select"
              popper-class="governance-remote-select-popper"
              @change="handleRelatedMerchantChange"
            >
              <el-option
                v-for="item in relatedMerchantOptions"
                :key="item.id"
                :label="buildRelatedMerchantOptionView(item).selectionLabel"
                :value="item.id"
              >
                <div class="picker-option">
                  <div class="picker-option__title">{{ buildRelatedMerchantOptionView(item).title }}</div>
                  <div class="picker-option__meta">
                    {{ buildRelatedMerchantOptionView(item).meta }}
                  </div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="截止时间">
              <el-date-picker
              v-model="createForm.dueTime"
              type="datetime"
              placeholder="请选择截止时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="create-date-picker"
            />
          </el-form-item>
          <el-form-item label="确认已读">
            <el-switch v-model="createForm.needAck" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="要求反馈">
            <el-switch v-model="createForm.needReply" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </div>

        <div class="relation-summary-grid">
          <div
            v-for="item in relationSummaryCards"
            :key="item.key"
            class="relation-summary-card"
            :class="{ 'relation-summary-card--empty': !item.bound }"
          >
            <div class="relation-summary-card__label">{{ item.label }}</div>
            <div class="relation-summary-card__value">{{ item.value }}</div>
            <div class="relation-summary-card__meta">{{ item.meta }}</div>
          </div>
        </div>

        <div class="readonly-check-panel">
          <div class="readonly-check-panel__head">
            <div>
              <div class="readonly-check-panel__title">对象一致性检查</div>
              <div class="readonly-check-panel__desc">这部分会先展示对象对齐情况；如果仍存在高风险错配，点击“创建通知”时会进入二次确认，避免误发治理事项。</div>
            </div>
            <div class="readonly-check-panel__stats">
              <span class="readonly-check-panel__stat readonly-check-panel__stat--danger">高风险 {{ createReadonlyHintOverview.stats.danger }}</span>
              <span class="readonly-check-panel__stat readonly-check-panel__stat--warning">待确认 {{ createReadonlyHintOverview.stats.warning }}</span>
              <span class="readonly-check-panel__stat readonly-check-panel__stat--success">已对齐 {{ createReadonlyHintOverview.stats.success }}</span>
            </div>
          </div>
          <div v-if="createReadonlyHintOverview.dangerHints.length" class="readonly-check-panel__alert">
            当前检测到 {{ createReadonlyHintOverview.dangerHints.length }} 项高风险错配，提交前会要求再次确认。
          </div>
          <div class="readonly-check-grid">
            <div
              v-for="item in createReadonlyHints"
              :key="item.key"
              class="readonly-check-item"
              :class="`readonly-check-item--${item.level}`"
            >
              <div class="readonly-check-item__head">
                <span class="readonly-check-item__title">{{ item.title }}</span>
                <el-tag :type="readonlyHintTag(item.level)" effect="light" size="small">
                  {{ readonlyHintText(item.level) }}
                </el-tag>
              </div>
              <div class="readonly-check-item__desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>

        <el-form-item label="通知标题" prop="title">
          <el-input v-model="createForm.title" maxlength="120" show-word-limit placeholder="请输入通知标题" />
        </el-form-item>

        <el-form-item label="通知摘要">
          <el-input v-model="createForm.summary" maxlength="255" show-word-limit placeholder="建议用一句话概括本次处理要求" />
        </el-form-item>

        <el-form-item label="详细说明" prop="detail">
          <el-input
            v-model="createForm.detail"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="请写明触发原因、问题点和背景说明"
          />
        </el-form-item>

        <el-form-item label="平台要求">
          <el-input
            v-model="createForm.requiredAction"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请写明用户下一步要完成的动作"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateNotice">创建通知</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：承载管理员治理通知列表、筛选、创建、远程搜索选人选职位选商家、详情查看与复核动作，并为封禁申诉待复核事项提供高优先过滤入口与高风险提交前二次确认。
2. 对外入口：fetchNotices、applyFocusPreset、openDetail、openCreateDialog、submitCreateNotice、submitReview、loadTargetUserOptions、loadRelatedJobOptions。
3. 关键结构：状态字典、focusPresetBaseOptions、query、pagination、currentNotice、latestAppealAction、reviewForm、createForm、selectedRelationContext、relationSummaryCards、createReadonlyHints、createReadonlyHintOverview、selectedTargetUserLabel、selectedRelatedMerchant、selectedRelatedJob。
4. 阅读建议：先看字典映射与高优先入口，再看创建弹窗里目标用户选择器、选中对象上下文、对象一致性检查概览与提交前二次确认，最后看详情抽屉里的申诉说明与复核逻辑。
*/
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdminGovernanceNotice,
  getAdminJobs,
  getAdminGovernanceNoticeDetail,
  getAdminGovernanceNotices,
  getAdminMerchants,
  getAdminUsers,
  reviewAdminGovernanceNotice
} from '@/api/admin'

const terminalStatuses = ['FINISHED', 'CLOSED', 'EXPIRED']
const noticeTypeTextMap = {
  JOB_RECTIFY: '职位整改',
  MERCHANT_RECTIFY: '商家整改',
  REPORT_RESULT: '举报结果',
  USER_WARNING: '用户警告',
  BAN_NOTICE: '封禁通知'
}
const noticeTypeTagMap = {
  JOB_RECTIFY: 'warning',
  MERCHANT_RECTIFY: 'warning',
  REPORT_RESULT: 'info',
  USER_WARNING: 'danger',
  BAN_NOTICE: 'danger'
}
const targetRoleTextMap = {
  MERCHANT: '商家',
  APPLICANT: '求职者'
}
const severityTextMap = {
  INFO: '提示',
  WARNING: '警告',
  HIGH: '高风险'
}
const severityTagMap = {
  INFO: 'info',
  WARNING: 'warning',
  HIGH: 'danger'
}
const sourceModuleTextMap = {
  JOB_AUDIT: '职位审核',
  MERCHANT_AUDIT: '商家审核',
  REPORT: '举报处理',
  RISK_CONTROL: '风险控制'
}
const statusTextMap = {
  PENDING_READ: '待查看',
  PENDING_ACTION: '待处理',
  PENDING_REVIEW: '待复核',
  FINISHED: '已完成',
  REJECTED: '已驳回',
  EXPIRED: '已失效',
  CLOSED: '已关闭'
}
const statusTagMap = {
  PENDING_READ: 'info',
  PENDING_ACTION: 'warning',
  PENDING_REVIEW: 'primary',
  FINISHED: 'success',
  REJECTED: 'danger',
  EXPIRED: 'danger',
  CLOSED: 'info'
}
const statusClassMap = {
  PENDING_READ: 'status-pending-read',
  PENDING_ACTION: 'status-pending-action',
  PENDING_REVIEW: 'status-pending-review',
  FINISHED: 'status-finished',
  REJECTED: 'status-rejected',
  EXPIRED: 'status-expired',
  CLOSED: 'status-closed'
}
const actorRoleTextMap = {
  ADMIN: '管理员',
  MERCHANT: '商家',
  APPLICANT: '求职者'
}
const actionTypeTextMap = {
  READ: '已读',
  SUBMIT_FIX: '提交整改',
  REPLY: '补充说明',
  APPEAL: '提交申诉',
  APPROVE: '复核通过',
  REJECT: '复核驳回',
  CLOSE: '关闭事项'
}
const reviewStatusMetaMap = {
  APPROVE: {
    title: '通过复核',
    hint: '通过后，当前治理事项会进入“已完成”，建议写明通过依据。',
    placeholder: '请输入通过复核的说明，例如：已按要求补全职位描述并通过复审',
    buttonText: '确认通过',
    buttonType: 'success',
    confirmType: 'success'
  },
  REJECT: {
    title: '驳回复核',
    hint: '驳回后，事项会退回到“已驳回”，便于用户继续补充整改说明。',
    placeholder: '请输入驳回复核的原因，便于商家继续修改',
    buttonText: '确认驳回',
    buttonType: 'danger',
    confirmType: 'warning'
  },
  CLOSE: {
    title: '关闭治理事项',
    hint: '关闭后，事项将不再继续流转，适用于误建单或无需继续跟进的情况。',
    placeholder: '请输入关闭原因，例如：重复事项或已线下处理',
    buttonText: '确认关闭',
    buttonType: 'warning',
    confirmType: 'info'
  }
}
const templateHintMap = {
  JOB_RECTIFY: '适用于职位审核驳回后的手动补单场景，建议补齐目标商家、职位 ID 和整改要求。',
  MERCHANT_RECTIFY: '适用于企业资料或资质整改，建议补齐目标商家与关联商家 ID。',
  USER_WARNING: '适用于账号违规提醒，建议写清触发原因和后续约束。',
  REPORT_RESULT: '适用于处理举报后的结论同步，可按实际情况调整目标角色。',
  BAN_NOTICE: '适用于限发、限制登录或封禁说明，建议写清范围、时长和恢复条件。'
}
const DEFAULT_TEMPLATE_HINT = '请先选择合适的治理模板，再补充必要字段。'
const TARGET_USER_PLACEHOLDER_MAP = {
  MERCHANT: '搜索商家账号 / 昵称 / 手机 / 邮箱',
  APPLICANT: '搜索求职者账号 / 昵称 / 手机 / 邮箱'
}
const DEFAULT_TARGET_USER_PLACEHOLDER = '搜索目标用户账号 / 昵称 / 手机 / 邮箱'
const RELATED_JOB_PLACEHOLDER_MAP = {
  JOB_RECTIFY: '搜索待整改职位标题或企业名'
}
const DEFAULT_RELATED_JOB_PLACEHOLDER = '可按职位标题或企业名搜索关联职位'
const RELATED_MERCHANT_PLACEHOLDER_MAP = {
  MERCHANT_RECTIFY: '搜索企业名称、联系人或联系电话'
}
const DEFAULT_RELATED_MERCHANT_PLACEHOLDER = '可按企业名称、联系人或联系电话搜索关联商家'
const TEMPLATE_ROLE_EXPECTATION_MAP = {
  JOB_RECTIFY: 'MERCHANT',
  MERCHANT_RECTIFY: 'MERCHANT'
}
const TEMPLATE_SOURCE_EXPECTATION_MAP = {
  JOB_RECTIFY: 'JOB_AUDIT',
  MERCHANT_RECTIFY: 'MERCHANT_AUDIT',
  REPORT_RESULT: 'REPORT',
  USER_WARNING: 'RISK_CONTROL',
  BAN_NOTICE: 'RISK_CONTROL'
}
const readonlyHintTextMap = {
  success: '已对齐',
  warning: '待确认',
  danger: '高风险',
  info: '建议'
}
const readonlyHintTagMap = {
  success: 'success',
  warning: 'warning',
  danger: 'danger',
  info: 'info'
}

const noticeTypeOptions = [
  { label: '职位整改', value: 'JOB_RECTIFY' },
  { label: '商家整改', value: 'MERCHANT_RECTIFY' },
  { label: '举报结果', value: 'REPORT_RESULT' },
  { label: '用户警告', value: 'USER_WARNING' },
  { label: '封禁通知', value: 'BAN_NOTICE' }
]

const statusOptions = [
  { label: '待查看', value: 'PENDING_READ' },
  { label: '待处理', value: 'PENDING_ACTION' },
  { label: '待复核', value: 'PENDING_REVIEW' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已失效', value: 'EXPIRED' },
  { label: '已关闭', value: 'CLOSED' }
]

const sourceModuleOptions = [
  { label: '职位审核', value: 'JOB_AUDIT' },
  { label: '商家审核', value: 'MERCHANT_AUDIT' },
  { label: '举报处理', value: 'REPORT' },
  { label: '风险控制', value: 'RISK_CONTROL' }
]

const focusPresetBaseOptions = [
  {
    key: 'BAN_APPEAL',
    eyebrow: 'P0 高优先',
    title: '封禁申诉待复核',
    desc: '集中核查封禁申诉，优先处理可能存在误封或恢复条件已满足的账号。',
    helper: '一键切到风控申诉专门视图',
    filters: {
      noticeType: 'BAN_NOTICE',
      status: 'PENDING_REVIEW',
      sourceModule: 'RISK_CONTROL'
    }
  },
  {
    key: 'JOB_REVIEW',
    eyebrow: 'P1 常规复核',
    title: '职位整改待复核',
    desc: '查看商家已经提交的职位整改说明，尽快给出通过或驳回结论。',
    helper: '直接切到职位整改复核列表',
    filters: {
      noticeType: 'JOB_RECTIFY',
      status: 'PENDING_REVIEW',
      sourceModule: 'JOB_AUDIT'
    }
  },
  {
    key: 'OVERDUE',
    eyebrow: '时限关注',
    title: '已逾期治理事项',
    desc: '补处理积压事项，避免长期挂起影响平台治理效率与用户感知。',
    helper: '快速查看所有已逾期事项',
    filters: {
      overdueOnly: true
    }
  }
]

const createTemplateOptions = [
  {
    value: 'JOB_RECTIFY',
    label: '职位整改',
    desc: '商家整改职位信息并提交复核'
  },
  {
    value: 'MERCHANT_RECTIFY',
    label: '商家整改',
    desc: '要求补充企业资料或资质'
  },
  {
    value: 'USER_WARNING',
    label: '用户警告',
    desc: '对违规求职者发出正式警告'
  },
  {
    value: 'REPORT_RESULT',
    label: '举报结果',
    desc: '向用户同步举报处理结论'
  },
  {
    value: 'BAN_NOTICE',
    label: '封禁说明',
    desc: '告知限制范围、原因与恢复条件'
  }
]
const getCreateTemplateLabel = (templateKey) => {
  return createTemplateOptions.find((item) => item.value === templateKey)?.label || '当前模板'
}

const templatePresetMap = {
  JOB_RECTIFY: {
    targetRole: 'MERCHANT',
    noticeType: 'JOB_RECTIFY',
    severity: 'WARNING',
    sourceModule: 'JOB_AUDIT',
    title: '职位需修改后重新提交',
    summary: '请根据平台审核意见修改职位信息，并在完成后提交复核。',
    detail: '该职位存在待修正内容，请结合审核结论补全或修正职位信息后重新提交。',
    requiredAction: '请修改职位信息，并在完成后重新提交复核。',
    dueDays: 3,
    needAck: 1,
    needReply: 1
  },
  MERCHANT_RECTIFY: {
    targetRole: 'MERCHANT',
    noticeType: 'MERCHANT_RECTIFY',
    severity: 'WARNING',
    sourceModule: 'MERCHANT_AUDIT',
    title: '企业资料需补充后重新提交',
    summary: '请补充或修正企业资料，并在完成后提交复核。',
    detail: '当前企业资料存在缺失或不符合要求的内容，请根据平台说明补齐材料后重新提交。',
    requiredAction: '请补充企业资料并重新提交复核。',
    dueDays: 3,
    needAck: 1,
    needReply: 1
  },
  USER_WARNING: {
    targetRole: 'APPLICANT',
    noticeType: 'USER_WARNING',
    severity: 'WARNING',
    sourceModule: 'RISK_CONTROL',
    title: '平台违规提醒',
    summary: '平台已识别到存在风险行为，请及时自查并停止相关行为。',
    detail: '平台检测到账号存在需关注的风险行为，请认真阅读说明并按要求完成整改或说明。',
    requiredAction: '请停止相关行为，如有异议可提交补充说明或申诉。',
    dueDays: 2,
    needAck: 1,
    needReply: 1
  },
  REPORT_RESULT: {
    targetRole: 'APPLICANT',
    noticeType: 'REPORT_RESULT',
    severity: 'INFO',
    sourceModule: 'REPORT',
    title: '举报处理结果通知',
    summary: '平台已完成举报处理，请查看本次处理结论与后续要求。',
    detail: '该事项用于同步举报处理结论，可补充处理依据、结论和后续要求。',
    requiredAction: '请确认处理结果，如有异议可联系平台说明。',
    dueDays: 2,
    needAck: 1,
    needReply: 0
  },
  BAN_NOTICE: {
    targetRole: 'APPLICANT',
    noticeType: 'BAN_NOTICE',
    severity: 'HIGH',
    sourceModule: 'RISK_CONTROL',
    title: '平台限制说明',
    summary: '平台已对账号执行限制，请查看限制范围、原因与恢复条件。',
    detail: '该事项用于同步限制或封禁结论，请补充限制范围、持续时间、原因及恢复条件。',
    requiredAction: '请阅读限制说明；如有异议，可提交申诉材料。',
    dueDays: 7,
    needAck: 1,
    needReply: 1
  }
}

const buildDueTime = (days = 3) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const createEmptyQuery = () => ({
  targetRole: '',
  noticeType: '',
  status: '',
  sourceModule: '',
  overdueOnly: false
})

const buildFocusQuery = (filters = {}) => ({
  ...createEmptyQuery(),
  ...filters
})

const createCreateFormByTemplate = (templateKey = 'JOB_RECTIFY') => {
  const preset = templatePresetMap[templateKey] || templatePresetMap.JOB_RECTIFY
  return {
    templateKey,
    targetRole: preset.targetRole,
    targetUserId: '',
    noticeType: preset.noticeType,
    severity: preset.severity,
    sourceModule: preset.sourceModule,
    sourceId: '',
    relatedJobId: '',
    relatedMerchantId: '',
    title: preset.title,
    summary: preset.summary,
    detail: preset.detail,
    requiredAction: preset.requiredAction,
    dueTime: buildDueTime(preset.dueDays),
    needAck: preset.needAck,
    needReply: preset.needReply
  }
}

const query = ref(createEmptyQuery())

const notices = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const currentNotice = ref(null)

const pagination = ref({
  current: 1,
  size: 20,
  total: 0
})

const createDialogVisible = ref(false)
const createFormRef = ref(null)
const createForm = ref(createCreateFormByTemplate())
const targetUserPickerRef = ref(null)
const targetUserPanelVisible = ref(false)
const targetUserKeyword = ref('')
const targetUserLoading = ref(false)
const targetUserOptions = ref([])
const selectedTargetUser = ref(null)
const relatedJobLoading = ref(false)
const relatedJobOptions = ref([])
const selectedRelatedJob = ref(null)
const relatedMerchantLoading = ref(false)
const relatedMerchantOptions = ref([])
const selectedRelatedMerchant = ref(null)
const createRequestTracker = () => ({ seq: 0 })
const targetUserRequestTracker = createRequestTracker()
const relatedJobRequestTracker = createRequestTracker()
const relatedMerchantRequestTracker = createRequestTracker()

const reviewDialogVisible = ref(false)
const reviewForm = ref({
  noticeId: null,
  reviewStatus: '',
  reviewComment: ''
})

const createRules = {
  targetRole: [{ required: true, message: '请选择目标角色', trigger: 'change' }],
  targetUserId: [{ required: true, message: '请选择目标用户', trigger: 'change' }],
  noticeType: [{ required: true, message: '请选择通知类型', trigger: 'change' }],
  severity: [{ required: true, message: '请选择严重级别', trigger: 'change' }],
  sourceModule: [{ required: true, message: '请选择来源模块', trigger: 'change' }],
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细说明', trigger: 'blur' }]
}

const noticeTypeText = (value) => {
  return noticeTypeTextMap[value] || '未知类型'
}

const noticeTypeTag = (value) => {
  return noticeTypeTagMap[value] || 'info'
}

const targetRoleText = (value) => {
  return targetRoleTextMap[value] || '未知角色'
}

const severityText = (value) => {
  return severityTextMap[value] || '未知级别'
}

const severityTag = (value) => {
  return severityTagMap[value] || 'info'
}

const sourceModuleText = (value) => {
  return sourceModuleTextMap[value] || '未知来源'
}

const statusText = (value) => {
  return statusTextMap[value] || '未知状态'
}

const statusTag = (value) => {
  return statusTagMap[value] || 'info'
}

const statusClass = (value) => {
  return statusClassMap[value] || 'status-default'
}

const actorRoleText = (value) => {
  return actorRoleTextMap[value] || '系统'
}

const actionTypeText = (value) => {
  return actionTypeTextMap[value] || '未知动作'
}

const isBanAppealPending = (notice) => {
  return Boolean(notice && notice.noticeType === 'BAN_NOTICE' && notice.status === 'PENDING_REVIEW')
}

const formatText = (value) => value || '—'

const getRowClassName = ({ row }) => {
  return [row?.overdue ? 'governance-row-overdue' : '', isBanAppealPending(row) ? 'governance-row-ban-appeal' : '']
    .filter(Boolean)
    .join(' ')
}

const isSameQueryState = (left, right) => {
  const leftQuery = left || createEmptyQuery()
  const rightQuery = right || createEmptyQuery()
  return (
    String(leftQuery.targetRole || '') === String(rightQuery.targetRole || '') &&
    String(leftQuery.noticeType || '') === String(rightQuery.noticeType || '') &&
    String(leftQuery.status || '') === String(rightQuery.status || '') &&
    String(leftQuery.sourceModule || '') === String(rightQuery.sourceModule || '') &&
    Boolean(leftQuery.overdueOnly) === Boolean(rightQuery.overdueOnly)
  )
}

const activeFocusKey = computed(() => {
  const matched = focusPresetBaseOptions.find((item) => isSameQueryState(query.value, buildFocusQuery(item.filters)))
  return matched?.key || ''
})

const focusPresetOptions = computed(() => {
  return focusPresetBaseOptions.map((item) => ({
    ...item,
    meta: activeFocusKey.value === item.key ? `当前命中 ${pagination.value.total} 条事项` : item.helper
  }))
})

const activeFocusPreset = computed(() => {
  return focusPresetOptions.value.find((item) => item.key === activeFocusKey.value) || null
})

const latestAppealAction = computed(() => {
  if (currentNotice.value?.noticeType !== 'BAN_NOTICE') {
    return null
  }
  const actions = currentNotice.value?.actions || []
  return actions
    .filter((item) => item.actionType === 'APPEAL')
    .sort((left, right) => new Date(right.createTime || 0).getTime() - new Date(left.createTime || 0).getTime())[0] || null
})

const currentReviewMeta = computed(() => {
  return reviewStatusMetaMap[reviewForm.value.reviewStatus] || {
    title: '处理治理事项',
    hint: '请填写本次处理说明。',
    placeholder: '请输入处理说明',
    buttonText: '确认',
    buttonType: 'primary',
    confirmType: 'info'
  }
})

const reviewDialogTitle = computed(() => currentReviewMeta.value.title)
const reviewDialogHint = computed(() => currentReviewMeta.value.hint)
const reviewPlaceholder = computed(() => currentReviewMeta.value.placeholder)
const reviewButtonText = computed(() => currentReviewMeta.value.buttonText)
const reviewButtonType = computed(() => currentReviewMeta.value.buttonType)

const currentTemplateMeta = computed(() => {
  const templateKey = createForm.value.templateKey
  return {
    key: templateKey,
    label: getCreateTemplateLabel(templateKey),
    hint: templateHintMap[templateKey] || DEFAULT_TEMPLATE_HINT,
    expectedRole: TEMPLATE_ROLE_EXPECTATION_MAP[templateKey],
    expectedSourceModule: TEMPLATE_SOURCE_EXPECTATION_MAP[templateKey]
  }
})

const resolveMappedValue = (valueMap, key, fallback) => {
  return valueMap[key] || fallback
}
const targetUserSearchPlaceholder = computed(() => {
  return resolveMappedValue(TARGET_USER_PLACEHOLDER_MAP, createForm.value.targetRole, DEFAULT_TARGET_USER_PLACEHOLDER)
})

const relatedJobSearchPlaceholder = computed(() => {
  return resolveMappedValue(RELATED_JOB_PLACEHOLDER_MAP, createForm.value.noticeType, DEFAULT_RELATED_JOB_PLACEHOLDER)
})

const relatedMerchantSearchPlaceholder = computed(() => {
  return resolveMappedValue(RELATED_MERCHANT_PLACEHOLDER_MAP, createForm.value.noticeType, DEFAULT_RELATED_MERCHANT_PLACEHOLDER)
})

const readonlyHintText = (level) => {
  return readonlyHintTextMap[level] || '提示'
}

const readonlyHintTag = (level) => {
  return readonlyHintTagMap[level] || 'info'
}

const normalizeCompareText = (value) => {
  return String(value || '').trim().toLowerCase()
}
const getTargetUserDisplayName = (user) => {
  return user?.nickname || user?.username || (user ? `用户#${user.id}` : '')
}
const getTargetUserContactText = (user) => {
  return user?.phone || user?.email || user?.username || '无联系方式'
}
const getRelatedJobDisplayName = (job) => {
  return job?.title || (job ? `职位#${job.id}` : '')
}
const getRelatedJobCompanyText = (job) => {
  return job?.companyName || '未绑定企业'
}
const getRelatedJobSummaryMeta = (job) => {
  return job ? `ID ${job.id} · ${getRelatedJobCompanyText(job)} · ${job.location || '地点待补充'}` : ''
}
const getRelatedMerchantDisplayName = (merchant) => {
  return merchant?.companyName || (merchant ? `商家#${merchant.id}` : '')
}
const getRelatedMerchantContactText = (merchant) => {
  return merchant?.contact || '未填联系人'
}
const getRelatedMerchantPhoneText = (merchant) => {
  return merchant?.phone || '无联系电话'
}
const buildTargetUserOptionView = (user) => {
  if (!user) {
    return { title: '', meta: '', selectionLabel: '' }
  }
  const title = getTargetUserDisplayName(user)
  const contactText = getTargetUserContactText(user)
  return {
    title,
    meta: `#${user.id} · ${targetRoleText(user.role)} · ${contactText}`,
    selectionLabel: `${title} · ${contactText}`
  }
}
const buildRelatedJobOptionView = (job) => {
  if (!job) {
    return { title: '', meta: '', selectionLabel: '' }
  }
  const title = getRelatedJobDisplayName(job)
  const companyText = getRelatedJobCompanyText(job)
  return {
    title,
    meta: `#${job.id} · ${companyText} · ${job.location || '地点待补充'}`,
    selectionLabel: `${title} · ${companyText}`
  }
}
const buildRelatedMerchantOptionView = (merchant) => {
  if (!merchant) {
    return { title: '', meta: '', selectionLabel: '' }
  }
  const title = getRelatedMerchantDisplayName(merchant)
  const contactText = getRelatedMerchantContactText(merchant)
  const phoneText = getRelatedMerchantPhoneText(merchant)
  return {
    title,
    meta: `档案ID ${merchant.id} · ${contactText} · ${phoneText}`,
    selectionLabel: `${title} · ${merchant.contact || phoneText}`
  }
}
const selectedRelationContext = computed(() => {
  const targetUser = selectedTargetUser.value
  const relatedMerchant = selectedRelatedMerchant.value
  const relatedJob = selectedRelatedJob.value
  return {
    targetUser,
    relatedMerchant,
    relatedJob,
    targetUserName: getTargetUserDisplayName(targetUser) || '当前账号',
    merchantName: getRelatedMerchantDisplayName(relatedMerchant) || (relatedJob ? getRelatedJobCompanyText(relatedJob) : ''),
    jobName: getRelatedJobDisplayName(relatedJob) || '',
    relatedJobCompany: normalizeCompareText(relatedJob?.companyName),
    relatedMerchantCompany: normalizeCompareText(relatedMerchant?.companyName),
    relatedMerchantPhone: normalizeCompareText(relatedMerchant?.phone),
    targetUserPhone: normalizeCompareText(targetUser?.phone)
  }
})
const canApplyObjectPreset = computed(() => {
  const { targetUser, relatedMerchant, relatedJob } = selectedRelationContext.value
  return Boolean(targetUser || relatedMerchant || relatedJob)
})

const buildTargetUserSummaryCard = (context, mode = 'form') => {
  const { targetUser: user } = context
  if (user) {
    return {
      key: 'target-user',
      label: '目标用户',
      bound: true,
      value: getTargetUserDisplayName(user),
      meta: buildTargetUserOptionView(user).meta.replace('#', 'ID ')
    }
  }
  return {
    key: 'target-user',
    label: '目标用户',
    bound: false,
    value: mode === 'confirm' ? '未绑定目标用户' : '尚未选择目标用户',
    meta: mode === 'confirm' ? '当前通知还没有明确接收账号。' : '可按账号、昵称、手机号或邮箱搜索。'
  }
}
const buildRelatedMerchantSummaryCard = (context, mode = 'form') => {
  const { relatedMerchant: merchant } = context
  if (merchant) {
    return {
      key: 'related-merchant',
      label: '关联商家',
      bound: true,
      value: getRelatedMerchantDisplayName(merchant),
      meta: buildRelatedMerchantOptionView(merchant).meta
    }
  }
  return {
    key: 'related-merchant',
    label: '关联商家',
    bound: false,
    value: mode === 'confirm' ? '未绑定关联商家' : '当前未绑定关联商家',
    meta: mode === 'confirm' ? '当前没有绑定企业档案。' : '商家整改、企业资料整改等场景建议绑定具体企业档案。'
  }
}
const buildRelatedJobSummaryCard = (context, mode = 'form') => {
  const { relatedJob: job } = context
  if (job) {
    return {
      key: 'related-job',
      label: '关联职位',
      bound: true,
      value: getRelatedJobDisplayName(job),
      meta: getRelatedJobSummaryMeta(job)
    }
  }
  return {
    key: 'related-job',
    label: '关联职位',
    bound: false,
    value: mode === 'confirm' ? '未绑定关联职位' : '当前未绑定关联职位',
    meta: mode === 'confirm' ? '当前没有绑定具体职位。' : '职位整改、举报结果等场景建议绑定具体职位。'
  }
}
const buildRelationSummaryCards = (mode = 'form') => {
  const context = selectedRelationContext.value
  return [
    buildTargetUserSummaryCard(context, mode),
    buildRelatedMerchantSummaryCard(context, mode),
    buildRelatedJobSummaryCard(context, mode)
  ]
}
const relationSummaryCards = computed(() => buildRelationSummaryCards())

const createReadonlyHints = computed(() => {
  const hints = []
  const templateKey = currentTemplateMeta.value.key
  const templateLabel = currentTemplateMeta.value.label
  const targetRole = createForm.value.targetRole
  const sourceModule = createForm.value.sourceModule
  const expectedRole = currentTemplateMeta.value.expectedRole
  const expectedSourceModule = currentTemplateMeta.value.expectedSourceModule
  const {
    targetUser,
    relatedMerchant,
    relatedJob,
    relatedJobCompany,
    relatedMerchantCompany,
    relatedMerchantPhone,
    targetUserPhone
  } = selectedRelationContext.value

  if (targetUser) {
    if (targetUser.role !== targetRole) {
      hints.push({
        key: 'target-role-mismatch',
        level: 'danger',
        title: '目标角色与目标用户不一致',
        desc: `当前表单角色是“${targetRoleText(targetRole)}”，但所选账号实际角色是“${targetRoleText(targetUser.role)}”，提交前建议重新确认目标账号。`
      })
    } else {
      hints.push({
        key: 'target-role-match',
        level: 'success',
        title: '目标账号角色已对齐',
        desc: `当前目标账号与表单角色均为“${targetRoleText(targetRole)}”，这一层关系已对齐。`
      })
    }
  } else {
    hints.push({
      key: 'target-user-missing',
      level: 'warning',
      title: '尚未绑定目标账号',
      desc: '当前还没有明确这条治理通知要发给哪个账号。建议先完成目标用户绑定，再提交治理事项。'
    })
  }

  if (expectedRole && targetRole !== expectedRole) {
    hints.push({
      key: 'template-role-risk',
      level: 'danger',
      title: '模板与目标角色存在错配风险',
      desc: `${templateLabel}通常应发送给“${targetRoleText(expectedRole)}”账号，当前角色配置可能导致通知发错对象。`
    })
  }

  if (expectedSourceModule) {
    if (sourceModule === expectedSourceModule) {
      hints.push({
        key: 'source-match',
        level: 'success',
        title: '来源模块与模板一致',
        desc: `当前模板与来源模块均指向“${sourceModuleText(sourceModule)}”，治理记录归档路径是清晰的。`
      })
    } else {
      hints.push({
        key: 'source-mismatch',
        level: 'warning',
        title: '来源模块与模板不一致',
        desc: `${templateLabel}通常建议使用“${sourceModuleText(expectedSourceModule)}”，若继续使用当前来源模块，后续检索和回查会更难理解。`
      })
    }
  }

  if (templateKey === 'JOB_RECTIFY') {
    if (!relatedJob) {
      hints.push({
        key: 'job-missing',
        level: 'danger',
        title: '职位整改未绑定具体职位',
        desc: '职位整改通知通常应绑定具体职位，否则商家无法从通知直接定位到待修改职位。'
      })
    } else {
      hints.push({
        key: 'job-bound',
        level: 'success',
        title: '职位整改已绑定具体职位',
        desc: `当前已绑定职位“${getRelatedJobDisplayName(relatedJob)}”，后续可直接从通知跳转到对应职位处理。`
      })
    }
  }

  if (templateKey === 'MERCHANT_RECTIFY') {
    if (!relatedMerchant) {
      hints.push({
        key: 'merchant-missing',
        level: 'danger',
        title: '商家整改未绑定企业档案',
        desc: '商家整改通知建议绑定具体企业档案，否则后续很难判断资料整改到底对应哪一家企业。'
      })
    } else {
      hints.push({
        key: 'merchant-bound',
        level: 'success',
        title: '商家整改已绑定企业档案',
        desc: `当前已绑定企业“${getRelatedMerchantDisplayName(relatedMerchant)}”，资料整改链路可以落到具体企业对象。`
      })
    }
  }

  if (relatedJob && relatedMerchant) {
    if (relatedJobCompany && relatedMerchantCompany && relatedJobCompany !== relatedMerchantCompany) {
      hints.push({
        key: 'job-merchant-mismatch',
        level: 'danger',
        title: '关联职位与关联商家疑似不属于同一企业',
        desc: `职位当前显示企业为“${getRelatedJobCompanyText(relatedJob)}”，但关联商家为“${getRelatedMerchantDisplayName(relatedMerchant)}”，建议重新核对是否误绑。`
      })
    } else {
      hints.push({
        key: 'job-merchant-match',
        level: 'success',
        title: '关联职位与关联商家名称已对齐',
        desc: '职位和企业档案的主体名称一致，后续治理对象关系更清晰。'
      })
    }
  } else if (relatedJob && !relatedMerchant) {
    hints.push({
      key: 'job-without-merchant',
      level: 'warning',
      title: '已绑定职位但未绑定企业档案',
      desc: '如果后续需要核对企业资料或从商家维度回查，建议再补充关联商家档案。'
    })
  }

  if (relatedMerchant && targetRole === 'MERCHANT') {
    if (!targetUser) {
      hints.push({
        key: 'merchant-target-missing',
        level: 'warning',
        title: '已绑定商家档案但未绑定商家账号',
        desc: '当前已有企业档案，但还没有明确通知要发给哪个商家账号。建议继续搜索并绑定登录账号。'
      })
    } else if (relatedMerchantPhone && targetUserPhone) {
      if (relatedMerchantPhone === targetUserPhone) {
        hints.push({
          key: 'merchant-phone-match',
          level: 'success',
          title: '商家档案与目标账号手机号一致',
          desc: '企业档案联系电话与目标账号手机号一致，这条关系较可信。'
        })
      } else {
        hints.push({
          key: 'merchant-phone-mismatch',
          level: 'warning',
          title: '商家档案与目标账号手机号不一致',
          desc: `企业档案联系电话是“${relatedMerchant.phone}”，目标账号手机号是“${targetUser.phone}”，建议人工确认这是否是同一家企业的实际运营账号。`
        })
      }
    } else {
      hints.push({
        key: 'merchant-phone-missing',
        level: 'info',
        title: '缺少手机号对照信息',
        desc: '当前无法通过手机号校验商家档案和目标账号的对应关系，建议结合企业名称、联系人或后台资料人工确认。'
      })
    }
  }

  return hints
})
const buildReadonlyHintOverview = (hints) => {
  const grouped = {
    success: [],
    warning: [],
    danger: [],
    info: []
  }
  hints.forEach((item) => {
    if (grouped[item.level]) {
      grouped[item.level].push(item)
    }
  })
  return {
    grouped,
    stats: {
      success: grouped.success.length,
      warning: grouped.warning.length,
      danger: grouped.danger.length,
      info: grouped.info.length
    },
    dangerHints: grouped.danger
  }
}
const createReadonlyHintOverview = computed(() => buildReadonlyHintOverview(createReadonlyHints.value))

const buildCreateRiskConfirmMessage = () => {
  const confirmRelationSummaryCards = buildRelationSummaryCards('confirm')
  return h('div', { class: 'create-risk-confirm' }, [
    h('div', { class: 'create-risk-confirm__lead' }, '当前创建单仍存在高风险对象错配。若继续提交，系统会直接向目标账号下发治理通知。'),
    h('div', { class: 'create-risk-confirm__section' }, [
      h('div', { class: 'create-risk-confirm__section-title' }, `高风险项（${createReadonlyHintOverview.value.dangerHints.length}）`),
      h('div', { class: 'create-risk-confirm__list' }, createReadonlyHintOverview.value.dangerHints.map((item) => {
        return h('div', { key: item.key, class: 'create-risk-confirm__item' }, [
          h('div', { class: 'create-risk-confirm__item-title' }, item.title),
          h('div', { class: 'create-risk-confirm__item-desc' }, item.desc)
        ])
      }))
    ]),
    h('div', { class: 'create-risk-confirm__section' }, [
      h('div', { class: 'create-risk-confirm__section-title' }, '当前绑定对象'),
      h('div', { class: 'create-risk-confirm__summary-grid' }, confirmRelationSummaryCards.map((item) => {
        return h('div', { key: item.key, class: 'create-risk-confirm__summary-card' }, [
          h('div', { class: 'create-risk-confirm__summary-label' }, item.label),
          h('div', { class: 'create-risk-confirm__summary-value' }, item.value),
          h('div', { class: 'create-risk-confirm__summary-meta' }, item.meta)
        ])
      }))
    ])
  ])
}

const confirmCreateRiskBeforeSubmit = async () => {
  if (!createReadonlyHintOverview.value.dangerHints.length) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      buildCreateRiskConfirmMessage(),
      '高风险提交确认',
      {
        confirmButtonText: '仍然创建',
        cancelButtonText: '返回检查',
        type: 'warning',
        customClass: 'governance-create-risk-box',
        closeOnClickModal: false,
        closeOnPressEscape: false,
        autofocus: false
      }
    )
    return true
  } catch (error) {
    return false
  }
}

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const normalizeOptionalText = (value) => {
  const text = String(value || '').trim()
  return text || undefined
}

/**
 * 统一把日期时间转换成后端 LocalDateTime 可直接解析的本地 ISO 文本。
 */
const normalizeOptionalDateTime = (value) => {
  const text = normalizeOptionalText(value)
  if (!text) return undefined
  return text.includes('T') ? text : text.replace(' ', 'T')
}

const normalizeOptionalNumber = (value) => {
  const text = String(value || '').trim()
  if (!text) return undefined
  const numberValue = Number(text)
  return Number.isFinite(numberValue) ? numberValue : undefined
}
const shouldPreserveSourceIdForTemplate = (nextForm) => {
  const currentSourceId = normalizeOptionalText(createForm.value.sourceId)
  if (!currentSourceId) {
    return false
  }
  if (createForm.value.sourceModule !== nextForm.sourceModule) {
    return false
  }
  if (nextForm.sourceModule === 'JOB_AUDIT') {
    return Boolean(createForm.value.relatedJobId)
  }
  if (nextForm.sourceModule === 'MERCHANT_AUDIT') {
    return Boolean(createForm.value.relatedMerchantId)
  }
  return true
}
const syncSelectedOption = (selectedRef, rawId, options) => {
  const optionId = Number(rawId)
  if (!Number.isFinite(optionId) || optionId <= 0) {
    selectedRef.value = null
    return
  }
  const matched = options.find((item) => Number(item.id) === optionId)
  if (matched) {
    selectedRef.value = matched
  }
}
const loadRemoteOptions = async ({ loadingRef, optionsRef, request, params, syncSelected, requestTracker }) => {
  const requestId = requestTracker ? requestTracker.seq + 1 : 0
  if (requestTracker) {
    requestTracker.seq = requestId
  }
  loadingRef.value = true
  try {
    const res = await request(params)
    if (requestTracker && requestId !== requestTracker.seq) {
      return
    }
    if (res.code === 200) {
      optionsRef.value = normalizeList(res.data)
      syncSelected()
      return
    }
    optionsRef.value = []
  } catch (error) {
    if (!requestTracker || requestId === requestTracker.seq) {
      optionsRef.value = []
    }
  } finally {
    if (!requestTracker || requestId === requestTracker.seq) {
      loadingRef.value = false
    }
  }
}

const buildTargetUserLabel = (item) => {
  return buildTargetUserOptionView(item).selectionLabel
}
const selectedTargetUserLabel = computed(() => buildTargetUserLabel(selectedTargetUser.value))
const isUsingSelectedTargetUserLabel = (keyword = targetUserKeyword.value) => {
  return Boolean(selectedTargetUserLabel.value) && keyword === selectedTargetUserLabel.value
}
const syncTargetUserKeyword = () => {
  if (selectedTargetUserLabel.value) {
    targetUserKeyword.value = selectedTargetUserLabel.value
    return
  }
  if (!targetUserPanelVisible.value) {
    targetUserKeyword.value = ''
  }
}
const clearSelectedTargetUser = (shouldClearKeyword = true) => {
  createForm.value.targetUserId = ''
  selectedTargetUser.value = null
  if (shouldClearKeyword) {
    targetUserKeyword.value = ''
  }
}
const setSelectedTargetUserOption = (item) => {
  createForm.value.targetUserId = item?.id || ''
  selectedTargetUser.value = item || null
  targetUserKeyword.value = item ? buildTargetUserLabel(item) : ''
}
const validateCreateTargetUserField = async () => {
  await nextTick()
  createFormRef.value?.validateField('targetUserId').catch(() => {})
}
const showTargetUserPanel = async (keyword = '') => {
  targetUserPanelVisible.value = true
  await loadTargetUserOptions(keyword)
}

const syncSelectedTargetUser = () => {
  syncSelectedOption(selectedTargetUser, createForm.value.targetUserId, targetUserOptions.value)
}

const syncSelectedRelatedJob = () => {
  syncSelectedOption(selectedRelatedJob, createForm.value.relatedJobId, relatedJobOptions.value)
}

const syncSelectedRelatedMerchant = () => {
  syncSelectedOption(selectedRelatedMerchant, createForm.value.relatedMerchantId, relatedMerchantOptions.value)
}

const loadTargetUserOptions = async (keyword = '') => {
  await loadRemoteOptions({
    loadingRef: targetUserLoading,
    optionsRef: targetUserOptions,
    request: getAdminUsers,
    params: {
      current: 1,
      size: 8,
      keyword: normalizeOptionalText(keyword),
      role: createForm.value.targetRole || undefined
    },
    syncSelected: syncSelectedTargetUser,
    requestTracker: targetUserRequestTracker
  })
}

const loadRelatedJobOptions = async (keyword = '') => {
  await loadRemoteOptions({
    loadingRef: relatedJobLoading,
    optionsRef: relatedJobOptions,
    request: getAdminJobs,
    params: {
      current: 1,
      size: 8,
      keyword: normalizeOptionalText(keyword)
    },
    syncSelected: syncSelectedRelatedJob,
    requestTracker: relatedJobRequestTracker
  })
}

const loadRelatedMerchantOptions = async (keyword = '') => {
  await loadRemoteOptions({
    loadingRef: relatedMerchantLoading,
    optionsRef: relatedMerchantOptions,
    request: getAdminMerchants,
    params: {
      current: 1,
      size: 8,
      keyword: normalizeOptionalText(keyword)
    },
    syncSelected: syncSelectedRelatedMerchant,
    requestTracker: relatedMerchantRequestTracker
  })
}
const loadCreateDialogOptions = async () => {
  await Promise.all([loadTargetUserOptions(''), loadRelatedJobOptions(''), loadRelatedMerchantOptions('')])
}

const syncCreateSelections = () => {
  syncSelectedTargetUser()
  syncSelectedRelatedJob()
  syncSelectedRelatedMerchant()
}
const resetCreateDialogSelections = () => {
  targetUserPanelVisible.value = false
  clearSelectedTargetUser()
  targetUserOptions.value = []
  relatedJobOptions.value = []
  relatedMerchantOptions.value = []
  selectedRelatedJob.value = null
  selectedRelatedMerchant.value = null
}
const setCreateSourceSelection = (sourceModule, sourceId = '') => {
  createForm.value.sourceModule = sourceModule
  createForm.value.sourceId = sourceId ? String(sourceId) : ''
}
const tryAutoSelectMerchantTargetUser = async (merchant) => {
  if (!merchant) return
  createForm.value.targetRole = 'MERCHANT'
  const searchKeyword = normalizeOptionalText(merchant.phone || merchant.contact || merchant.companyName)
  await loadTargetUserOptions(searchKeyword || '')
  const exactPhoneMatch = targetUserOptions.value.find((item) => normalizeOptionalText(item.phone) && normalizeOptionalText(item.phone) === normalizeOptionalText(merchant.phone))
  const preferred = exactPhoneMatch || (targetUserOptions.value.length === 1 ? targetUserOptions.value[0] : null)
  if (preferred) {
    setSelectedTargetUserOption(preferred)
  }
}

const syncSourceIdsBySelection = () => {
  if (createForm.value.noticeType === 'JOB_RECTIFY' || createForm.value.sourceModule === 'JOB_AUDIT') {
    if (createForm.value.relatedJobId) {
      setCreateSourceSelection('JOB_AUDIT', createForm.value.relatedJobId)
    } else if (createForm.value.sourceModule === 'JOB_AUDIT') {
      setCreateSourceSelection('JOB_AUDIT')
    }
  }
  if (createForm.value.noticeType === 'MERCHANT_RECTIFY' || createForm.value.sourceModule === 'MERCHANT_AUDIT') {
    if (createForm.value.relatedMerchantId) {
      setCreateSourceSelection('MERCHANT_AUDIT', createForm.value.relatedMerchantId)
    } else if (createForm.value.sourceModule === 'MERCHANT_AUDIT') {
      setCreateSourceSelection('MERCHANT_AUDIT')
    }
  }
}
const buildReportResultSubjectName = ({ jobName, merchantName, targetUserName }) => {
  if (jobName) {
    return `职位《${jobName}》`
  }
  if (merchantName) {
    return `企业《${merchantName}》`
  }
  return targetUserName
}

const buildObjectPresetDraft = () => {
  const templateKey = currentTemplateMeta.value.key
  const { targetUserName, merchantName, jobName } = selectedRelationContext.value
  switch (templateKey) {
    case 'JOB_RECTIFY': {
      const title = jobName ? `职位《${jobName}》需修改后重新提交` : '职位需修改后重新提交'
      return {
        title,
        summary: merchantName ? `企业《${merchantName}》的职位信息需要修正，请按平台意见完成整改。` : '请根据平台审核意见修改职位信息，并在完成后提交复核。',
        detail: `${jobName ? `关联职位：${jobName}。` : ''}${merchantName ? `关联企业：${merchantName}。` : ''}请结合审核结论补全或修正职位信息、招聘要求、薪资说明与展示字段，完成后重新提交复核。`,
        requiredAction: '请修改职位信息，并在确认无误后重新提交复核。'
      }
    }
    case 'MERCHANT_RECTIFY': {
      const title = merchantName ? `企业《${merchantName}》资料需补充后重新提交` : '企业资料需补充后重新提交'
      return {
        title,
        summary: merchantName ? `企业《${merchantName}》的资料或资质仍需补充，请按平台要求完成整改。` : '请补充或修正企业资料，并在完成后提交复核。',
        detail: `${merchantName ? `关联企业：${merchantName}。` : ''}请根据平台审核意见补齐企业资料、资质材料、联系人信息或企业介绍，完成后重新提交复核。`,
        requiredAction: '请补充企业资料与资质材料，保存后重新提交复核。'
      }
    }
    case 'REPORT_RESULT': {
      const subject = buildReportResultSubjectName({ jobName, merchantName, targetUserName })
      return {
        title: `关于${subject}的举报处理结果通知`,
        summary: `平台已完成关于${subject}的举报处理，请查看本次处理结论。`,
        detail: `平台已完成关于${subject}的举报处理。请结合处理结论、依据和后续要求，向目标对象同步本次治理结果。`,
        requiredAction: '请阅读处理结果；如有异议，可按平台要求补充说明。'
      }
    }
    case 'USER_WARNING':
      return {
        title: `账号《${targetUserName}》违规提醒`,
        summary: `平台已识别到账号《${targetUserName}》存在需要关注的风险行为，请及时自查。`,
        detail: `平台检测到账号《${targetUserName}》存在需关注的风险行为，请认真阅读说明，并按要求停止相关行为或补充必要说明。`,
        requiredAction: '请停止相关行为；如有异议，可提交补充说明或申诉。'
      }
    case 'BAN_NOTICE':
      return {
        title: `账号《${targetUserName}》限制说明`,
        summary: `平台已对账号《${targetUserName}》执行限制，请查看原因、范围与恢复条件。`,
        detail: `该事项用于同步账号《${targetUserName}》的限制或封禁结论。请补充限制范围、持续时间、触发原因及恢复条件，便于后续申诉与复核。`,
        requiredAction: '请阅读限制说明；如有异议，可按平台要求提交申诉材料。'
      }
    default:
      return null
  }
}

const syncRelatedMerchantByJobCompany = async () => {
  const companyName = selectedRelatedJob.value?.companyName
  if (createForm.value.relatedMerchantId || !companyName) {
    return
  }
  await loadRelatedMerchantOptions(companyName)
  const exactCompany = relatedMerchantOptions.value.find((item) => item.companyName === companyName)
  if (!exactCompany) {
    return
  }
  createForm.value.relatedMerchantId = exactCompany.id
  selectedRelatedMerchant.value = exactCompany
  syncSourceIdsBySelection()
}

const applyObjectPresetCopy = () => {
  const preset = buildObjectPresetDraft()
  if (!preset) {
    ElMessage.warning('当前模板缺少可预填的业务对象，请先选择关联职位、关联商家或目标用户')
    return
  }
  createForm.value = {
    ...createForm.value,
    ...preset
  }
  syncSourceIdsBySelection()
  ElMessage.success('已按当前对象预填建议文案')
}

const closeTargetUserPanel = () => {
  targetUserPanelVisible.value = false
  syncTargetUserKeyword()
}

const openTargetUserPanel = async () => {
  const keyword = isUsingSelectedTargetUserLabel() ? '' : targetUserKeyword.value
  await showTargetUserPanel(keyword)
}

const clearTargetUserSelection = async () => {
  clearSelectedTargetUser()
  await validateCreateTargetUserField()
}

const selectTargetUserOption = async (item) => {
  setSelectedTargetUserOption(item)
  closeTargetUserPanel()
  await validateCreateTargetUserField()
}

const handleTargetUserKeywordInput = async (value) => {
  const keyword = String(value || '')
  if (selectedTargetUser.value && !isUsingSelectedTargetUserLabel(keyword)) {
    clearSelectedTargetUser(false)
  }
  targetUserKeyword.value = keyword
  await showTargetUserPanel(keyword)
}

const handleRelatedJobChange = async (value) => {
  if (!value) {
    selectedRelatedJob.value = null
    if (createForm.value.sourceModule === 'JOB_AUDIT') {
      setCreateSourceSelection('JOB_AUDIT')
    }
    return
  }
  syncSelectedRelatedJob()
  syncSourceIdsBySelection()
  await syncRelatedMerchantByJobCompany()
  if (!selectedRelatedJob.value) {
    await loadRelatedJobOptions('')
  }
}

const handleRelatedMerchantChange = async (value) => {
  if (!value) {
    selectedRelatedMerchant.value = null
    if (createForm.value.sourceModule === 'MERCHANT_AUDIT') {
      setCreateSourceSelection('MERCHANT_AUDIT')
    }
    return
  }
  syncSelectedRelatedMerchant()
  syncSourceIdsBySelection()
  if (createForm.value.noticeType === 'MERCHANT_RECTIFY') {
    await tryAutoSelectMerchantTargetUser(selectedRelatedMerchant.value)
  }
  if (!selectedRelatedMerchant.value) {
    await loadRelatedMerchantOptions('')
  }
}

const fetchNotices = async () => {
  loading.value = true
  try {
    const res = await getAdminGovernanceNotices({
      ...query.value,
      current: pagination.value.current,
      size: pagination.value.size
    })
    if (res.code === 200) {
      notices.value = normalizeList(res.data)
      pagination.value.total = res.data?.total || 0
      return
    }
    notices.value = []
    pagination.value.total = 0
    ElMessage.error(res.msg || '获取治理通知失败')
  } catch (error) {
    notices.value = []
    pagination.value.total = 0
    ElMessage.error(error?.message || '获取治理通知失败')
  } finally {
    loading.value = false
  }
}

const fetchDetail = async (noticeId) => {
  detailLoading.value = true
  try {
    const res = await getAdminGovernanceNoticeDetail(noticeId)
    if (res.code === 200) {
      currentNotice.value = res.data
      return
    }
    currentNotice.value = null
    ElMessage.error(res.msg || '获取治理事项详情失败')
  } catch (error) {
    currentNotice.value = null
    ElMessage.error(error?.message || '获取治理事项详情失败')
  } finally {
    detailLoading.value = false
  }
}

const applyFilter = () => {
  pagination.value.current = 1
  fetchNotices()
}

const resetFilter = () => {
  query.value = createEmptyQuery()
  pagination.value.current = 1
  fetchNotices()
}

const applyFocusPreset = (preset) => {
  query.value = buildFocusQuery(preset?.filters)
  pagination.value.current = 1
  fetchNotices()
}

const handlePageChange = (page) => {
  pagination.value.current = page
  fetchNotices()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  fetchNotices()
}

const openDetail = async (row) => {
  detailVisible.value = true
  currentNotice.value = row
  await fetchDetail(row.id)
}

const canApproveReject = (notice) => {
  return notice?.status === 'PENDING_REVIEW'
}

const canCloseNotice = (notice) => {
  return notice && !terminalStatuses.includes(notice.status)
}

const applyCreateTemplate = (templateKey) => {
  const nextForm = createCreateFormByTemplate(templateKey)
  const currentIds = {
    targetUserId: createForm.value.targetRole === nextForm.targetRole ? createForm.value.targetUserId : '',
    sourceId: shouldPreserveSourceIdForTemplate(nextForm) ? createForm.value.sourceId : '',
    relatedJobId: nextForm.noticeType === 'JOB_RECTIFY' ? createForm.value.relatedJobId : '',
    relatedMerchantId: createForm.value.relatedMerchantId
  }
  createForm.value = {
    ...nextForm,
    ...currentIds
  }
  syncCreateSelections()
  syncSourceIdsBySelection()
}

const openCreateDialog = async () => {
  createForm.value = createCreateFormByTemplate()
  resetCreateDialogSelections()
  createDialogVisible.value = true
  await loadCreateDialogOptions()
  await nextTick()
  createFormRef.value?.clearValidate()
}

const openReviewDialog = (reviewStatus, notice) => {
  reviewForm.value = {
    noticeId: notice.id,
    reviewStatus,
    reviewComment: ''
  }
  reviewDialogVisible.value = true
}

const submitCreateNotice = async () => {
  try {
    await createFormRef.value?.validate()
  } catch (error) {
    return
  }

  const payload = {
    targetRole: createForm.value.targetRole,
    targetUserId: normalizeOptionalNumber(createForm.value.targetUserId),
    noticeType: createForm.value.noticeType,
    severity: createForm.value.severity,
    sourceModule: createForm.value.sourceModule,
    sourceId: normalizeOptionalNumber(createForm.value.sourceId),
    relatedJobId: normalizeOptionalNumber(createForm.value.relatedJobId),
    relatedMerchantId: normalizeOptionalNumber(createForm.value.relatedMerchantId),
    title: String(createForm.value.title || '').trim(),
    summary: normalizeOptionalText(createForm.value.summary),
    detail: String(createForm.value.detail || '').trim(),
    requiredAction: normalizeOptionalText(createForm.value.requiredAction),
    dueTime: normalizeOptionalDateTime(createForm.value.dueTime),
    needAck: Number(createForm.value.needAck || 0),
    needReply: Number(createForm.value.needReply || 0)
  }

  if (!payload.targetUserId) {
    ElMessage.warning('目标用户 ID 不合法')
    return
  }

  const riskConfirmed = await confirmCreateRiskBeforeSubmit()
  if (!riskConfirmed) {
    return
  }

  try {
    const res = await createAdminGovernanceNotice(payload)
    if (res.code === 200) {
      ElMessage.success('治理通知创建成功')
      createDialogVisible.value = false
      await fetchNotices()
      if (res.data) {
        await openDetail({ id: res.data })
      }
      return
    }
    ElMessage.error(res.msg || '治理通知创建失败')
  } catch (error) {
    ElMessage.error(error?.message || '治理通知创建失败')
  }
}

const submitReview = async () => {
  const { noticeId, reviewStatus, reviewComment } = reviewForm.value
  if (!noticeId || !reviewStatus) {
    ElMessage.warning('治理事项状态异常，请重新打开')
    return
  }

  if ((reviewStatus === 'REJECT' || reviewStatus === 'CLOSE') && !reviewComment.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }

  const actionText = reviewButtonText.value.replace('确认', '')
  try {
    await ElMessageBox.confirm(
      `确认${actionText}当前治理事项吗？`,
      '处理确认',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: currentReviewMeta.value.confirmType
        }
      )
  } catch (error) {
    return
  }

  try {
    const res = await reviewAdminGovernanceNotice(noticeId, {
      reviewStatus,
      reviewComment: reviewComment.trim()
    })
    if (res.code === 200) {
      ElMessage.success('治理事项处理成功')
      reviewDialogVisible.value = false
      await Promise.all([fetchNotices(), fetchDetail(noticeId)])
      return
    }
    ElMessage.error(res.msg || '治理事项处理失败')
  } catch (error) {
    ElMessage.error(error?.message || '治理事项处理失败')
  }
}

onMounted(() => {
  fetchNotices()
  document.addEventListener('mousedown', handleDocumentMouseDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleDocumentMouseDown)
})

const handleDocumentMouseDown = (event) => {
  if (!targetUserPanelVisible.value) return
  if (targetUserPickerRef.value?.contains(event.target)) return
  closeTargetUserPanel()
}

watch(
  () => createForm.value.targetRole,
  async (role) => {
    if (!createDialogVisible.value) return
    if (selectedTargetUser.value && selectedTargetUser.value.role !== role) {
      clearSelectedTargetUser()
    }
    await loadTargetUserOptions('')
  }
)

watch(
  () => [createForm.value.relatedJobId, createForm.value.relatedMerchantId, createForm.value.targetUserId],
  () => {
    syncCreateSelections()
  }
)

watch(selectedTargetUser, () => {
  syncTargetUserKeyword()
})
</script>

<style scoped>
.governance-page {
  display: grid;
  gap: 18px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #111827;
}

.page-desc {
  margin: 8px 0 0;
  color: #6b7280;
  line-height: 1.6;
}

.header-meta {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 14px 18px;
  border-radius: 18px;
  background:
    linear-gradient(140deg, rgba(0, 113, 227, 0.12), rgba(59, 130, 246, 0.04));
  border: 1px solid rgba(0, 113, 227, 0.12);
  color: #475569;
}

.meta-label,
.meta-unit {
  font-size: 13px;
}

.meta-value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.workflow-card {
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background:
    radial-gradient(circle at top left, rgba(0, 113, 227, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 249, 255, 0.92));
}

.workflow-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.workflow-item {
  display: flex;
  gap: 14px;
  padding: 6px 4px;
}

.workflow-index {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(0, 113, 227, 0.12);
  color: #0071e3;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.workflow-copy {
  display: grid;
  gap: 4px;
}

.workflow-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.workflow-desc {
  color: #64748b;
  line-height: 1.6;
  font-size: 13px;
}

.focus-card {
  border: 1px solid rgba(248, 113, 113, 0.12);
  background:
    radial-gradient(circle at top right, rgba(248, 113, 113, 0.14), transparent 28%),
    radial-gradient(circle at bottom left, rgba(0, 113, 227, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(249, 250, 251, 0.94));
}

.focus-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.focus-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.focus-card__desc {
  margin-top: 6px;
  color: #64748b;
  line-height: 1.6;
  font-size: 13px;
}

.focus-card__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.focus-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.focus-chip {
  display: grid;
  gap: 8px;
  text-align: left;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.92);
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease, background 0.22s ease;
}

.focus-chip:hover,
.focus-chip--active {
  transform: translateY(-1px);
  border-color: rgba(239, 68, 68, 0.22);
  background: linear-gradient(150deg, rgba(254, 242, 242, 0.94), rgba(255, 255, 255, 0.98));
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.08);
}

.focus-chip__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #dc2626;
}

.focus-chip__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.focus-chip__desc {
  color: #475569;
  line-height: 1.6;
  font-size: 13px;
}

.focus-chip__meta {
  font-size: 12px;
  color: #94a3b8;
}

.filter-card {
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.adaptive-filter-control {
  min-width: 180px;
}

.adaptive-filter-control--sm {
  width: 180px;
}

.adaptive-filter-actions {
  display: flex;
  gap: 10px;
}

.batch-row {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
}

.batch-hint {
  font-size: 13px;
}

.notice-no {
  display: grid;
  gap: 6px;
}

.notice-no__value {
  font-weight: 600;
  color: #111827;
}

.notice-no__time {
  font-size: 12px;
  color: #94a3b8;
}

.notice-title-cell {
  display: grid;
  gap: 8px;
}

.notice-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notice-title {
  font-weight: 700;
  color: #111827;
}

.overdue-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
  font-size: 12px;
  font-weight: 600;
}

.focus-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  font-size: 12px;
  font-weight: 600;
}

.notice-summary {
  color: #475569;
  line-height: 1.6;
}

.notice-sub-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: #94a3b8;
}

.due-time-cell {
  display: grid;
  gap: 4px;
}

.due-time-meta {
  font-size: 12px;
  color: #94a3b8;
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.status-bar {
  width: 4px;
  height: 22px;
  border-radius: 999px;
  background: #cbd5e1;
}

.status-pending-read {
  background: #94a3b8;
}

.status-pending-action {
  background: #f59e0b;
}

.status-pending-review {
  background: #3b82f6;
}

.status-finished {
  background: #22c55e;
}

.status-rejected,
.status-expired {
  background: #ef4444;
}

.status-closed {
  background: #64748b;
}

.drawer-loading {
  padding: 8px 8px 16px;
}

.drawer-body {
  display: grid;
  gap: 20px;
  padding-bottom: 20px;
}

.drawer-hero {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(160deg, rgba(0, 113, 227, 0.12), rgba(255, 255, 255, 0.96));
  border: 1px solid rgba(0, 113, 227, 0.14);
}

.drawer-hero__title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.5;
  color: #111827;
}

.drawer-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.meta-card {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.meta-card__label {
  font-size: 12px;
  color: #94a3b8;
}

.meta-card__value {
  color: #111827;
  font-weight: 700;
  line-height: 1.5;
}

.meta-card__sub {
  font-size: 12px;
  color: #64748b;
}

.detail-section {
  display: grid;
  gap: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.detail-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.96);
}

.detail-line {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.detail-line--block {
  align-items: start;
}

.detail-label {
  color: #94a3b8;
  font-size: 13px;
}

.detail-value,
.detail-rich-text {
  color: #1f2937;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.appeal-card {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(239, 68, 68, 0.18);
  background:
    linear-gradient(145deg, rgba(254, 242, 242, 0.92), rgba(255, 255, 255, 0.98));
}

.appeal-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.appeal-card__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.appeal-card__meta {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

.appeal-card__content {
  color: #334155;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.appeal-card__hint {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px dashed rgba(239, 68, 68, 0.16);
  color: #64748b;
  line-height: 1.6;
  font-size: 13px;
}

.action-timeline {
  padding: 4px 4px 4px 2px;
}

.timeline-item {
  display: grid;
  gap: 6px;
  padding: 2px 0 6px;
}

.timeline-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.timeline-title {
  font-weight: 700;
  color: #111827;
}

.timeline-role,
.timeline-meta {
  font-size: 12px;
  color: #94a3b8;
}

.timeline-content {
  color: #475569;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 4px;
}

.review-dialog-copy {
  margin-bottom: 12px;
  color: #64748b;
  line-height: 1.6;
}

.create-dialog-copy {
  margin-bottom: 14px;
  color: #64748b;
  line-height: 1.7;
  font-size: 14px;
}

.template-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}

.template-chip {
  --template-accent: rgba(0, 113, 227, 0.72);
  --template-accent-soft: rgba(0, 113, 227, 0.12);
  --template-accent-bg: rgba(239, 246, 255, 0.82);
  position: relative;
  overflow: hidden;
  border: 1px solid var(--template-accent-soft);
  border-radius: 16px;
  padding: 12px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 255, 255, 0.92)),
    linear-gradient(145deg, var(--template-accent-bg), rgba(255, 255, 255, 0.96));
  display: grid;
  gap: 4px;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease, background 0.22s ease;
}

.template-chip::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--template-accent), rgba(255, 255, 255, 0.18));
  opacity: 0.95;
}

.template-chip:hover,
.template-chip--active {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--template-accent) 28%, white);
  box-shadow:
    0 16px 32px rgba(15, 23, 42, 0.08),
    0 0 0 1px color-mix(in srgb, var(--template-accent) 18%, white);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.99), rgba(255, 255, 255, 0.94)),
    linear-gradient(145deg, color-mix(in srgb, var(--template-accent-bg) 88%, white), rgba(255, 255, 255, 0.98));
}

.template-chip__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.template-chip__desc {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}

.template-chip[data-template='JOB_RECTIFY'] {
  --template-accent: rgba(37, 99, 235, 0.82);
  --template-accent-soft: rgba(37, 99, 235, 0.16);
  --template-accent-bg: rgba(219, 234, 254, 0.82);
}

.template-chip[data-template='MERCHANT_RECTIFY'] {
  --template-accent: rgba(13, 148, 136, 0.8);
  --template-accent-soft: rgba(13, 148, 136, 0.16);
  --template-accent-bg: rgba(204, 251, 241, 0.82);
}

.template-chip[data-template='USER_WARNING'] {
  --template-accent: rgba(217, 119, 6, 0.82);
  --template-accent-soft: rgba(217, 119, 6, 0.16);
  --template-accent-bg: rgba(254, 243, 199, 0.88);
}

.template-chip[data-template='REPORT_RESULT'] {
  --template-accent: rgba(71, 85, 105, 0.78);
  --template-accent-soft: rgba(71, 85, 105, 0.14);
  --template-accent-bg: rgba(241, 245, 249, 0.9);
}

.template-chip[data-template='BAN_NOTICE'] {
  --template-accent: rgba(220, 38, 38, 0.8);
  --template-accent-soft: rgba(220, 38, 38, 0.16);
  --template-accent-bg: rgba(254, 226, 226, 0.86);
}

.template-hint {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
  color: #64748b;
  line-height: 1.6;
  font-size: 13px;
}

.prefill-helper {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(0, 113, 227, 0.12);
  background: linear-gradient(145deg, rgba(0, 113, 227, 0.08), rgba(255, 255, 255, 0.98));
}

.prefill-helper__copy {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.create-form {
  display: grid;
  gap: 2px;
}

.create-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 18px;
}

.create-form-item--span-2 {
  grid-column: span 2;
}

.create-remote-select {
  width: 100%;
}

.inline-remote-picker {
  position: relative;
  width: 100%;
}

.inline-remote-picker__panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  z-index: 24;
  display: grid;
  gap: 0;
  max-height: 288px;
  overflow-y: auto;
  padding: 8px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.inline-remote-picker__state {
  padding: 14px 16px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.inline-remote-picker__state--empty {
  color: #94a3b8;
}

.inline-remote-picker__option {
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 14px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.inline-remote-picker__option:hover,
.inline-remote-picker__option--active {
  background: rgba(0, 113, 227, 0.08);
  box-shadow: inset 0 0 0 1px rgba(0, 113, 227, 0.08);
}

.picker-option {
  display: grid;
  gap: 4px;
  padding: 0;
  min-width: 0;
  align-content: start;
}

.picker-option__title {
  display: block;
  color: #111827;
  font-weight: 600;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
}

.picker-option__meta {
  display: block;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
}

:deep(.governance-remote-select-popper .el-select-dropdown__item) {
  height: auto !important;
  min-height: 72px !important;
  padding: 10px 14px !important;
  line-height: 1.5 !important;
  display: block !important;
  box-sizing: border-box;
  white-space: normal !important;
  overflow: visible !important;
  text-overflow: initial !important;
}

:deep(.governance-remote-select-popper .el-select-dropdown__item.is-disabled) {
  min-height: 44px;
}

.relation-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.readonly-check-panel {
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background:
    linear-gradient(150deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
}

.readonly-check-panel__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.readonly-check-panel__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.readonly-check-panel__desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.readonly-check-panel__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.readonly-check-panel__alert {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(239, 68, 68, 0.12);
  background: linear-gradient(145deg, rgba(254, 242, 242, 0.92), rgba(255, 255, 255, 0.98));
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.7;
  font-weight: 600;
}

.readonly-check-panel__stat {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.readonly-check-panel__stat--danger {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

.readonly-check-panel__stat--warning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.readonly-check-panel__stat--success {
  background: rgba(34, 197, 94, 0.1);
  color: #15803d;
}

.readonly-check-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.readonly-check-item {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: rgba(255, 255, 255, 0.84);
}

.readonly-check-item--danger {
  border-color: rgba(239, 68, 68, 0.18);
  background: linear-gradient(145deg, rgba(254, 242, 242, 0.92), rgba(255, 255, 255, 0.98));
}

.readonly-check-item--warning {
  border-color: rgba(245, 158, 11, 0.16);
  background: linear-gradient(145deg, rgba(255, 251, 235, 0.92), rgba(255, 255, 255, 0.98));
}

.readonly-check-item--success {
  border-color: rgba(34, 197, 94, 0.16);
  background: linear-gradient(145deg, rgba(240, 253, 244, 0.92), rgba(255, 255, 255, 0.98));
}

.readonly-check-item--info {
  border-color: rgba(59, 130, 246, 0.14);
  background: linear-gradient(145deg, rgba(239, 246, 255, 0.92), rgba(255, 255, 255, 0.98));
}

.readonly-check-item__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.readonly-check-item__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.readonly-check-item__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

:deep(.governance-create-risk-box) {
  width: min(720px, calc(100vw - 40px));
}

:deep(.governance-create-risk-box .el-message-box__message) {
  padding-top: 2px;
}

:deep(.governance-create-risk-box .create-risk-confirm) {
  display: grid;
  gap: 16px;
}

:deep(.governance-create-risk-box .create-risk-confirm__lead) {
  color: #334155;
  font-size: 14px;
  line-height: 1.8;
}

:deep(.governance-create-risk-box .create-risk-confirm__section) {
  display: grid;
  gap: 12px;
}

:deep(.governance-create-risk-box .create-risk-confirm__section-title) {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}

:deep(.governance-create-risk-box .create-risk-confirm__list) {
  display: grid;
  gap: 10px;
}

:deep(.governance-create-risk-box .create-risk-confirm__item) {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  background: linear-gradient(145deg, rgba(254, 242, 242, 0.94), rgba(255, 255, 255, 0.98));
}

:deep(.governance-create-risk-box .create-risk-confirm__item-title) {
  font-size: 13px;
  font-weight: 700;
  color: #b91c1c;
}

:deep(.governance-create-risk-box .create-risk-confirm__item-desc) {
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

:deep(.governance-create-risk-box .create-risk-confirm__summary-grid) {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

:deep(.governance-create-risk-box .create-risk-confirm__summary-card) {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(0, 113, 227, 0.12);
  background: linear-gradient(145deg, rgba(239, 246, 255, 0.88), rgba(255, 255, 255, 0.98));
}

:deep(.governance-create-risk-box .create-risk-confirm__summary-label) {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.04em;
}

:deep(.governance-create-risk-box .create-risk-confirm__summary-value) {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  color: #0f172a;
}

:deep(.governance-create-risk-box .create-risk-confirm__summary-meta) {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

:global(.governance-create-dialog .el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

:global(.governance-create-dialog .el-dialog__header) {
  padding: 22px 28px 16px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
}

:global(.governance-create-dialog .el-dialog__body) {
  max-height: calc(100vh - 176px);
  overflow-y: auto;
  padding: 18px 28px 20px;
}

:global(.governance-create-dialog .el-dialog__footer) {
  padding: 16px 28px 22px;
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.72), rgba(255, 255, 255, 0.96));
}

.relation-summary-card {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(0, 113, 227, 0.14);
  background:
    linear-gradient(145deg, rgba(0, 113, 227, 0.1), rgba(255, 255, 255, 0.98));
}

.relation-summary-card--empty {
  border-color: rgba(148, 163, 184, 0.14);
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(255, 255, 255, 0.98));
}

.relation-summary-card__label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #64748b;
}

.relation-summary-card__value {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
}

.relation-summary-card__meta {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.create-date-picker {
  width: 100%;
}

:deep(.governance-row-overdue) {
  --el-table-tr-bg-color: rgba(254, 242, 242, 0.66);
}

:deep(.governance-row-ban-appeal) {
  --el-table-tr-bg-color: rgba(255, 247, 237, 0.88);
}

@media (max-width: 1280px) {
  .create-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .create-form-item--span-2 {
    grid-column: span 1;
  }

  .workflow-list {
    grid-template-columns: 1fr;
  }

  .focus-strip {
    grid-template-columns: 1fr;
  }

  .template-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  :deep(.governance-create-risk-box .create-risk-confirm__summary-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .header-meta {
    width: 100%;
    justify-content: center;
  }

  .focus-card__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .drawer-meta-grid {
    grid-template-columns: 1fr;
  }

  .detail-line {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .batch-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .create-form-grid {
    grid-template-columns: 1fr;
  }

  .create-form-item--span-2 {
    grid-column: span 1;
  }

  .prefill-helper {
    flex-direction: column;
    align-items: flex-start;
  }

  .appeal-card__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .readonly-check-panel__head,
  .readonly-check-item__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .readonly-check-grid,
  .relation-summary-grid {
    grid-template-columns: 1fr;
  }

  .template-strip {
    grid-template-columns: 1fr;
  }

  :deep(.governance-create-risk-box .create-risk-confirm__summary-grid) {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.governance-remote-select-popper.el-select-dropdown .el-select-dropdown__item {
  height: auto !important;
  min-height: 72px !important;
  padding: 10px 14px !important;
  line-height: normal !important;
  white-space: normal !important;
  overflow: visible !important;
  text-overflow: clip !important;
  display: flex !important;
  align-items: flex-start !important;
  box-sizing: border-box;
}

.governance-remote-select-popper.el-select-dropdown .el-select-dropdown__item > .picker-option {
  width: 100%;
}

.governance-remote-select-popper.el-select-dropdown .el-select-dropdown__item.is-disabled {
  min-height: 44px !important;
}
</style>
