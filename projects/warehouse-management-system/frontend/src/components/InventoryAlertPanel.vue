<!--
  @file 速览索引
  @summary 库存预警页面，负责预警列表展示、筛选、预警规则维护与首页快捷筛选承接。
  @core 1. 展示库存预警分页数据
  @core 2. 支持按仓库、SKU、预警级别等条件筛选
  @core 3. 支持承接治理看板传入的快捷筛选 preset
  @core 4. 根据角色差异控制可读/可维护范围
  @entry 先看：loadData、applyQuickFilter、clearQuickFilters
  @deps 关键依赖：api/inventory.js、DashboardPanel.vue
  @state 关键状态：query、rows、ruleRows、loading
  @risk 高风险修改点：首页快捷筛选兼容性、角色权限差异、预警规则与预警列表联动
  @link 相关文件：前端/src/components/DashboardPanel.vue、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
-->
<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  inventoryAlertPageApi,
  inventoryAlertRuleCreateApi,
  inventoryAlertRulePageApi,
  inventoryAlertRuleUpdateApi
} from "../api/inventory";
import { skuPageApi } from "../api/sku";
import { warehouseOptionsApi } from "../api/warehouse";

const props = defineProps({
  roleCode: {
    type: String,
    default: ""
  }
});

const canManageRules = computed(() => props.roleCode === "ADMIN" || props.roleCode === "WAREHOUSE");
const roleLabelMap = {
  ADMIN: "管理员",
  WAREHOUSE: "仓库员",
  PURCHASER: "采购员"
};
const ruleAccessHint = computed(() => {
  const roleLabel = roleLabelMap[props.roleCode] || "当前账号";
  if (canManageRules.value) {
    return {
      type: "success",
      title: `${roleLabel}可查看预警列表并维护预警规则。`
    };
  }
  return {
    type: "warning",
    title: `${roleLabel}当前为只读模式，仅可查看预警列表。预警规则由管理员或仓库员维护。`
  };
});
const activeTab = ref("alerts");
const alertLoading = ref(false);
const ruleLoading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentRuleId = ref(null);
const alertRows = ref([]);
const ruleRows = ref([]);
const alertTotal = ref(0);
const ruleTotal = ref(0);
const warehouseOptions = ref([]);
const skuOptions = ref([]);

const alertQuery = reactive({
  keyword: "",
  alertType: "",
  alertLevel: "",
  pageNo: 1,
  pageSize: 10
});

const ruleQuery = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  warehouseId: null,
  skuId: null,
  minQty: 0,
  safeQty: 0,
  maxQty: 0,
  status: 1,
  remark: ""
});

const alertTypeLabelMap = {
  LOW: "低库存",
  HIGH: "超储"
};

const alertLevelTypeMap = {
  CRITICAL: "danger",
  WARN: "warning",
  INFO: "info"
};

const alertLevelLabelMap = {
  CRITICAL: "紧急",
  WARN: "预警",
  INFO: "提示",
  NORMAL: "正常"
};

const alertSummary = computed(() => {
  const rows = alertRows.value || [];
  return {
    pageTotal: rows.length,
    lowCount: rows.filter((item) => item.alertType === "LOW").length,
    highCount: rows.filter((item) => item.alertType === "HIGH").length,
    criticalCount: rows.filter((item) => item.alertLevel === "CRITICAL").length,
    warnCount: rows.filter((item) => item.alertLevel === "WARN").length,
    infoCount: rows.filter((item) => item.alertLevel === "INFO").length
  };
});

function resetForm() {
  form.warehouseId = null;
  form.skuId = null;
  form.minQty = 0;
  form.safeQty = 0;
  form.maxQty = 0;
  form.status = 1;
  form.remark = "";
}

function buildPayload() {
  return {
    warehouseId: form.warehouseId,
    skuId: form.skuId,
    minQty: form.minQty,
    safeQty: form.safeQty,
    maxQty: form.maxQty,
    status: form.status,
    remark: form.remark
  };
}

function validateForm() {
  if (!form.warehouseId || !form.skuId) {
    ElMessage.warning("请选择仓库和SKU");
    return false;
  }
  if (form.minQty > form.safeQty || form.safeQty > form.maxQty) {
    ElMessage.warning("阈值需满足 预警下限 <= 安全库存 <= 预警上限");
    return false;
  }
  return true;
}

