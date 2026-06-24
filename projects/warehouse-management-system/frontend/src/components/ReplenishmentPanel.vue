<!--
  @file 速览索引
  @summary 智能补货页面，负责补货计划查询、E1统计展示、批量操作与导出，是治理看板的重要下游页面。
  @core 1. 展示补货计划分页列表与明细
  @core 2. 展示E1统计指标与人工干预Top SKU
  @core 3. 支持承接首页快捷筛选 preset
  @core 4. 支持重算、确认建议、修改最终数量与批量流转
  @core 5. 支持当前页CSV导出
  @entry 先看：loadData、loadMetrics、applyQuickFilter、batchConfirmSelected
  @deps 关键依赖：api/replenishment.js、DashboardPanel.vue
  @state 关键状态：query、metricsQuery、rows、selectedRows、detail
  @risk 高风险修改点：状态流转、批量操作幂等、统计口径展示、首页快捷跳转兼容性
  @link 相关文件：前端/src/components/DashboardPanel.vue、后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, ElNotification } from "element-plus";
import {
  replenishmentCalculateApi,
  replenishmentConfirmApi,
  replenishmentDetailApi,
  replenishmentMetricsApi,
  replenishmentPageApi,
  replenishmentRecalculateApi,
  replenishmentUpdateFinalQtyApi,
  replenishmentToPurchaseDraftApi
} from "../api/replenishment";
import { warehouseOptionsApi } from "../api/warehouse";

const props = defineProps({
  roleCode: {
    type: String,
    default: ""
  }
});

const canOperate = computed(() => props.roleCode === "ADMIN" || props.roleCode === "PURCHASER");
const roleLabelMap = {
  ADMIN: "管理员",
  WAREHOUSE: "仓库员",
  PURCHASER: "采购员"
};
const roleAccessHint = computed(() => {
  const roleLabel = roleLabelMap[props.roleCode] || "当前账号";
  if (canOperate.value) {
    return {
      type: "success",
      title: `${roleLabel}可执行生成建议、重算、确认与生成采购草稿操作。`
    };
  }
  return {
    type: "warning",
    title: `${roleLabel}当前为只读模式，仅可查询与查看补货建议详情。`
  };
});
const loading = ref(false);
const saving = ref(false);
const actionLoading = ref(false);
const recalculateSaving = ref(false);
const rows = ref([]);
const total = ref(0);
const selectedRows = ref([]);
const detailVisible = ref(false);
const detailData = ref(null);
const calculateVisible = ref(false);
const recalculateVisible = ref(false);
const recalculateOriginal = ref(null);
const warehouseOptions = ref([]);
const detailSavingIds = ref([]);
const FILTER_DEBOUNCE_MS = 300;
const METRICS_DEFAULT_DAYS = 30;
let keywordDebounceTimer = null;
const metricsLoading = ref(false);
const metrics = ref(null);

const query = reactive({
  keyword: "",
  status: null,
  generatedDateRange: [],
  pageNo: 1,
  pageSize: 10
});

const calculateForm = reactive({
  warehouseId: null,
  calcDays: 15,
  leadTimeDays: 3,
  safetyDays: 2,
  remark: ""
});
const recalculateForm = reactive({
  id: null,
  planNo: "",
  warehouseName: "",
  calcDays: 15,
  leadTimeDays: 3,
  safetyDays: 2,
  remark: ""
});
const metricsQuery = reactive({
  warehouseId: null,
  dateRange: []
});

const statusMap = {
  0: { text: "待确认", type: "info" },
  1: { text: "待转采购", type: "warning" },
  2: { text: "已生成采购草稿", type: "success" }
};
const selectedDraftRows = computed(() => selectedRows.value.filter((row) => row.status === 0));
const selectedConfirmedRows = computed(() => selectedRows.value.filter((row) => row.status === 1));
const metricsOverviewCards = computed(() => {
  const overview = metrics.value?.overview || {};
  return [
    { label: "采纳率", value: formatRatioPercent(overview.adoptionRate) },
    { label: "人工干预率", value: formatRatioPercent(overview.manualAdjustRate) },
    { label: "缺口命中率", value: formatRatioPercent(overview.shortageHitRate) },
    { label: "MAPE", value: formatRatioPercent(overview.mape) },
    { label: "周转率", value: formatRatio(overview.inventoryTurnoverRate) },
    { label: "统计明细数", value: formatInteger(overview.itemCount) }
  ];
});

