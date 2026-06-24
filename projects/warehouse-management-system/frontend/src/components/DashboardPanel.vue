<!--
  @file 速览索引
  @summary 治理看板组件，负责聚合治理摘要、治理总览、治理入口、作业待办与近期动态。
  @core 1. 拉取管理员/采购员/仓库员首页所需统计数据
  @core 2. 生成治理摘要条、治理总览卡片和快捷入口
  @core 3. 提供从首页跳转到预警、补货、用户治理等页面的能力
  @core 4. 展示最近预警、最近补货计划与待办任务
  @entry 先看：adminSummaryItems、adminGovernanceItems、quickActions、loadData
  @deps 关键依赖：inventory/replenishment/user/role/warehouse 等 API
  @state 关键状态：metrics、recentAlerts、recentReplenishments、replenishmentTrendRows
  @risk 高风险修改点：统计口径、首页文案层级、快捷跳转 preset、管理员与采购员展示差异
  @link 相关文件：前端/src/App.vue、前端/src/components/InventoryAlertPanel.vue、前端/src/components/ReplenishmentPanel.vue
-->
<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { inboundPageApi } from "../api/inbound";
import { inventoryAlertPageApi, inventoryStockPageApi } from "../api/inventory";
import { locationPageApi } from "../api/location";
import { outboundPageApi } from "../api/outbound";
import { putawayPageApi } from "../api/putaway";
import { replenishmentPageApi } from "../api/replenishment";
import { rolePageApi } from "../api/role";
import { skuPageApi } from "../api/sku";
import { stocktakePageApi } from "../api/stocktake";
import { supplierPageApi } from "../api/supplier";
import { userPageApi } from "../api/user";
import { warehousePageApi } from "../api/warehouse";