async function loadBaseOptions() {
  try {
    const [warehouseList, skuPage] = await Promise.all([
      warehouseOptionsApi(),
      skuPageApi({ pageNo: 1, pageSize: 300, keyword: "" })
    ]);
    warehouseOptions.value = warehouseList || [];
    skuOptions.value = (skuPage.records || []).filter((item) => item.status === 1);
  } catch (error) {
    ElMessage.error(error?.message || "基础选项加载失败");
  }
}

// 加载预警结果列表，优先保证“可查可演示”。
async function loadAlerts() {
  alertLoading.value = true;
  try {
    const params = {
      keyword: alertQuery.keyword,
      pageNo: alertQuery.pageNo,
      pageSize: alertQuery.pageSize
    };
    if (alertQuery.alertType) {
      params.alertType = alertQuery.alertType;
    }
    if (alertQuery.alertLevel) {
      params.alertLevel = alertQuery.alertLevel;
    }
    const data = await inventoryAlertPageApi(params);
    alertRows.value = data.records || [];
    alertTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "预警列表加载失败");
  } finally {
    alertLoading.value = false;
  }
}

// 规则列表用于维护阈值配置，是 M5 的输入面。
async function loadRules() {
  ruleLoading.value = true;
  try {
    const data = await inventoryAlertRulePageApi(ruleQuery);
    ruleRows.value = data.records || [];
    ruleTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "预警规则加载失败");
  } finally {
    ruleLoading.value = false;
  }
}

async function loadData() {
  if (activeTab.value === "rules") {
    if (!canManageRules.value) {
      activeTab.value = "alerts";
      await loadAlerts();
      return;
    }
    if (!warehouseOptions.value.length || !skuOptions.value.length) {
      await loadBaseOptions();
    }
    await loadRules();
    return;
  }
  await loadAlerts();
}

/**
 * 看板快捷入口筛选：
 * - 支持按级别/类型直达预警列表；
 * - 支持切换到预警规则页（若当前角色具备权限）；
 * - 默认重置未传入条件，防止历史筛选干扰。
 */
async function applyQuickFilter(preset = {}) {
  const targetTab = preset.activeTab || "alerts";
  if (targetTab === "rules" && canManageRules.value) {
    activeTab.value = "rules";
    ruleQuery.keyword = typeof preset.keyword === "string" ? preset.keyword : "";
    ruleQuery.pageNo = 1;
    if (!warehouseOptions.value.length || !skuOptions.value.length) {
      await loadBaseOptions();
    }
    await loadRules();
    return;
  }

  activeTab.value = "alerts";
  alertQuery.keyword = typeof preset.keyword === "string" ? preset.keyword : "";
  alertQuery.alertType = preset.alertType || "";
  alertQuery.alertLevel = preset.alertLevel || "";
  alertQuery.pageNo = 1;
  await loadAlerts();
}

function alertTypeLabel(type) {
  return alertTypeLabelMap[type] || type || "-";
}

function alertLevelTag(level) {
  return alertLevelTypeMap[level] || "info";
}

function alertLevelLabel(level) {
  return alertLevelLabelMap[level] || level || "-";
}

function alertTypeTagType(type) {
  if (type === "LOW") return "warning";
  if (type === "HIGH") return "primary";
  return "info";
}

function alertRiskGap(row) {
  const currentQty = Number(row?.currentQty || 0);
  if (row?.alertType === "LOW") {
    const safeQty = Number(row?.safeQty || 0);
    return Math.max(0, safeQty - currentQty);
  }
  if (row?.alertType === "HIGH") {
    const maxQty = Number(row?.maxQty || 0);
    return Math.max(0, currentQty - maxQty);
  }
  return 0;
}

function alertActionText(row) {
  const gap = alertRiskGap(row);
  if (row?.alertType === "LOW") {
    return `建议补货 +${gap}`;
  }
  if (row?.alertType === "HIGH") {
    return `建议去化 -${gap}`;
  }
  return "-";
}

async function toggleTypeFilter(type) {
  alertQuery.alertType = alertQuery.alertType === type ? "" : type;
  alertQuery.pageNo = 1;
  await loadAlerts();
}

async function toggleLevelFilter(level) {
  alertQuery.alertLevel = alertQuery.alertLevel === level ? "" : level;
  alertQuery.pageNo = 1;
  await loadAlerts();
}

async function clearQuickFilters() {
  alertQuery.alertType = "";
  alertQuery.alertLevel = "";
  alertQuery.pageNo = 1;
  await loadAlerts();
}