function formatStatus(status) {
  return statusMap[status]?.text || "未知";
}

function statusType(status) {
  return statusMap[status]?.type || "info";
}

function formatTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    const text = String(value).replace("T", " ");
    return text.length >= 16 ? text.slice(0, 16) : text;
  }
  const pad = (n) => String(n).padStart(2, "0");
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hour = pad(date.getHours());
  const minute = pad(date.getMinutes());
  const currentYear = new Date().getFullYear();
  if (year === currentYear) {
    return `${month}-${day} ${hour}:${minute}`;
  }
  return `${year}-${month}-${day} ${hour}:${minute}`;
}

function formatInteger(value) {
  const number = Number(value ?? 0);
  if (!Number.isFinite(number)) {
    return "0";
  }
  return String(Math.floor(number));
}

function formatRatio(value) {
  const number = Number(value ?? 0);
  if (!Number.isFinite(number)) {
    return "0.00";
  }
  return number.toFixed(2);
}

function formatRatioPercent(value) {
  const number = Number(value ?? 0);
  if (!Number.isFinite(number)) {
    return "0.00%";
  }
  return `${(number * 100).toFixed(2)}%`;
}

function toDateText(date) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function defaultMetricsDateRange() {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - (METRICS_DEFAULT_DAYS - 1));
  return [toDateText(start), toDateText(end)];
}

function resetCalculateForm() {
  calculateForm.warehouseId = null;
  calculateForm.calcDays = 15;
  calculateForm.leadTimeDays = 3;
  calculateForm.safetyDays = 2;
  calculateForm.remark = "";
}

function buildCalculatePayload() {
  return {
    warehouseId: calculateForm.warehouseId,
    calcDays: calculateForm.calcDays,
    leadTimeDays: calculateForm.leadTimeDays,
    safetyDays: calculateForm.safetyDays,
    remark: calculateForm.remark || null
  };
}

function resetRecalculateForm(row) {
  const original = {
    calcDays: Number(row.calcDays ?? 15),
    leadTimeDays: Number(row.leadTimeDays ?? 3),
    safetyDays: Number(row.safetyDays ?? 2)
  };
  recalculateOriginal.value = original;
  recalculateForm.id = row.id;
  recalculateForm.planNo = row.planNo || "";
  recalculateForm.warehouseName = row.warehouseName || "";
  recalculateForm.calcDays = original.calcDays;
  recalculateForm.leadTimeDays = original.leadTimeDays;
  recalculateForm.safetyDays = original.safetyDays;
  recalculateForm.remark = row.remark || "";
}

function buildRecalculatePayload() {
  return {
    calcDays: recalculateForm.calcDays,
    leadTimeDays: recalculateForm.leadTimeDays,
    safetyDays: recalculateForm.safetyDays,
    // 传空字符串表示清空备注；不传字段才表示沿用原值。
    remark: recalculateForm.remark
  };
}

function validateRecalculateForm() {
  const checks = [
    { value: recalculateForm.calcDays, min: 1, max: 90, label: "计算天数" },
    { value: recalculateForm.leadTimeDays, min: 0, max: 60, label: "交期天数" },
    { value: recalculateForm.safetyDays, min: 0, max: 30, label: "安全天数" }
  ];
  for (const check of checks) {
    if (!Number.isFinite(Number(check.value))) {
      ElMessage.warning(`${check.label}不能为空`);
      return false;
    }
    if (check.value < check.min || check.value > check.max) {
      ElMessage.warning(`${check.label}范围应在 ${check.min}-${check.max}`);
      return false;
    }
  }
  return true;
}

function validateCalculateForm() {
  if (!calculateForm.warehouseId) {
    ElMessage.warning("请选择仓库");
    return false;
  }
  return true;
}

async function loadWarehouseOptions() {
  try {
    warehouseOptions.value = await warehouseOptionsApi();
  } catch (error) {
    ElMessage.error(error?.message || "仓库选项加载失败");
  }
}