const props = defineProps({
  roleCode: {
    type: String,
    default: ""
  },
  menus: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["jump-menu"]);

const loading = ref(false);
const updatedAt = ref("");
const partialErrorText = ref("");
const recentReplenishments = ref([]);
const recentAlerts = ref([]);
const alertTopRows = ref([]);
const replenishmentTrendRows = ref([]);
const todoScrollRef = ref(null);

const metrics = reactive({
  userTotal: null,
  roleTotal: null,
  warehouseTotal: null,
  locationTotal: null,
  skuTotal: null,
  supplierTotal: null,
  stockRecordTotal: null,
  alertTotal: null,
  alertCritical: null,
  alertWarn: null,
  alertInfo: null,
  replenishmentTotal: null,
  replenishmentToConfirm: null,
  replenishmentToPurchase: null,
  replenishmentConverted: null,
  inboundSubmitted: null,
  putawaySubmitted: null,
  outboundSubmitted: null,
  stocktakeSubmitted: null
});

const replenishmentStatusMap = {
  0: { text: "待确认", type: "info" },
  1: { text: "待转采购", type: "warning" },
  2: { text: "已生成采购草稿", type: "success" }
};

const alertLevelMap = {
  CRITICAL: { text: "紧急", type: "danger" },
  WARN: { text: "预警", type: "warning" },
  INFO: { text: "提示", type: "info" }
};

const menuSet = computed(() => new Set(props.menus || []));
const isAdmin = computed(() => props.roleCode === "ADMIN");
const isPurchaser = computed(() => props.roleCode === "PURCHASER");
const isWarehouse = computed(() => props.roleCode === "WAREHOUSE");
const replenishmentTrendMax = computed(() => {
  return Math.max(...(replenishmentTrendRows.value || []).map((item) => Number(item.total || 0)), 0);
});

// 管理员看板采用“治理 + 执行”双视角：
// 第一层展示账号与主数据规模，第二层展示风险与待处理任务。
const adminOverviewCards = computed(() => {
  return [
    {
      key: "userTotal",
      title: "用户数量",
      desc: "系统可登录账号",
      menuCode: "user",
      preset: null,
      tone: "blue",
      visible: hasMenu("user")
    },
    {
      key: "roleTotal",
      title: "角色数量",
      desc: "权限模型规模",
      menuCode: "user",
      preset: null,
      tone: "slate",
      visible: hasMenu("user")
    },
    {
      key: "warehouseTotal",
      title: "仓库数量",
      desc: "可用仓储节点",
      menuCode: "warehouse",
      preset: null,
      tone: "teal",
      visible: hasMenu("warehouse")
    },
    {
      key: "locationTotal",
      title: "库位数量",
      desc: "可用作业库位",
      menuCode: "location",
      preset: null,
      tone: "cyan",
      visible: hasMenu("location")
    },
    {
      key: "alertCritical",
      title: "紧急预警",
      desc: "需优先处置风险",
      menuCode: "inventory-alert",
      preset: { alertLevel: "CRITICAL" },
      tone: "danger",
      visible: hasMenu("inventory-alert")
    },
    {
      key: "replenishmentToConfirm",
      title: "待确认补货",
      desc: "建议等待业务确认",
      menuCode: "replenishment",
      preset: { status: 0 },
      tone: "warning",
      visible: hasMenu("replenishment")
    }
  ].filter((card) => card.visible);
});

const baseOverviewCards = computed(() => {
  return [
    {
      key: "warehouseTotal",
      title: "仓库数量",
      desc: "可用仓储节点",
      menuCode: "warehouse",
      preset: null,
      tone: "blue",
      visible: hasMenu("warehouse")
    },
    {
      key: "skuTotal",
      title: "SKU数量",
      desc: "在管商品主数据",
      menuCode: "sku",
      preset: null,
      tone: "teal",
      visible: hasMenu("sku")
    },
    {
      key: "supplierTotal",
      title: "供应商数量",
      desc: "有效供应来源",
      menuCode: "supplier",
      preset: null,
      tone: "cyan",
      visible: hasMenu("supplier")
    },
    {
      key: "stockRecordTotal",
      title: "库存记录",
      desc: "仓库-SKU库存条目",
      menuCode: "inventory",
      preset: null,
      tone: "slate",
      visible: hasMenu("inventory")
    },
    {
      key: "alertTotal",
      title: "预警总数",
      desc: "库存风险需处理",
      menuCode: "inventory-alert",
      preset: null,
      tone: "orange",
      visible: hasMenu("inventory-alert")
    },
    {
      key: "replenishmentTotal",
      title: "补货计划总数",
      desc: "智能补货计划池",
      menuCode: "replenishment",
      preset: null,
      tone: "purple",
      visible: hasMenu("replenishment")
    }
  ].filter((card) => card.visible);
});

const purchaserOverviewCards = computed(() => {
  return [
    {
      key: "replenishmentToConfirm",
      title: "待确认建议",
      desc: "需要核对并确认",
      menuCode: "replenishment",
      preset: { status: 0 },
      tone: "warning",
      visible: hasMenu("replenishment")
    },
    {
      key: "replenishmentToPurchase",
      title: "待转采购",
      desc: "建议生成采购草稿",
      menuCode: "replenishment",
      preset: { status: 1 },
      tone: "blue",
      visible: hasMenu("replenishment")
    },
    {
      key: "replenishmentConverted",
      title: "已转采购草稿",
      desc: "已进入采购执行",
      menuCode: "replenishment",
      preset: { status: 2 },
      tone: "purple",
      visible: hasMenu("replenishment")
    },
    {
      key: "alertCritical",
      title: "紧急预警",
      desc: "高优先级库存风险",
      menuCode: "inventory-alert",
      preset: { alertLevel: "CRITICAL" },
      tone: "danger",
      visible: hasMenu("inventory-alert")
    },
    {
      key: "alertWarn",
      title: "一般预警",
      desc: "建议当日复核",
      menuCode: "inventory-alert",
      preset: { alertLevel: "WARN" },
      tone: "orange",
      visible: hasMenu("inventory-alert")
    },
    {
      key: "supplierTotal",
      title: "可用供应商",
      desc: "采购资源池规模",
      menuCode: "supplier",
      preset: null,
      tone: "cyan",
      visible: hasMenu("supplier")
    }
  ].filter((card) => card.visible);
});

const overviewCards = computed(() => {
  if (isPurchaser.value) return purchaserOverviewCards.value;
  if (isAdmin.value) return adminOverviewCards.value;
  return baseOverviewCards.value;
});

function resolveRiskLevel(value, highThreshold, mediumThreshold) {
  if (value === null || value === undefined) {
    return { text: "待刷新", type: "info" };
  }
  const safeValue = Number(value || 0);
  if (safeValue >= highThreshold) {
    return { text: "高风险", type: "danger" };
  }
  if (safeValue >= mediumThreshold) {
    return { text: "需关注", type: "warning" };
  }
  return { text: "可控", type: "success" };
}

// 管理员“监管总览”口径：优先展示风险与积压，而不是直接进入执行动作。
const adminGovernanceItems = computed(() => {
  const items = [];
  if (hasMenu("inventory-alert")) {
    const level = resolveRiskLevel(metrics.alertCritical, 1, 1);
    items.push({
      key: "alertCritical",
      title: "紧急库存预警",
      count: metrics.alertCritical,
      desc: "用于监管高优先级库存风险，建议每日优先巡检。",
      menuCode: "inventory-alert",
      preset: { alertLevel: "CRITICAL" },
      levelText: level.text,
      levelType: level.type
    });
  }
  if (hasMenu("replenishment")) {
    const level = resolveRiskLevel(metrics.replenishmentToConfirm, 8, 3);
    items.push({
      key: "replenishmentToConfirm",
      title: "补货待确认",
      count: metrics.replenishmentToConfirm,
      desc: "用于监管建议积压，避免补货决策阻塞采购流程。",
      menuCode: "replenishment",
      preset: { status: 0 },
      levelText: level.text,
      levelType: level.type
    });
  }
  if (hasMenu("outbound")) {
    const level = resolveRiskLevel(metrics.outboundSubmitted, 10, 4);
    items.push({
      key: "outboundSubmitted",
      title: "出库待处理",
      count: metrics.outboundSubmitted,
      desc: "用于监管履约链路压力，避免出库延迟影响交付。",
      menuCode: "outbound",
      preset: null,
      levelText: level.text,
      levelType: level.type
    });
  }
  if (hasMenu("stocktake")) {
    const level = resolveRiskLevel(metrics.stocktakeSubmitted, 4, 1);
    items.push({
      key: "stocktakeSubmitted",
      title: "盘点待处理",
      count: metrics.stocktakeSubmitted,
      desc: "用于监管账实一致性风险，确保库存治理闭环。",
      menuCode: "stocktake",
      preset: null,
      levelText: level.text,
      levelType: level.type
    });
  }
  return items;
});

const quickActionTitle = computed(() => {
  if (isAdmin.value) return "治理入口";
  return "快捷操作";
});

const todoItems = computed(() => {
  const items = [];
  if (hasMenu("inventory-alert")) {
    items.push({
      label: "紧急库存预警",
      count: metrics.alertCritical,
      tip: "优先排查缺货/超储风险",
      menuCode: "inventory-alert",
      preset: { alertLevel: "CRITICAL" },
      tone: "danger"
    });
  }
  if (hasMenu("replenishment")) {
    items.push({
      label: "补货待确认",
      count: metrics.replenishmentToConfirm,
      tip: "可重算或确认建议明细",
      menuCode: "replenishment",
      preset: { status: 0 },
      tone: "warning"
    });
    items.push({
      label: "补货待转采购",
      count: metrics.replenishmentToPurchase,
      tip: "建议转为采购草稿继续流转",
      menuCode: "replenishment",
      preset: { status: 1 },
      tone: "primary"
    });
  }
  if (isPurchaser.value) {
    if (hasMenu("supplier")) {
      items.push({
        label: "供应商可用数量",
        count: metrics.supplierTotal,
        tip: "供应侧异常时可快速切换货源",
        menuCode: "supplier",
        preset: null,
        tone: "primary"
      });
    }
    return items
      .filter((item) => item.count !== null)
      .sort((a, b) => Number(b.count || 0) - Number(a.count || 0));
  }
  if (hasMenu("inbound")) {
    items.push({
      label: "入库待处理",
      count: metrics.inboundSubmitted,
      tip: "已提交未入库单据",
      menuCode: "inbound",
      preset: null,
      tone: "primary"
    });
  }
  if (hasMenu("putaway")) {
    items.push({
      label: "上架待处理",
      count: metrics.putawaySubmitted,
      tip: "已提交未完成上架",
      menuCode: "putaway",
      preset: null,
      tone: "primary"
    });
  }
  if (hasMenu("outbound")) {
    items.push({
      label: "出库待处理",
      count: metrics.outboundSubmitted,
      tip: "已提交待执行出库",
      menuCode: "outbound",
      preset: null,
      tone: "primary"
    });
  }
  if (hasMenu("stocktake")) {
    items.push({
      label: "盘点待处理",
      count: metrics.stocktakeSubmitted,
      tip: "已提交待完成盘点",
      menuCode: "stocktake",
      preset: null,
      tone: "primary"
    });
  }
  return items
    .filter((item) => item.count !== null)
    .sort((a, b) => Number(b.count || 0) - Number(a.count || 0));
});

const quickActions = computed(() => {
  const actions = [];

  if (isPurchaser.value) {
    if (hasMenu("replenishment")) {
      actions.push({
        label: "处理待确认",
        menuCode: "replenishment",
        count: metrics.replenishmentToConfirm,
        preset: { status: 0 },
        tone: "warning"
      });
      actions.push({
        label: "处理待转采购",
        menuCode: "replenishment",
        count: metrics.replenishmentToPurchase,
        preset: { status: 1 },
        tone: "primary"
      });
      actions.push({
        label: "查看补货计划",
        menuCode: "replenishment",
        count: metrics.replenishmentTotal,
        preset: null,
        tone: "slate"
      });
    }
    if (hasMenu("inventory-alert")) {
      actions.push({
        label: "查看紧急预警",
        menuCode: "inventory-alert",
        count: metrics.alertCritical,
        preset: { alertLevel: "CRITICAL" },
        tone: "danger"
      });
    }
    if (hasMenu("supplier")) {
      actions.push({
        label: "维护供应商",
        menuCode: "supplier",
        count: metrics.supplierTotal,
        preset: null,
        tone: "teal"
      });
    }
    return actions;
  }

  if (isAdmin.value) {
    if (hasMenu("user")) {
      actions.push({
        label: "用户与角色治理",
        menuCode: "user",
        count: metrics.userTotal,
        preset: null,
        tone: "primary"
      });
    }
    if (hasMenu("inventory-alert")) {
      actions.push({
        label: "查看紧急预警",
        menuCode: "inventory-alert",
        count: metrics.alertCritical,
        preset: { alertLevel: "CRITICAL" },
        tone: "danger"
      });
    }
    if (hasMenu("replenishment")) {
      actions.push({
        label: "查看补货待确认",
        menuCode: "replenishment",
        count: metrics.replenishmentToConfirm,
        preset: { status: 0 },
        tone: "warning"
      });
    }
    return actions;
  }

  return actions;
});

// 仓库员“执行待办”采用 3x2 紧凑布局，最多展示 6 条，避免出现第三行孤卡造成留白。
const displayTodoItems = computed(() => {
  const items = todoItems.value || [];
  if (!isWarehouse.value) {
    return items;
  }
  return items.slice(0, 6);
});

const hasTodoOverflow = computed(() => {
  if (!isWarehouse.value) return false;
  return (todoItems.value?.length || 0) > (displayTodoItems.value?.length || 0);
});

const replenishmentPipeline = computed(() => {
  if (!hasMenu("replenishment")) return [];
  return [
    { label: "待确认", value: metrics.replenishmentToConfirm, tone: "warning" },
    { label: "待转采购", value: metrics.replenishmentToPurchase, tone: "primary" },
    { label: "已生成采购草稿", value: metrics.replenishmentConverted, tone: "success" }
  ];
});

const dashboardTitle = computed(() => {
  if (isAdmin.value) return "治理看板";
  return isPurchaser.value ? "采购运营看板" : "运营看板";
});

const dashboardSubtitle = computed(() => {
  if (isAdmin.value) {
    return "围绕账号权限、主数据规模与风险任务，帮助管理员快速完成系统治理和关键调度。";
  }
  if (isPurchaser.value) {
    return "围绕补货建议、库存预警和供应商管理，优先展示采购员当日最需要处理的任务。";
  }
  return "聚合库存预警、补货计划和单据待办，支持你快速定位当天优先事项。";
});

// 顶部治理摘要条：把最关键的治理信号压缩成一排状态项，方便管理员 3 秒内完成扫描。
const adminSummaryItems = computed(() => {
  if (!isAdmin.value) return [];
  return [
    {
      key: "alertCritical",
      label: "紧急预警",
      value: metrics.alertCritical,
      menuCode: "inventory-alert",
      preset: { alertLevel: "CRITICAL" },
      tone: resolveRiskLevel(metrics.alertCritical, 1, 1).type
    },
    {
      key: "replenishmentToConfirm",
      label: "待确认补货",
      value: metrics.replenishmentToConfirm,
      menuCode: "replenishment",
      preset: { status: 0 },
      tone: resolveRiskLevel(metrics.replenishmentToConfirm, 8, 3).type
    },
    {
      key: "outboundSubmitted",
      label: "出库积压",
      value: metrics.outboundSubmitted,
      menuCode: "outbound",
      preset: null,
      tone: resolveRiskLevel(metrics.outboundSubmitted, 10, 4).type
    },
    {
      key: "stocktakeSubmitted",
      label: "盘点积压",
      value: metrics.stocktakeSubmitted,
      menuCode: "stocktake",
      preset: null,
      tone: resolveRiskLevel(metrics.stocktakeSubmitted, 4, 1).type
    }
  ].filter((item) => item.value !== null);
});

function hasMenu(code) {
  return menuSet.value.has(code);
}

function resetDashboardState() {
  Object.keys(metrics).forEach((key) => {
    metrics[key] = null;
  });
  recentReplenishments.value = [];
  recentAlerts.value = [];
  alertTopRows.value = [];
  replenishmentTrendRows.value = [];
  partialErrorText.value = "";
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function displayMetric(value) {
  return value === null ? "-" : value;
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

function formatReplenishmentStatus(status) {
  return replenishmentStatusMap[status]?.text || "未知";
}

function replenishmentStatusType(status) {
  return replenishmentStatusMap[status]?.type || "info";
}

function formatAlertLevel(level) {
  return alertLevelMap[level]?.text || level || "-";
}

function alertLevelType(level) {
  return alertLevelMap[level]?.type || "info";
}

function formatAlertType(type) {
  if (type === "LOW") return "低库存";
  if (type === "HIGH") return "超储";
  return type || "-";
}

function alertRiskText(row) {
  const currentQty = toNumber(row?.currentQty);
  const safeQty = toNumber(row?.safeQty);
  const maxQty = toNumber(row?.maxQty);
  if (row?.alertType === "LOW") {
    const gap = Math.max(0, safeQty - currentQty);
    return `低于安全库存 ${gap}`;
  }
  if (row?.alertType === "HIGH") {
    const gap = Math.max(0, currentQty - maxQty);
    return `超出上限 ${gap}`;
  }
  return "-";
}

function alertRiskGapValue(row) {
  if (row?.alertType === "LOW") {
    return Math.max(0, toNumber(row.safeQty) - toNumber(row.currentQty));
  }
  if (row?.alertType === "HIGH") {
    return Math.max(0, toNumber(row.currentQty) - toNumber(row.maxQty));
  }
  return 0;
}

function alertLevelWeight(level) {
  if (level === "CRITICAL") return 3000;
  if (level === "WARN") return 2000;
  if (level === "INFO") return 1000;
  return 0;
}

// 高风险排序口径：先按级别（紧急>预警>提示），再按偏差量降序，便于采购员优先处理关键风险。
function buildAlertTopRows(rows) {
  return (rows || [])
    .map((item) => {
      const gap = alertRiskGapValue(item);
      return {
        ...item,
        riskGap: gap,
        riskScore: alertLevelWeight(item.alertLevel) + gap
      };
    })
    .sort((a, b) => b.riskScore - a.riskScore)
    .slice(0, 5);
}

function normalizeDateKey(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n) => String(n).padStart(2, "0");
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  return `${year}-${month}-${day}`;
}

function buildRecentDateKeys(days = 7) {
  const now = new Date();
  const keys = [];
  for (let i = days - 1; i >= 0; i -= 1) {
    const d = new Date(now);
    d.setHours(0, 0, 0, 0);
    d.setDate(now.getDate() - i);
    keys.push(normalizeDateKey(d));
  }
  return keys;
}

function shortDateLabel(dateKey) {
  if (!dateKey || dateKey.length < 10) return "-";
  return dateKey.slice(5);
}

function formatDelta(value) {
  if (value === null || value === undefined) return "-";
  if (value > 0) return `+${value}`;
  return `${value}`;
}

function deltaClass(value) {
  if (value > 0) return "delta-up";
  if (value < 0) return "delta-down";
  return "delta-flat";
}

function trendBarHeight(value) {
  const max = replenishmentTrendMax.value;
  if (max <= 0) return 8;
  return Math.max(8, Math.round((Number(value || 0) / max) * 100));
}

// 近7天趋势口径：按“生成时间”分桶，统计每天计划总数及三态分布，并附带较前一天变化。
function buildReplenishmentTrendRows(records, days = 7) {
  const dateKeys = buildRecentDateKeys(days);
  const buckets = {};
  dateKeys.forEach((key) => {
    buckets[key] = {
      dateKey: key,
      displayDate: shortDateLabel(key),
      total: 0,
      toConfirm: 0,
      toPurchase: 0,
      converted: 0,
      toConfirmDelta: null,
      toPurchaseDelta: null,
      convertedDelta: null
    };
  });

  (records || []).forEach((item) => {
    const key = normalizeDateKey(item?.generatedAt);
    if (!key || !buckets[key]) return;
    buckets[key].total += 1;
    if (Number(item?.status) === 0) buckets[key].toConfirm += 1;
    if (Number(item?.status) === 1) buckets[key].toPurchase += 1;
    if (Number(item?.status) === 2) buckets[key].converted += 1;
  });

  const rows = dateKeys.map((key) => buckets[key]);
  return rows.map((row, index) => {
    if (index === 0) {
      return row;
    }
    const prev = rows[index - 1];
    return {
      ...row,
      toConfirmDelta: row.toConfirm - prev.toConfirm,
      toPurchaseDelta: row.toPurchase - prev.toPurchase,
      convertedDelta: row.converted - prev.converted
    };
  });
}

function jumpToMenu(code, preset = null) {
  if (!code || !hasMenu(code)) {
    ElMessage.warning("当前账号无该模块访问权限");
    return;
  }
  emit("jump-menu", { menuCode: code, preset: preset || null });
}

function scrollTodo(direction) {
  const container = todoScrollRef.value;
  if (!container) return;
  const step = Math.max(260, Math.floor(container.clientWidth * 0.75));
  container.scrollBy({
    left: direction * step,
    behavior: "smooth"
  });
}

// 鼠标滚轮默认是上下滚动，这里把纵向滚轮转成横向滑动，提升待办卡片浏览体验。
function handleTodoWheel(event) {
  const container = todoScrollRef.value;
  if (!container || container.scrollWidth <= container.clientWidth) return;
  if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
  event.preventDefault();
  container.scrollLeft += event.deltaY;
}

function jumpToAlertBySku(row) {
  if (!row) return;
  const preset = {
    keyword: row.skuCode || "",
    alertLevel: row.alertLevel || "",
    alertType: row.alertType || ""
  };
  jumpToMenu("inventory-alert", preset);
}

async function queryTotal(requestFn, params) {
  const data = await requestFn(params);
  return toNumber(data?.total);
}

async function loadData(showSuccess = false) {
  loading.value = true;
  resetDashboardState();
  const failedParts = [];
  try {
    const tasks = [];

    // 基础主数据指标：便于快速判断数据规模是否齐备。
    if (hasMenu("user")) {
      tasks.push(
        queryTotal(userPageApi, { pageNo: 1, pageSize: 1, keyword: "" })
          .then((total) => {
            metrics.userTotal = total;
          })
          .catch(() => failedParts.push("用户指标"))
      );
    }

    if (hasMenu("user")) {
      tasks.push(
        queryTotal(rolePageApi, { pageNo: 1, pageSize: 1, keyword: "" })
          .then((total) => {
            metrics.roleTotal = total;
          })
          .catch(() => failedParts.push("角色指标"))
      );
    }

    if (hasMenu("warehouse")) {
      tasks.push(
        queryTotal(warehousePageApi, { pageNo: 1, pageSize: 1, keyword: "" })
          .then((total) => {
            metrics.warehouseTotal = total;
          })
          .catch(() => failedParts.push("仓库指标"))
      );
    }

    if (hasMenu("sku")) {
      tasks.push(
        queryTotal(skuPageApi, { pageNo: 1, pageSize: 1, keyword: "" })
          .then((total) => {
            metrics.skuTotal = total;
          })
          .catch(() => failedParts.push("SKU指标"))
      );
    }

    if (hasMenu("supplier")) {
      tasks.push(
        queryTotal(supplierPageApi, { pageNo: 1, pageSize: 1, keyword: "" })
          .then((total) => {
            metrics.supplierTotal = total;
          })
          .catch(() => failedParts.push("供应商指标"))
      );
    }

    if (hasMenu("location")) {
      tasks.push(
        queryTotal(locationPageApi, { pageNo: 1, pageSize: 1, keyword: "", warehouseId: null })
          .then((total) => {
            metrics.locationTotal = total;
          })
          .catch(() => failedParts.push("库位指标"))
      );
    }

    if (hasMenu("inventory")) {
      tasks.push(
        queryTotal(inventoryStockPageApi, { pageNo: 1, pageSize: 1, keyword: "" })
          .then((total) => {
            metrics.stockRecordTotal = total;
          })
          .catch(() => failedParts.push("库存指标"))
      );
    }

    // 预警看板：按级别统计总量，支撑风险优先级展示。
    if (hasMenu("inventory-alert")) {
      tasks.push(
        Promise.all([
          queryTotal(inventoryAlertPageApi, { pageNo: 1, pageSize: 1, keyword: "" }),
          queryTotal(inventoryAlertPageApi, { pageNo: 1, pageSize: 1, keyword: "", alertLevel: "CRITICAL" }),
          queryTotal(inventoryAlertPageApi, { pageNo: 1, pageSize: 1, keyword: "", alertLevel: "WARN" }),
          queryTotal(inventoryAlertPageApi, { pageNo: 1, pageSize: 1, keyword: "", alertLevel: "INFO" }),
          inventoryAlertPageApi({ pageNo: 1, pageSize: 6, keyword: "" }),
          inventoryAlertPageApi({ pageNo: 1, pageSize: 100, keyword: "" })
        ])
          .then(([total, critical, warn, info, recent, topCandidates]) => {
            metrics.alertTotal = total;
            metrics.alertCritical = critical;
            metrics.alertWarn = warn;
            metrics.alertInfo = info;
            recentAlerts.value = recent?.records || [];
            alertTopRows.value = buildAlertTopRows(topCandidates?.records || []);
          })
          .catch(() => failedParts.push("库存预警"))
      );
    }

    // 补货看板：围绕三态流转展示执行进度。
    if (hasMenu("replenishment")) {
      tasks.push(
        Promise.all([
          queryTotal(replenishmentPageApi, { pageNo: 1, pageSize: 1, keyword: "" }),
          queryTotal(replenishmentPageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 0 }),
          queryTotal(replenishmentPageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 1 }),
          queryTotal(replenishmentPageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 2 }),
          replenishmentPageApi({ pageNo: 1, pageSize: 6, keyword: "" }),
          replenishmentPageApi({ pageNo: 1, pageSize: 500, keyword: "" })
        ])
          .then(([total, toConfirm, toPurchase, converted, recent, trendCandidates]) => {
            metrics.replenishmentTotal = total;
            metrics.replenishmentToConfirm = toConfirm;
            metrics.replenishmentToPurchase = toPurchase;
            metrics.replenishmentConverted = converted;
            recentReplenishments.value = recent?.records || [];
            replenishmentTrendRows.value = buildReplenishmentTrendRows(trendCandidates?.records || []);
          })
          .catch(() => failedParts.push("智能补货"))
      );
    }

    // 单据待办：统一按“已提交待处理”口径统计。
    if (hasMenu("inbound")) {
      tasks.push(
        queryTotal(inboundPageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 1 })
          .then((total) => {
            metrics.inboundSubmitted = total;
          })
          .catch(() => failedParts.push("入库待办"))
      );
    }

    if (hasMenu("putaway")) {
      tasks.push(
        queryTotal(putawayPageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 1 })
          .then((total) => {
            metrics.putawaySubmitted = total;
          })
          .catch(() => failedParts.push("上架待办"))
      );
    }

    if (hasMenu("outbound")) {
      tasks.push(
        queryTotal(outboundPageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 1 })
          .then((total) => {
            metrics.outboundSubmitted = total;
          })
          .catch(() => failedParts.push("出库待办"))
      );
    }

    if (hasMenu("stocktake")) {
      tasks.push(
        queryTotal(stocktakePageApi, { pageNo: 1, pageSize: 1, keyword: "", status: 1 })
          .then((total) => {
            metrics.stocktakeSubmitted = total;
          })
          .catch(() => failedParts.push("盘点待办"))
      );
    }

    await Promise.all(tasks);
    updatedAt.value = formatTime(new Date());
    if (failedParts.length) {
      partialErrorText.value = `部分看板数据加载失败：${[...new Set(failedParts)].join("、")}。可点击“刷新看板”重试。`;
    } else if (showSuccess) {
      ElMessage.success("看板刷新成功");
    }
  } catch (error) {
    partialErrorText.value = "看板加载失败，请稍后重试。";
    if (showSuccess) {
      ElMessage.error(error?.message || "看板加载失败");
    }
  } finally {
    loading.value = false;
  }
}