function alertGapText(row) {
  const gap = alertRiskGap(row);
  if (row?.alertType === "LOW") {
    return `低于安全 ${gap}`;
  }
  if (row?.alertType === "HIGH") {
    return `超上限 ${gap}`;
  }
  return "-";
}

function alertRowClassName({ row }) {
  const classes = [];
  if (row?.alertType === "LOW") {
    classes.push("row-type-low");
  }
  if (row?.alertType === "HIGH") {
    classes.push("row-type-high");
  }
  if (row?.alertLevel === "CRITICAL") {
    classes.push("row-level-critical");
  }
  if (row?.alertLevel === "WARN") {
    classes.push("row-level-warn");
  }
  if (row?.alertLevel === "INFO") {
    classes.push("row-level-info");
  }
  return classes.join(" ");
}

function openCreate() {
  if (!canManageRules.value) {
    return;
  }
  isEdit.value = false;
  currentRuleId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row) {
  if (!canManageRules.value) {
    return;
  }
  isEdit.value = true;
  currentRuleId.value = row.id;
  form.warehouseId = row.warehouseId;
  form.skuId = row.skuId;
  form.minQty = row.minQty;
  form.safeQty = row.safeQty;
  form.maxQty = row.maxQty;
  form.status = row.status;
  form.remark = row.remark || "";
  dialogVisible.value = true;
}

async function saveRule() {
  if (!canManageRules.value) {
    return;
  }
  if (!validateForm()) return;
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await inventoryAlertRuleUpdateApi(currentRuleId.value, payload);
      ElMessage.success("预警规则更新成功");
    } else {
      await inventoryAlertRuleCreateApi(payload);
      ElMessage.success("预警规则新增成功");
    }
    dialogVisible.value = false;
    await loadRules();
    if (activeTab.value === "alerts") {
      await loadAlerts();
    }
  } catch (error) {
    ElMessage.error(error?.message || "规则保存失败");
  } finally {
    saving.value = false;
  }
}

async function onTabChange() {
  if (activeTab.value === "rules" && !canManageRules.value) {
    activeTab.value = "alerts";
    await loadAlerts();
    return;
  }
  await loadData();
}

defineExpose({ loadData, applyQuickFilter });
</script>