async function loadMetrics() {
  metricsLoading.value = true;
  try {
    const params = {};
    if (metricsQuery.warehouseId) {
      params.warehouseId = metricsQuery.warehouseId;
    }
    if (Array.isArray(metricsQuery.dateRange) && metricsQuery.dateRange.length === 2) {
      const [startDate, endDate] = metricsQuery.dateRange;
      if (startDate && endDate) {
        params.startDate = startDate;
        params.endDate = endDate;
      }
    }
    metrics.value = await replenishmentMetricsApi(params);
  } catch (error) {
    ElMessage.error(error?.message || "统计指标加载失败");
  } finally {
    metricsLoading.value = false;
  }
}

function resetMetricsFilter() {
  metricsQuery.warehouseId = null;
  metricsQuery.dateRange = defaultMetricsDateRange();
  loadMetrics();
}

async function loadData() {
  loading.value = true;
  try {
    const params = {
      keyword: query.keyword,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    };
    if (query.status !== null && query.status !== "") {
      params.status = query.status;
    }
    if (Array.isArray(query.generatedDateRange) && query.generatedDateRange.length === 2) {
      const [generatedDateStart, generatedDateEnd] = query.generatedDateRange;
      if (generatedDateStart && generatedDateEnd) {
        params.generatedDateStart = generatedDateStart;
        params.generatedDateEnd = generatedDateEnd;
      }
    }
    const data = await replenishmentPageApi(params);
    rows.value = data.records || [];
    total.value = data.total || 0;
    selectedRows.value = [];
  } catch (error) {
    ElMessage.error(error?.message || "补货建议列表加载失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 看板快捷入口筛选：
 * - 支持按状态直达（待确认/待转采购/已生成采购草稿）；
 * - 支持带关键词直达；
 * - 默认清空未传入的筛选项，避免旧筛选残留影响判断。
 */
async function applyQuickFilter(preset = {}) {
  if (keywordDebounceTimer) {
    clearTimeout(keywordDebounceTimer);
    keywordDebounceTimer = null;
  }
  query.pageNo = 1;
  query.keyword = typeof preset.keyword === "string" ? preset.keyword : "";
  if (preset.generatedDateStart && preset.generatedDateEnd) {
    query.generatedDateRange = [preset.generatedDateStart, preset.generatedDateEnd];
  } else {
    query.generatedDateRange = [];
  }
  if (preset.status === null || preset.status === "" || preset.status === undefined) {
    query.status = null;
  } else {
    const parsedStatus = Number(preset.status);
    query.status = Number.isNaN(parsedStatus) ? null : parsedStatus;
  }
  await loadData();
}

/**
 * 统一的筛选刷新入口：
 * 1) 切换筛选项后自动回到第一页；
 * 2) 立即刷新列表结果，不再依赖手动点“查询”按钮。
 */
function triggerFilterSearch() {
  query.pageNo = 1;
  loadData();
}

/**
 * 关键词输入使用防抖，避免每个字符都触发一次请求。
 */
function handleKeywordInput() {
  if (keywordDebounceTimer) {
    clearTimeout(keywordDebounceTimer);
  }
  keywordDebounceTimer = setTimeout(() => {
    triggerFilterSearch();
  }, FILTER_DEBOUNCE_MS);
}

/**
 * 清空关键词时立即刷新，确保列表恢复到无关键词状态。
 */
function handleKeywordClear() {
  if (keywordDebounceTimer) {
    clearTimeout(keywordDebounceTimer);
    keywordDebounceTimer = null;
  }
  triggerFilterSearch();
}

/**
 * 按回车时立即查询，覆盖防抖等待时间，提升操作手感。
 */
function handleKeywordEnter() {
  if (keywordDebounceTimer) {
    clearTimeout(keywordDebounceTimer);
    keywordDebounceTimer = null;
  }
  triggerFilterSearch();
}

/**
 * 状态筛选变化后立即刷新。
 */
function handleStatusChange() {
  triggerFilterSearch();
}

/**
 * 生成日期筛选变化后立即刷新。
 */
function handleGeneratedDateChange() {
  triggerFilterSearch();
}

async function openDetail(row) {
  try {
    detailSavingIds.value = [];
    detailData.value = await replenishmentDetailApi(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || "补货建议详情加载失败");
  }
}

async function openCalculate() {
  if (!canOperate.value) {
    return;
  }
  resetCalculateForm();
  if (!warehouseOptions.value.length) {
    await loadWarehouseOptions();
  }
  calculateVisible.value = true;
}

async function calculatePlan() {
  if (!canOperate.value) {
    return;
  }
  if (!validateCalculateForm()) return;
  saving.value = true;
  try {
    const payload = buildCalculatePayload();
    const detail = await replenishmentCalculateApi(payload);
    calculateVisible.value = false;
    ElMessage.success("补货建议生成成功");
    await loadData();
    await loadMetrics();
    detailSavingIds.value = [];
    detailData.value = detail;
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || "补货建议生成失败");
  } finally {
    saving.value = false;
  }
}

function recalculateRow(row) {
  if (!canOperate.value) {
    return;
  }
  resetRecalculateForm(row);
  recalculateVisible.value = true;
}

async function submitRecalculate() {
  if (!canOperate.value) {
    return;
  }
  if (!recalculateForm.id) {
    ElMessage.warning("未找到重算目标计划");
    return;
  }
  if (!validateRecalculateForm()) return;

  const before = recalculateOriginal.value || {
    calcDays: Number(recalculateForm.calcDays),
    leadTimeDays: Number(recalculateForm.leadTimeDays),
    safetyDays: Number(recalculateForm.safetyDays)
  };

  recalculateSaving.value = true;
  try {
    const detail = await replenishmentRecalculateApi(recalculateForm.id, buildRecalculatePayload());
    recalculateVisible.value = false;
    await loadData();
    await loadMetrics();
    detailSavingIds.value = [];
    detailData.value = detail;
    detailVisible.value = true;

    const changed = [
      before.calcDays !== detail.calcDays ? `计算天数 ${before.calcDays} -> ${detail.calcDays}` : null,
      before.leadTimeDays !== detail.leadTimeDays ? `交期天数 ${before.leadTimeDays} -> ${detail.leadTimeDays}` : null,
      before.safetyDays !== detail.safetyDays ? `安全天数 ${before.safetyDays} -> ${detail.safetyDays}` : null
    ].filter(Boolean);
    const changeText = changed.length ? changed.join("，") : "参数未变化";
    const itemCount = detail?.items?.length || 0;

    ElNotification({
      title: "重算完成",
      type: "success",
      duration: 5200,
      message: `计划 ${detail.planNo} 已重算，${changeText}，共 ${itemCount} 条明细。`
    });
    recalculateOriginal.value = null;
  } catch (error) {
    ElMessage.error(error?.message || "重算失败");
  } finally {
    recalculateSaving.value = false;
  }
}

async function confirmRow(row) {
  if (!canOperate.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `计划号：${row.planNo}\n确认后建议将锁定，不能再重算。`,
      "二次确认",
      { type: "warning", confirmButtonText: "确认执行", cancelButtonText: "取消" }
    );
    actionLoading.value = true;
    await replenishmentConfirmApi(row.id);
    ElMessage.success("确认成功");
    await loadData();
    await loadMetrics();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "确认失败");
    }
  } finally {
    actionLoading.value = false;
  }
}