defineExpose({ loadData });
</script>

<template>
  <div class="panel dashboard" v-loading="loading">
    <div class="dashboard-header">
      <div>
        <h3 class="dashboard-title">{{ dashboardTitle }}</h3>
        <p class="dashboard-subtitle">{{ dashboardSubtitle }}</p>
      </div>
      <div class="dashboard-actions">
        <div class="updated-at">最后刷新：{{ updatedAt || "-" }}</div>
        <el-button type="primary" @click="loadData(true)">刷新看板</el-button>
      </div>
    </div>
    <div v-if="isAdmin && adminSummaryItems.length" class="govern-summary">
      <div class="govern-summary-label">今日治理摘要</div>
      <div class="govern-summary-items">
        <button
          v-for="item in adminSummaryItems"
          :key="item.key"
          class="govern-summary-pill"
          :class="`summary-${item.tone}`"
          @click="jumpToMenu(item.menuCode, item.preset)"
        >
          <span class="govern-summary-name">{{ item.label }}</span>
          <span class="govern-summary-value">{{ displayMetric(item.value) }}</span>
        </button>
      </div>
    </div>

    <el-alert v-if="partialErrorText" type="warning" :closable="false" show-icon :title="partialErrorText" />

    <template v-if="isAdmin && adminGovernanceItems.length">
      <div class="section-title">治理总览</div>
      <div class="govern-grid">
        <div
          v-for="item in adminGovernanceItems"
          :key="item.key"
          class="govern-card"
          :class="`govern-${item.levelType}`"
          @click="jumpToMenu(item.menuCode, item.preset)"
        >
          <div class="govern-head">
            <div class="govern-title">{{ item.title }}</div>
            <el-tag size="small" :type="item.levelType">{{ item.levelText }}</el-tag>
          </div>
          <div class="govern-value">{{ displayMetric(item.count) }}</div>
          <div class="govern-desc">{{ item.desc }}</div>
          <div class="govern-action">查看明细</div>
        </div>
      </div>
      <div class="govern-note">管理员首页默认聚焦治理信号，作业处理入口保留在下方次级区域。</div>
    </template>

    <template v-if="quickActions.length">
      <div class="section-title">{{ quickActionTitle }}</div>
      <div class="quick-grid">
        <button
          v-for="action in quickActions"
          :key="`${action.menuCode}-${action.label}`"
          class="quick-card"
          :class="`quick-${action.tone}`"
          @click="jumpToMenu(action.menuCode, action.preset)"
        >
          <span class="quick-label">{{ action.label }}</span>
          <span class="quick-count">{{ displayMetric(action.count) }}</span>
        </button>
      </div>
    </template>

    <template v-if="isPurchaser && replenishmentPipeline.length">
      <div class="section-title">采购执行流</div>
      <div class="pipeline-wrap">
        <div v-for="(step, index) in replenishmentPipeline" :key="step.label" class="pipeline-step" :class="`step-${step.tone}`">
          <div class="pipeline-index">{{ index + 1 }}</div>
          <div class="pipeline-body">
            <div class="pipeline-label">{{ step.label }}</div>
            <div class="pipeline-value">{{ displayMetric(step.value) }}</div>
          </div>
        </div>
      </div>
    </template>

    <template v-if="isPurchaser && hasMenu('replenishment')">
      <div class="section-title">近7天处理趋势</div>
      <div class="trend-card">
        <div class="trend-head">
          <div class="table-title">补货计划日趋势（按生成日）</div>
          <div class="trend-tip">用于观察待确认/待转采购是否在持续积压。</div>
        </div>
        <div class="trend-grid">
          <div v-for="row in replenishmentTrendRows" :key="row.dateKey" class="trend-item">
            <div class="trend-date">{{ row.displayDate }}</div>
            <div class="trend-bar-wrap">
              <div class="trend-bar" :style="{ height: `${trendBarHeight(row.total)}%` }"></div>
            </div>
            <div class="trend-total">计划 {{ row.total }}</div>
            <div class="trend-line">
              待确认 {{ row.toConfirm }}
              <span class="trend-delta" :class="deltaClass(row.toConfirmDelta)">({{ formatDelta(row.toConfirmDelta) }})</span>
            </div>
            <div class="trend-line">
              待转采购 {{ row.toPurchase }}
              <span class="trend-delta" :class="deltaClass(row.toPurchaseDelta)">({{ formatDelta(row.toPurchaseDelta) }})</span>
            </div>
            <div class="trend-line">
              已转草稿 {{ row.converted }}
              <span class="trend-delta" :class="deltaClass(row.convertedDelta)">({{ formatDelta(row.convertedDelta) }})</span>
            </div>
            <div class="trend-action">
              <el-button
                link
                type="primary"
                @click="jumpToMenu('replenishment', { generatedDateStart: row.dateKey, generatedDateEnd: row.dateKey })"
              >
                查看当日
              </el-button>
            </div>
          </div>
          <div v-if="!replenishmentTrendRows.length" class="todo-empty">暂无趋势数据。</div>
        </div>
      </div>
    </template>

    <div class="section-title">关键指标</div>
    <div class="metric-grid">
      <div
        v-for="card in overviewCards"
        :key="card.key"
        class="metric-card"
        :class="`tone-${card.tone}`"
        @click="jumpToMenu(card.menuCode, card.preset)"
      >
        <div class="metric-title">{{ card.title }}</div>
        <div class="metric-value">{{ displayMetric(metrics[card.key]) }}</div>
        <div class="metric-desc">{{ card.desc }}</div>
      </div>
    </div>

    <div class="section-header">
      <div class="section-title">{{ isAdmin ? "作业待办（次级）" : "执行待办" }}</div>
      <div v-if="displayTodoItems.length" class="todo-nav">
        <el-button text class="todo-nav-btn" @click="scrollTodo(-1)">向左</el-button>
        <el-button text class="todo-nav-btn" @click="scrollTodo(1)">向右</el-button>
      </div>
    </div>
    <div v-if="isAdmin" class="todo-subtitle">管理员可直接处理任务，但建议优先依据上方治理信号进行调度。</div>
    <div v-if="hasTodoOverflow" class="todo-subtitle">已按优先级展示前 6 项待办，可左右滑动查看。</div>
    <div ref="todoScrollRef" class="todo-scroll" @wheel="handleTodoWheel">
      <div v-if="isWarehouse" class="todo-grid todo-grid-warehouse todo-grid-horizontal">
        <div
          v-for="item in displayTodoItems"
          :key="item.label"
          class="todo-card todo-card-warehouse"
          :class="`todo-${item.tone}`"
        >
          <div class="todo-head">
            <div class="todo-label">{{ item.label }}</div>
            <div class="todo-count">{{ item.count }}</div>
          </div>
          <div class="todo-foot">
            <div class="todo-tip" :title="item.tip">{{ item.tip }}</div>
            <el-button size="small" class="todo-action-btn" @click="jumpToMenu(item.menuCode, item.preset)">
              立即处理
            </el-button>
          </div>
        </div>
        <div v-if="!displayTodoItems.length" class="todo-empty">
          当前账号暂无可统计的待办模块。
        </div>
      </div>
      <div v-else class="todo-grid todo-grid-horizontal">
        <div v-for="item in displayTodoItems" :key="item.label" class="todo-card" :class="`todo-${item.tone}`">
          <div class="todo-head">
            <div class="todo-label">{{ item.label }}</div>
            <div class="todo-count">{{ item.count }}</div>
          </div>
          <div class="todo-tip">{{ item.tip }}</div>
          <el-button size="small" class="todo-action-btn" @click="jumpToMenu(item.menuCode, item.preset)">
            立即处理
          </el-button>
        </div>
        <div v-if="!displayTodoItems.length" class="todo-empty">
          当前账号暂无可统计的待办模块。
        </div>
      </div>
    </div>

    <template v-if="isPurchaser && hasMenu('inventory-alert')">
      <div class="section-title">高风险SKU Top5</div>
      <div class="table-card">
        <div class="table-card-head">
          <div class="table-title">建议优先处理清单</div>
          <el-button size="small" @click="jumpToMenu('inventory-alert', { alertLevel: 'CRITICAL' })">
            去预警页处理
          </el-button>
        </div>
        <el-table :data="alertTopRows" border size="small" empty-text="暂无高风险SKU">
          <el-table-column type="index" label="排名" width="56" />
          <el-table-column label="SKU信息" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="jumpToAlertBySku(row)">
                {{ row.skuCode }} / {{ row.skuName }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="仓库" min-width="90" />
          <el-table-column label="级别" min-width="70">
            <template #default="{ row }">
              <el-tag :type="alertLevelType(row.alertLevel)">{{ formatAlertLevel(row.alertLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="偏差量" min-width="70">
            <template #default="{ row }">
              {{ row.riskGap }}
            </template>
          </el-table-column>
          <el-table-column label="建议" min-width="120">
            <template #default="{ row }">
              {{ alertRiskText(row) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="92">
            <template #default="{ row }">
              <el-button link type="primary" @click="jumpToAlertBySku(row)">定位预警</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <div class="section-title">最近动态</div>
    <div class="table-grid">
      <div class="table-card">
        <div class="table-card-head">
          <div class="table-title">最近补货计划</div>
          <el-button v-if="hasMenu('replenishment')" size="small" @click="jumpToMenu('replenishment')">
            进入智能补货
          </el-button>
        </div>
        <el-table :data="recentReplenishments" border size="small" empty-text="暂无补货计划">
          <el-table-column prop="planNo" label="计划号" min-width="120" />
          <el-table-column prop="warehouseName" label="仓库" min-width="90" />
          <el-table-column label="状态" min-width="132">
            <template #default="{ row }">
              <el-tag :type="replenishmentStatusType(row.status)">{{ formatReplenishmentStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生成时间" min-width="118">
            <template #default="{ row }">
              {{ formatTime(row.generatedAt) }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="table-card">
        <div class="table-card-head">
          <div class="table-title">最近库存预警</div>
          <el-button v-if="hasMenu('inventory-alert')" size="small" @click="jumpToMenu('inventory-alert')">
            进入库存预警
          </el-button>
        </div>
        <el-table :data="recentAlerts" border size="small" empty-text="暂无预警记录">
          <el-table-column label="仓库 / SKU" min-width="130">
            <template #default="{ row }">
              {{ row.warehouseName }} / {{ row.skuCode }}
            </template>
          </el-table-column>
          <el-table-column label="类型" min-width="70">
            <template #default="{ row }">
              {{ formatAlertType(row.alertType) }}
            </template>
          </el-table-column>
          <el-table-column label="级别" min-width="70">
            <template #default="{ row }">
              <el-tag :type="alertLevelType(row.alertLevel)">{{ formatAlertLevel(row.alertLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="风险提示" min-width="100">
            <template #default="{ row }">
              {{ alertRiskText(row) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbe7ff;
  border-radius: 10px;
  background: linear-gradient(135deg, #f3f7ff 0%, #f8fbff 100%);
}

.dashboard-title {
  margin: 0;
  font-size: 18px;
  color: #17335f;
}

.dashboard-subtitle {
  margin: 4px 0 0;
  color: #5a6b82;
  font-size: 13px;
}

.dashboard-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.govern-summary {
  margin-top: -2px;
  border: 1px solid #dbe7ff;
  border-radius: 10px;
  background: linear-gradient(135deg, #f7faff 0%, #ffffff 100%);
  padding: 8px 10px;
}

.govern-summary-label {
  font-size: 12px;
  color: #35527b;
  font-weight: 700;
}

.govern-summary-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.govern-summary-pill {
  border: 1px solid #dbe3ef;
  border-radius: 999px;
  background: #fff;
  padding: 6px 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font: inherit;
  color: #243b61;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.govern-summary-pill:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(16, 24, 40, 0.08);
}

.govern-summary-name {
  font-size: 12px;
  font-weight: 600;
}

.govern-summary-value {
  font-size: 16px;
  line-height: 1;
  font-weight: 700;
}

.summary-danger {
  border-color: #fecdd3;
  background: #fff5f7;
  color: #b42318;
}

.summary-warning {
  border-color: #fed7aa;
  background: #fff8f1;
  color: #b54708;
}

.summary-success {
  border-color: #bfe9c7;
  background: #f2fcf4;
  color: #027a48;
}

.summary-info {
  border-color: #dbe3ef;
  background: #f8fafc;
  color: #475467;
}

.updated-at {
  color: #667085;
  font-size: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #344054;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.todo-nav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.todo-nav-btn {
  font-size: 12px;
  color: #475467;
}

.todo-subtitle {
  margin-top: -6px;
  font-size: 12px;
  color: #667085;
}

.govern-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.govern-card {
  border: 1px solid #e7ebf2;
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.govern-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(16, 24, 40, 0.06);
}

.govern-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.govern-title {
  font-size: 13px;
  color: #344054;
  font-weight: 700;
}

.govern-value {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1;
  color: #101828;
  font-weight: 700;
}

.govern-desc {
  margin-top: 6px;
  font-size: 12px;
  color: #667085;
  min-height: 34px;
}

.govern-action {
  margin-top: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #2f66e3;
}

.govern-note {
  margin-top: -4px;
  color: #667085;
  font-size: 12px;
}

.govern-danger {
  border-color: #fecdd3;
  background: linear-gradient(160deg, #fff5f7 0%, #ffffff 100%);
}

.govern-warning {
  border-color: #fed7aa;
  background: linear-gradient(160deg, #fff9f2 0%, #ffffff 100%);
}

.govern-success {
  border-color: #bfe9c7;
  background: linear-gradient(160deg, #f3fcf5 0%, #ffffff 100%);
}

.govern-info {
  border-color: #dbe3ef;
  background: linear-gradient(160deg, #f8fafc 0%, #ffffff 100%);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.quick-card {
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: #101828;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.quick-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(16, 24, 40, 0.06);
}

.quick-label {
  font-size: 13px;
  font-weight: 600;
  color: #344054;
}

.quick-count {
  font-size: 20px;
  line-height: 1;
  font-weight: 700;
}

.quick-warning {
  border-color: #fed7aa;
  background: #fff8ef;
}

.quick-primary {
  border-color: #cfe0ff;
  background: #f4f8ff;
}

.quick-slate {
  border-color: #dbe3ef;
  background: #f8fafc;
}

.quick-danger {
  border-color: #fecdd3;
  background: #fff7f8;
}

.quick-teal {
  border-color: #bfece7;
  background: #effcf9;
}

.quick-cyan {
  border-color: #cfeaf9;
  background: #eef8ff;
}

.pipeline-wrap {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.pipeline-step {
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  background: #fff;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.pipeline-index {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  background: #eef2f6;
  color: #344054;
  flex-shrink: 0;
}

.pipeline-body {
  min-width: 0;
}

.pipeline-label {
  color: #667085;
  font-size: 12px;
}

.pipeline-value {
  margin-top: 2px;
  color: #101828;
  font-size: 22px;
  line-height: 1.1;
  font-weight: 700;
}

.step-warning {
  border-color: #fed7aa;
  background: #fffaf3;
}

.step-primary {
  border-color: #cfe0ff;
  background: #f5f8ff;
}

.step-success {
  border-color: #bfe9c7;
  background: #f1fbf3;
}

.trend-card {
  border: 1px solid #e7ebf2;
  border-radius: 10px;
  background: #fff;
  padding: 10px;
}

.trend-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}

.trend-tip {
  font-size: 12px;
  color: #667085;
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.trend-item {
  border: 1px solid #edf0f5;
  border-radius: 10px;
  background: #fbfcff;
  padding: 8px;
}

.trend-date {
  font-size: 12px;
  color: #475467;
  text-align: center;
}

.trend-bar-wrap {
  height: 54px;
  margin: 6px 0 4px;
  border-radius: 8px;
  background: #eef2f6;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  overflow: hidden;
}

.trend-bar {
  width: 60%;
  min-height: 6px;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #5b8cff, #2f66e3);
}

.trend-total {
  font-size: 12px;
  color: #344054;
  font-weight: 700;
  text-align: center;
  margin-bottom: 4px;
}

.trend-line {
  font-size: 11px;
  color: #475467;
  line-height: 1.5;
}

.trend-action {
  margin-top: 4px;
  display: flex;
  justify-content: flex-end;
}

.trend-delta {
  margin-left: 4px;
  font-weight: 700;
}

.delta-up {
  color: #b42318;
}

.delta-down {
  color: #027a48;
}

.delta-flat {
  color: #667085;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.metric-card {
  border-radius: 10px;
  padding: 12px;
  border: 1px solid #e7ebf2;
  background: #ffffff;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.metric-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(16, 24, 40, 0.06);
}

.metric-title {
  color: #667085;
  font-size: 12px;
}

.metric-value {
  margin-top: 4px;
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
  color: #101828;
}

.metric-desc {
  margin-top: 6px;
  font-size: 12px;
  color: #475467;
}

.tone-blue {
  border-color: #cfe0ff;
  background: linear-gradient(160deg, #f4f8ff 0%, #ffffff 100%);
}

.tone-teal {
  border-color: #bfece7;
  background: linear-gradient(160deg, #effcf9 0%, #ffffff 100%);
}

.tone-cyan {
  border-color: #cfeaf9;
  background: linear-gradient(160deg, #eef8ff 0%, #ffffff 100%);
}

.tone-slate {
  border-color: #dbe3ef;
  background: linear-gradient(160deg, #f6f8fc 0%, #ffffff 100%);
}

.tone-orange {
  border-color: #ffd5b0;
  background: linear-gradient(160deg, #fff7ed 0%, #ffffff 100%);
}

.tone-danger {
  border-color: #fecdd3;
  background: linear-gradient(160deg, #fff4f6 0%, #ffffff 100%);
}

.tone-purple {
  border-color: #d7ccff;
  background: linear-gradient(160deg, #f5f2ff 0%, #ffffff 100%);
}

.todo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
}

.todo-grid-warehouse {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.todo-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  scroll-behavior: smooth;
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.todo-scroll::-webkit-scrollbar {
  display: none;
}

.todo-grid-horizontal {
  display: flex;
  flex-wrap: nowrap;
  gap: 10px;
  width: max-content;
  min-width: 100%;
}

.todo-grid-horizontal .todo-card {
  width: 240px;
  flex: 0 0 240px;
}

.todo-grid-horizontal.todo-grid-warehouse .todo-card {
  width: 310px;
  flex-basis: 310px;
}

.todo-card {
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 116px;
}

.todo-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}

.todo-label {
  font-size: 13px;
  color: #344054;
  font-weight: 600;
}

.todo-count {
  font-size: 26px;
  line-height: 1;
  font-weight: 700;
  color: #101828;
}

.todo-tip {
  margin: 4px 0 6px;
  font-size: 12px;
  color: #667085;
  min-height: 30px;
}

.todo-card :deep(.el-button) {
  margin-top: auto;
  align-self: flex-start;
}

.todo-action-btn {
  height: 26px;
  border-radius: 999px;
  border: 1px solid #c9d9fb;
  background: #eff5ff;
  color: #2958c8;
  font-size: 12px;
  font-weight: 600;
  padding: 0 12px;
}

.todo-action-btn:hover {
  border-color: #9fbaf6;
  background: #e2edff;
  color: #1f46a6;
}

.todo-card-warehouse {
  min-height: auto;
  padding: 8px 10px;
  gap: 8px;
}

.todo-card-warehouse .todo-head {
  align-items: center;
}

.todo-card-warehouse .todo-count {
  font-size: 24px;
}

.todo-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.todo-card-warehouse .todo-tip {
  margin: 0;
  min-height: auto;
  line-height: 1.45;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.todo-card-warehouse :deep(.el-button) {
  margin-top: 0;
  min-height: 26px;
}

.todo-primary {
  border-color: #d0dffc;
}

.todo-warning {
  border-color: #fed7aa;
  background: #fffaf4;
}

.todo-danger {
  border-color: #fecdd3;
  background: #fff7f8;
}

.todo-empty {
  border: 1px dashed #d0d5dd;
  border-radius: 10px;
  color: #667085;
  background: #fcfcfd;
  padding: 16px;
  text-align: center;
}

.todo-grid-horizontal .todo-empty {
  min-width: 100%;
}

.table-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.table-card {
  border: 1px solid #e7ebf2;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.table-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 10px 0;
}

.table-title {
  font-size: 13px;
  color: #344054;
  font-weight: 700;
}

@media (max-width: 1200px) {
  .table-grid {
    grid-template-columns: 1fr;
  }

  .trend-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .todo-grid-warehouse {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dashboard-header {
    flex-direction: column;
    align-items: stretch;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-actions {
    align-items: flex-start;
  }

  .trend-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .todo-grid-warehouse {
    grid-template-columns: 1fr;
  }

  .todo-foot {
    justify-content: flex-start;
    gap: 10px;
    flex-wrap: wrap;
  }

  .todo-card-warehouse .todo-tip {
    white-space: normal;
    text-overflow: clip;
  }

  .todo-grid-horizontal .todo-card {
    width: 220px;
    flex-basis: 220px;
  }

  .todo-grid-horizontal.todo-grid-warehouse .todo-card {
    width: 260px;
    flex-basis: 260px;
  }
}
</style>