<template>
  <div class="panel">
    <el-alert
      title="M5 说明：本页用于维护安全库存阈值并查看缺货/低库存/超储预警。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <el-alert
      :title="ruleAccessHint.title"
      :type="ruleAccessHint.type"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="预警列表" name="alerts">
        <div class="alert-overview">
          <div class="overview-card compact-card total">
            <div class="compact-title">预警总数</div>
            <div class="compact-inline">
              <span class="compact-main">{{ alertTotal }}</span>
              <el-button link type="primary" @click="clearQuickFilters">清空筛选</el-button>
            </div>
          </div>
          <div class="overview-card compact-card">
            <div class="compact-title">类型分布</div>
            <div class="compact-inline">
              <button
                class="mini-filter mini-low"
                :class="{ 'is-active': alertQuery.alertType === 'LOW' }"
                @click="toggleTypeFilter('LOW')"
              >
                低库存 <span class="mini-filter-count">{{ alertSummary.lowCount }}</span>
              </button>
              <button
                class="mini-filter mini-high"
                :class="{ 'is-active': alertQuery.alertType === 'HIGH' }"
                @click="toggleTypeFilter('HIGH')"
              >
                超储 <span class="mini-filter-count">{{ alertSummary.highCount }}</span>
              </button>
            </div>
          </div>
          <div class="overview-card compact-card">
            <div class="compact-title">级别分布</div>
            <div class="compact-inline">
              <button
                class="mini-filter mini-critical"
                :class="{ 'is-active': alertQuery.alertLevel === 'CRITICAL' }"
                @click="toggleLevelFilter('CRITICAL')"
              >
                紧急 <span class="mini-filter-count">{{ alertSummary.criticalCount }}</span>
              </button>
              <button
                class="mini-filter mini-warn"
                :class="{ 'is-active': alertQuery.alertLevel === 'WARN' }"
                @click="toggleLevelFilter('WARN')"
              >
                预警 <span class="mini-filter-count">{{ alertSummary.warnCount }}</span>
              </button>
              <button
                class="mini-filter mini-info"
                :class="{ 'is-active': alertQuery.alertLevel === 'INFO' }"
                @click="toggleLevelFilter('INFO')"
              >
                提示 <span class="mini-filter-count">{{ alertSummary.infoCount }}</span>
              </button>
            </div>
          </div>
        </div>
        <div class="toolbar">
          <el-input v-model="alertQuery.keyword" clearable placeholder="按仓库/SKU搜索" class="wms-query-input-md" />
          <el-select v-model="alertQuery.alertType" clearable placeholder="预警类型" class="wms-query-select">
            <el-option value="LOW" label="低库存" />
            <el-option value="HIGH" label="超储" />
          </el-select>
          <el-select v-model="alertQuery.alertLevel" clearable placeholder="预警级别" class="wms-query-select">
            <el-option value="CRITICAL" label="紧急" />
            <el-option value="WARN" label="预警" />
            <el-option value="INFO" label="提示" />
          </el-select>
          <el-button type="primary" @click="alertQuery.pageNo = 1; loadAlerts()">查询</el-button>
        </div>
        <el-table v-loading="alertLoading" :data="alertRows" border size="small" :row-class-name="alertRowClassName">
          <el-table-column prop="warehouseName" label="仓库" min-width="96" show-overflow-tooltip />
          <el-table-column prop="skuCode" label="SKU编码" min-width="100" show-overflow-tooltip />
          <el-table-column prop="skuName" label="SKU名称" min-width="108" show-overflow-tooltip />
          <el-table-column prop="currentQty" label="当前库存" min-width="72" />
          <el-table-column prop="minQty" label="预警下限" min-width="72" />
          <el-table-column prop="safeQty" label="安全库存" min-width="72" />
          <el-table-column prop="maxQty" label="预警上限" min-width="72" />
          <el-table-column label="预警类型" min-width="80">
            <template #default="{ row }">
              <el-tag :type="alertTypeTagType(row.alertType)" effect="dark">
                {{ alertTypeLabel(row.alertType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="级别" min-width="80">
            <template #default="{ row }">
              <el-tag :type="alertLevelTag(row.alertLevel)" :effect="row.alertLevel === 'CRITICAL' ? 'dark' : 'plain'">
                {{ alertLevelLabel(row.alertLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="偏差值" min-width="88">
            <template #default="{ row }">
              <span class="gap-text" :class="row.alertType === 'LOW' ? 'gap-low' : 'gap-high'">
                {{ alertGapText(row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="风险提示" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="risk-text" :class="row.alertType === 'LOW' ? 'risk-low' : 'risk-high'">
                {{ alertActionText(row) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="alertQuery.pageNo"
            v-model:page-size="alertQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="alertTotal"
            @change="loadAlerts"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="canManageRules" label="预警规则" name="rules">
        <div class="toolbar">
          <el-input v-model="ruleQuery.keyword" clearable placeholder="按仓库/SKU搜索规则" class="wms-query-input-md" />
          <el-button type="primary" @click="ruleQuery.pageNo = 1; loadRules()">查询</el-button>
          <el-button @click="openCreate">新增规则</el-button>
        </div>
        <el-table v-loading="ruleLoading" :data="ruleRows" border size="small">
          <el-table-column prop="warehouseName" label="仓库" min-width="100" show-overflow-tooltip />
          <el-table-column prop="skuCode" label="SKU编码" min-width="104" show-overflow-tooltip />
          <el-table-column prop="skuName" label="SKU名称" min-width="118" show-overflow-tooltip />
          <el-table-column prop="minQty" label="预警下限" min-width="76" />
          <el-table-column prop="safeQty" label="安全库存" min-width="76" />
          <el-table-column prop="maxQty" label="预警上限" min-width="76" />
          <el-table-column label="状态" min-width="84">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
          <el-table-column label="操作" width="104" fixed="right">
            <template #default="{ row }">
              <el-button size="small" class="op-btn op-edit" @click="openEdit(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="ruleQuery.pageNo"
            v-model:page-size="ruleQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="ruleTotal"
            @change="loadRules"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑预警规则' : '新增预警规则'" width="720px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="仓库" required>
              <el-select v-model="form.warehouseId" style="width: 100%" placeholder="请选择仓库">
                <el-option
                  v-for="item in warehouseOptions"
                  :key="item.id"
                  :label="`${item.warehouseCode} - ${item.warehouseName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SKU" required>
              <el-select v-model="form.skuId" filterable style="width: 100%" placeholder="请选择SKU">
                <el-option
                  v-for="item in skuOptions"
                  :key="item.id"
                  :label="`${item.skuCode} - ${item.skuName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="预警下限">
              <el-input-number v-model="form.minQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="安全库存">
              <el-input-number v-model="form.safeQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预警上限">
              <el-input-number v-model="form.maxQty" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  --color-low-main: #b54708;
  --color-low-bg: #fff8eb;
  --color-low-border: #fed7aa;
  --color-high-main: #175cd3;
  --color-high-bg: #eff6ff;
  --color-high-border: #bfdbfe;
  --color-critical-main: #b42318;
  --color-critical-bg: #ffe8ea;
  --color-critical-border: #fda4af;
  --color-warn-main: #0f766e;
  --color-warn-bg: #ecfeff;
  --color-warn-border: #99f6e4;
  --color-info-main: #475467;
  --color-info-bg: #f8fafc;
  --color-info-border: #cbd5e1;
}

.alert-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.overview-card {
  border-radius: 10px;
  padding: 8px 10px;
  border: 1px solid #e6e9ef;
  background: #fff;
}

.overview-card.total {
  background: linear-gradient(135deg, #f8fafc, #eef2ff);
  border-color: #dbe4ff;
}

.compact-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 54px;
}

.compact-title {
  font-size: 12px;
  color: #526071;
  white-space: nowrap;
}

.compact-main {
  font-size: 26px;
  font-weight: 700;
  color: #101828;
  line-height: 1;
}

.compact-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mini-filter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 10px;
  min-height: 30px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #f2f4f7;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  font: inherit;
  line-height: 1;
  color: #344054;
  transition: all 0.15s ease;
}

.mini-filter:focus-visible {
  outline: 2px solid #98a2b3;
  outline-offset: 1px;
  border-radius: 8px;
}

.mini-filter-count {
  margin-left: 6px;
  font-weight: 800;
}

.mini-filter.is-active {
  box-shadow: inset 0 0 0 1px currentColor;
}

.mini-low {
  background: #fff3e0;
  color: var(--color-low-main);
  border-color: #fed7aa;
}

.mini-high {
  background: #eaf2ff;
  color: var(--color-high-main);
  border-color: #bfdbfe;
}

.mini-critical {
  background: #ffe9ec;
  color: var(--color-critical-main);
  border-color: #f7b0b7;
}

.mini-warn {
  background: #e8f9f7;
  color: var(--color-warn-main);
  border-color: #99f6e4;
}

.mini-info {
  background: #eef2f6;
  color: var(--color-info-main);
  border-color: #cbd5e1;
}

.mini-low.is-active {
  background: #ffd9b0;
  color: #8a2f08;
  border-color: #f4b37d;
}

.mini-high.is-active {
  background: #cfe2ff;
  color: #0f4fb6;
  border-color: #8eb7ff;
}

.mini-critical.is-active {
  background: #ffcfd6;
  color: #912018;
  border-color: #f08a97;
}

.mini-warn.is-active {
  background: #c8f3ee;
  color: #0b635c;
  border-color: #72dccf;
}

.mini-info.is-active {
  background: #dbe1ea;
  color: #344054;
  border-color: #b7c3d3;
}

.gap-text {
  font-weight: 600;
}

.gap-low {
  color: var(--color-low-main);
}

.gap-high {
  color: var(--color-high-main);
}

.risk-text {
  font-weight: 600;
}

.risk-low {
  color: var(--color-low-main);
}

.risk-high {
  color: var(--color-high-main);
}

:deep(.el-table .row-level-critical td) {
  background: #fff7f7;
}

:deep(.el-table .row-level-warn td) {
  background: #f0fdfa;
}

:deep(.el-table .row-level-info td) {
  background: #f8fafc;
}

:deep(.el-table .row-type-low td:first-child) {
  border-left: 4px solid var(--color-low-main);
}

:deep(.el-table .row-type-high td:first-child) {
  border-left: 4px solid var(--color-high-main);
}

:deep(.el-table .row-level-critical td:first-child) {
  box-shadow: inset 4px 0 0 0 var(--color-critical-main);
}

@media (max-width: 980px) {
  .alert-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 580px) {
  .alert-overview {
    grid-template-columns: 1fr;
  }

  .compact-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .compact-inline {
    justify-content: flex-start;
  }
}
</style>