async function toPurchaseDraftRow(row) {
  if (!canOperate.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `计划号：${row.planNo}\n将生成采购草稿号并标记为已生成采购草稿。`,
      "二次确认",
      { type: "warning", confirmButtonText: "确认执行", cancelButtonText: "取消" }
    );
    actionLoading.value = true;
    await replenishmentToPurchaseDraftApi(row.id);
    ElMessage.success("采购草稿生成成功");
    await loadData();
    await loadMetrics();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "生成采购草稿失败");
    }
  } finally {
    actionLoading.value = false;
  }
}

function handleSelectionChange(selection) {
  selectedRows.value = selection || [];
}

function buildCsvText(lines) {
  return `\uFEFF${lines.join("\n")}`;
}

function downloadCsv(fileName, lines) {
  const blob = new Blob([buildCsvText(lines)], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

function exportCurrentPageCsv() {
  const header = [
    "计划号", "仓库", "状态", "计算窗口", "交期天数", "安全天数", "采购草稿号", "生成时间"
  ];
  const lines = [
    header.join(","),
    ...rows.value.map((row) => {
      const values = [
        row.planNo || "",
        row.warehouseName || "",
        formatStatus(row.status),
        row.calcDays ?? "",
        row.leadTimeDays ?? "",
        row.safetyDays ?? "",
        row.purchaseDraftNo || "",
        formatTime(row.generatedAt)
      ];
      return values.map((value) => `"${String(value).replace(/"/g, "\"\"")}"`).join(",");
    })
  ];
  downloadCsv(`M6-补货建议列表-${toDateText(new Date())}.csv`, lines);
  ElMessage.success("当前页CSV已导出");
}

async function batchConfirmSelected() {
  if (!canOperate.value || !selectedDraftRows.value.length) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将批量确认 ${selectedDraftRows.value.length} 条待确认计划，确认后将不可重算。`,
      "批量确认",
      { type: "warning", confirmButtonText: "确认执行", cancelButtonText: "取消" }
    );
    actionLoading.value = true;
    let success = 0;
    let failed = 0;
    for (const row of selectedDraftRows.value) {
      try {
        await replenishmentConfirmApi(row.id);
        success += 1;
      } catch (error) {
        failed += 1;
      }
    }
    await loadData();
    await loadMetrics();
    ElNotification({
      title: "批量确认完成",
      type: failed > 0 ? "warning" : "success",
      message: `成功 ${success} 条，失败 ${failed} 条。`
    });
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "批量确认失败");
    }
  } finally {
    actionLoading.value = false;
  }
}

async function batchToPurchaseDraftSelected() {
  if (!canOperate.value || !selectedConfirmedRows.value.length) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将批量生成 ${selectedConfirmedRows.value.length} 条采购草稿。`,
      "批量转采购草稿",
      { type: "warning", confirmButtonText: "确认执行", cancelButtonText: "取消" }
    );
    actionLoading.value = true;
    let success = 0;
    let failed = 0;
    for (const row of selectedConfirmedRows.value) {
      try {
        await replenishmentToPurchaseDraftApi(row.id);
        success += 1;
      } catch (error) {
        failed += 1;
      }
    }
    await loadData();
    await loadMetrics();
    ElNotification({
      title: "批量转草稿完成",
      type: failed > 0 ? "warning" : "success",
      message: `成功 ${success} 条，失败 ${failed} 条。`
    });
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "批量转采购草稿失败");
    }
  } finally {
    actionLoading.value = false;
  }
}

function isDetailItemSaving(itemId) {
  return detailSavingIds.value.includes(itemId);
}

async function saveFinalQtyRow(row) {
  if (!canOperate.value) {
    return;
  }
  if (!detailData.value || detailData.value.status !== 0) {
    ElMessage.warning("仅待确认状态可调整最终量");
    return;
  }
  if (isDetailItemSaving(row.id)) {
    return;
  }
  const nextFinalQty = Number(row.finalQty);
  if (!Number.isFinite(nextFinalQty) || nextFinalQty < 0) {
    ElMessage.warning("最终量不能小于0");
    return;
  }
  detailSavingIds.value = [...detailSavingIds.value, row.id];
  try {
    const detail = await replenishmentUpdateFinalQtyApi(detailData.value.id, row.id, { finalQty: Math.floor(nextFinalQty) });
    detailData.value = detail;
    await loadData();
    await loadMetrics();
    ElMessage.success("最终量已更新");
  } catch (error) {
    ElMessage.error(error?.message || "最终量更新失败");
  } finally {
    detailSavingIds.value = detailSavingIds.value.filter((id) => id !== row.id);
  }
}

defineExpose({ loadData, applyQuickFilter, loadMetrics });

onMounted(async () => {
  metricsQuery.dateRange = defaultMetricsDateRange();
  if (!warehouseOptions.value.length) {
    await loadWarehouseOptions();
  }
  await loadMetrics();
});

onBeforeUnmount(() => {
  if (keywordDebounceTimer) {
    clearTimeout(keywordDebounceTimer);
    keywordDebounceTimer = null;
  }
});
</script>

<template>
  <div class="panel">
    <el-alert
      title="M6 说明：本页用于查看并管理智能补货建议，具备权限时可执行重算、确认与生成采购草稿。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <el-alert
      :title="roleAccessHint.title"
      :type="roleAccessHint.type"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <div class="metrics-panel">
      <div class="metrics-toolbar">
        <el-select v-model="metricsQuery.warehouseId" clearable placeholder="统计仓库（为空=全部）" class="wms-query-select">
          <el-option
            v-for="item in warehouseOptions"
            :key="item.id"
            :label="`${item.warehouseCode} - ${item.warehouseName}`"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="metricsQuery.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          unlink-panels
          range-separator="至"
          start-placeholder="统计开始"
          end-placeholder="统计结束"
          class="wms-query-date"
        />
        <el-button type="primary" :loading="metricsLoading" @click="loadMetrics">刷新指标</el-button>
        <el-button :disabled="metricsLoading" @click="resetMetricsFilter">重置范围</el-button>
      </div>
      <div class="metrics-cards" v-loading="metricsLoading">
        <div v-for="card in metricsOverviewCards" :key="card.label" class="metric-card">
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">{{ card.value }}</div>
        </div>
      </div>
      <el-table
        v-loading="metricsLoading"
        :data="metrics?.topAdjustSkus || []"
        border
        size="small"
        class="metrics-top-table"
      >
        <el-table-column prop="skuCode" label="Top SKU编码" min-width="130" />
        <el-table-column prop="skuName" label="SKU名称" min-width="150" />
        <el-table-column prop="adjustAbsQtyTotal" label="偏差总量" min-width="96" />
        <el-table-column prop="adjustItemCount" label="干预次数" min-width="96" />
      </el-table>
    </div>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="按计划号/仓库搜索"
        class="wms-query-input"
        @input="handleKeywordInput"
        @clear="handleKeywordClear"
        @keyup.enter="handleKeywordEnter"
      />
      <el-select
        v-model="query.status"
        clearable
        placeholder="状态"
        class="wms-query-select"
        @change="handleStatusChange"
        @clear="handleStatusChange"
      >
        <el-option :value="0" label="待确认" />
        <el-option :value="1" label="待转采购" />
        <el-option :value="2" label="已生成采购草稿" />
      </el-select>
      <el-date-picker
        v-model="query.generatedDateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        unlink-panels
        range-separator="至"
        start-placeholder="生成开始日期"
        end-placeholder="生成结束日期"
        class="wms-query-date"
        @change="handleGeneratedDateChange"
      />
      <el-button type="primary" @click="triggerFilterSearch">查询</el-button>
      <el-button v-if="canOperate" @click="openCalculate">生成建议</el-button>
      <el-button :disabled="!rows.length" @click="exportCurrentPageCsv">导出当前页CSV</el-button>
      <el-button
        v-if="canOperate"
        :disabled="!selectedDraftRows.length"
        :loading="actionLoading"
        @click="batchConfirmSelected"
      >
        批量确认({{ selectedDraftRows.length }})
      </el-button>
      <el-button
        v-if="canOperate"
        :disabled="!selectedConfirmedRows.length"
        :loading="actionLoading"
        @click="batchToPurchaseDraftSelected"
      >
        批量转采购({{ selectedConfirmedRows.length }})
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      border
      size="small"
      row-key="id"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column prop="planNo" label="计划号" min-width="128" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="仓库" min-width="96" show-overflow-tooltip />
      <el-table-column label="状态" min-width="132">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ formatStatus(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="calcDays" label="计算窗口" min-width="78" />
      <el-table-column prop="leadTimeDays" label="交期天数" min-width="78" />
      <el-table-column prop="safetyDays" label="安全天数" min-width="78" />
      <el-table-column prop="purchaseDraftNo" label="采购草稿号" min-width="116" show-overflow-tooltip />
      <el-table-column label="生成时间" min-width="124">
        <template #default="{ row }">
          {{ formatTime(row.generatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="198" fixed="right">
        <template #default="{ row }">
          <el-button size="small" class="op-btn op-view" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canOperate && row.status === 0"
            size="small"
            class="op-btn op-submit"
            :loading="recalculateSaving"
            @click="recalculateRow(row)"
          >
            重算
          </el-button>
          <el-button
            v-if="canOperate && row.status === 0"
            size="small"
            class="op-btn op-confirm"
            :loading="actionLoading"
            @click="confirmRow(row)"
          >
            确认
          </el-button>
          <el-button
            v-if="canOperate && row.status === 1"
            size="small"
            class="op-btn op-edit"
            :loading="actionLoading"
            @click="toPurchaseDraftRow(row)"
          >
            生成采购草稿
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @change="loadData"
      />
    </div>

    <el-dialog v-model="calculateVisible" title="生成补货建议" width="680px">
      <el-form label-position="top" class="calculate-form">
        <el-form-item label="仓库" required>
          <el-select v-model="calculateForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option
              v-for="item in warehouseOptions"
              :key="item.id"
              :label="`${item.warehouseCode} - ${item.warehouseName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="12" class="calculate-grid">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="计算天数">
              <el-input-number v-model="calculateForm.calcDays" :min="1" :max="90" class="calculate-number" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="交期天数">
              <el-input-number v-model="calculateForm.leadTimeDays" :min="0" :max="60" class="calculate-number" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="安全天数">
              <el-input-number v-model="calculateForm.safetyDays" :min="0" :max="30" class="calculate-number" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="calculateForm.remark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="calculateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="calculatePlan">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recalculateVisible" title="重算参数" width="680px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        :title="`计划号：${recalculateForm.planNo || '-'}（${recalculateForm.warehouseName || '未知仓库'}）`"
        description="重算会按当前参数重新生成建议明细。你可以先调整参数，再执行重算。"
      />
      <el-form label-position="top" class="calculate-form">
        <el-row :gutter="12" class="calculate-grid">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="计算天数">
              <el-input-number v-model="recalculateForm.calcDays" :min="1" :max="90" class="calculate-number" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="交期天数">
              <el-input-number v-model="recalculateForm.leadTimeDays" :min="0" :max="60" class="calculate-number" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="安全天数">
              <el-input-number v-model="recalculateForm.safetyDays" :min="0" :max="30" class="calculate-number" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注（可选）">
          <el-input
            v-model="recalculateForm.remark"
            clearable
            placeholder="例如：促销备货、参数临时调整原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recalculateVisible = false">取消</el-button>
        <el-button type="primary" :loading="recalculateSaving" @click="submitRecalculate">执行重算</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="补货建议详情" width="1120px">
      <template v-if="detailData">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="计划号">{{ detailData.planNo }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detailData.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ formatStatus(detailData.status) }}</el-descriptions-item>
          <el-descriptions-item label="计算窗口">{{ detailData.calcDays }} 天</el-descriptions-item>
          <el-descriptions-item label="交期天数">{{ detailData.leadTimeDays }} 天</el-descriptions-item>
          <el-descriptions-item label="安全天数">{{ detailData.safetyDays }} 天</el-descriptions-item>
          <el-descriptions-item label="采购草稿号">{{ detailData.purchaseDraftNo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ formatTime(detailData.generatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailData.remark || "-" }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detailData.items || []" border style="margin-top: 12px">
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="130" />
          <el-table-column prop="currentQty" label="当前库存" min-width="90" />
          <el-table-column prop="safeQty" label="安全库存" min-width="90" />
          <el-table-column prop="predictedDailySales" label="预测日均" min-width="90" />
          <el-table-column prop="predictedTotalQty" label="预测总量" min-width="90" />
          <el-table-column prop="shortageQty" label="库存缺口" min-width="90" />
          <el-table-column prop="suggestedQty" label="建议量" min-width="90" />
          <el-table-column label="最终量" min-width="120">
            <template #default="{ row }">
              <el-input-number
                v-if="detailData.status === 0 && canOperate"
                v-model="row.finalQty"
                :min="0"
                :step="1"
                :precision="0"
                controls-position="right"
                style="width: 100%"
              />
              <span v-else>{{ row.finalQty }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="recoSource" label="推荐来源" min-width="120" />
          <el-table-column prop="confidence" label="置信度" min-width="90" />
          <el-table-column prop="reason" label="建议理由" min-width="180" show-overflow-tooltip />
          <el-table-column v-if="detailData.status === 0 && canOperate" label="明细操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                class="op-btn op-edit"
                :loading="isDetailItemSaving(row.id)"
                @click="saveFinalQtyRow(row)"
              >
                保存
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.metrics-panel {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.metrics-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.metrics-cards {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.metric-card {
  border: 1px solid #e8edf7;
  border-radius: 8px;
  padding: 8px 10px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.metric-label {
  font-size: 12px;
  color: #5b6475;
}

.metric-value {
  margin-top: 3px;
  font-size: 18px;
  font-weight: 600;
  color: #16365f;
}

.metrics-top-table {
  margin-top: 4px;
}

.calculate-form .calculate-number {
  width: 100%;
  min-width: 140px;
}

@media (max-width: 1200px) {
  .metrics-cards {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }
}

@media (max-width: 768px) {
  .metrics-cards {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
