<!--
  @file 速览索引
  @summary 采购入库页面，负责入库单分页、详情、新增编辑、提交与确认，是采购到货进入库存的入口。
  @core 1. 展示入库单列表与详情
  @core 2. 支持入库单新增与编辑
  @core 3. 支持提交与确认流转
  @core 4. 联动仓库、SKU、供应商基础数据
  @entry 先看：loadData、save、submit、confirm、openDetail
  @deps 关键依赖：api/inbound.js、api/warehouse.js、api/sku.js、api/supplier.js
  @state 关键状态：query、rows、detail、form、dialogVisible
  @risk 高风险修改点：单据状态流转、明细表单字段、确认后库存写入口径
  @link 相关文件：前端/src/api/inbound.js、后端/src/main/java/com/wms/backend/inbound/service/InboundService.java
-->
<script setup>
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  inboundConfirmApi,
  inboundCreateApi,
  inboundDeleteApi,
  inboundDetailApi,
  inboundPageApi,
  inboundSubmitApi,
  inboundUpdateApi
} from "../api/inbound";
import { supplierPageApi } from "../api/supplier";
import { warehouseOptionsApi } from "../api/warehouse";
import { skuPageApi } from "../api/sku";

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const dialogVisible = ref(false);
const detailVisible = ref(false);
const isEdit = ref(false);
const currentId = ref(null);
const detailData = ref(null);
const supplierOptions = ref([]);
const warehouseOptions = ref([]);
const skuOptions = ref([]);

const query = reactive({
  keyword: "",
  status: null,
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  supplierId: null,
  warehouseId: null,
  remark: "",
  items: []
});

const statusMap = {
  0: { text: "草稿", type: "info" },
  1: { text: "已提交", type: "warning" },
  2: { text: "已入库", type: "success" }
};

function formatStatus(status) {
  return statusMap[status]?.text || "未知";
}

function statusType(status) {
  return statusMap[status]?.type || "info";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
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

function resetForm() {
  form.supplierId = null;
  form.warehouseId = null;
  form.remark = "";
  form.items = [{ skuId: null, planQty: 1, receivedQty: 1, remark: "" }];
}

function createPayload() {
  return {
    supplierId: form.supplierId,
    warehouseId: form.warehouseId,
    remark: form.remark,
    items: form.items.map((item) => ({
      skuId: item.skuId,
      planQty: item.planQty,
      receivedQty: item.receivedQty,
      remark: item.remark
    }))
  };
}

function validateForm() {
  if (!form.supplierId || !form.warehouseId) {
    ElMessage.warning("请选择供应商和仓库");
    return false;
  }
  if (!form.items.length) {
    ElMessage.warning("请至少添加一条明细");
    return false;
  }
  for (const item of form.items) {
    if (!item.skuId || !item.planQty || item.planQty < 1) {
      ElMessage.warning("请完整填写明细（SKU 和计划数量）");
      return false;
    }
    if (item.receivedQty == null || item.receivedQty < 0) {
      ElMessage.warning("实收数量不能小于 0");
      return false;
    }
  }
  return true;
}

async function loadBaseOptions() {
  try {
    const [supplierPage, warehouseList, skuPage] = await Promise.all([
      supplierPageApi({ pageNo: 1, pageSize: 200, keyword: "" }),
      warehouseOptionsApi(),
      skuPageApi({ pageNo: 1, pageSize: 200, keyword: "" })
    ]);
    supplierOptions.value = (supplierPage.records || []).filter((item) => item.status === 1);
    warehouseOptions.value = warehouseList || [];
    skuOptions.value = (skuPage.records || []).filter((item) => item.status === 1);
  } catch (error) {
    ElMessage.error(error?.message || "基础选项加载失败");
  }
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
    const data = await inboundPageApi(params);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "入库单列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function openCreate() {
  isEdit.value = false;
  currentId.value = null;
  resetForm();
  dialogVisible.value = true;
  if (!supplierOptions.value.length || !warehouseOptions.value.length || !skuOptions.value.length) {
    await loadBaseOptions();
  }
}

async function openEdit(row) {
  if (row.status !== 0) {
    ElMessage.warning("仅草稿状态可以编辑");
    return;
  }
  isEdit.value = true;
  currentId.value = row.id;
  if (!supplierOptions.value.length || !warehouseOptions.value.length || !skuOptions.value.length) {
    await loadBaseOptions();
  }
  try {
    const detail = await inboundDetailApi(row.id);
    form.supplierId = detail.supplierId;
    form.warehouseId = detail.warehouseId;
    form.remark = detail.remark || "";
    form.items = (detail.items || []).map((item) => ({
      skuId: item.skuId,
      planQty: item.planQty,
      receivedQty: item.receivedQty ?? item.planQty,
      remark: item.remark || ""
    }));
    if (!form.items.length) {
      form.items = [{ skuId: null, planQty: 1, receivedQty: 1, remark: "" }];
    }
    dialogVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || "入库单详情加载失败");
  }
}

function addItem() {
  form.items.push({
    skuId: null,
    planQty: 1,
    receivedQty: 1,
    remark: ""
  });
}

function removeItem(index) {
  if (form.items.length <= 1) {
    ElMessage.warning("至少保留一条明细");
    return;
  }
  form.items.splice(index, 1);
}

async function save() {
  if (!validateForm()) return;
  saving.value = true;
  try {
    const payload = createPayload();
    if (isEdit.value) {
      await inboundUpdateApi(currentId.value, payload);
      ElMessage.success("入库单更新成功");
    } else {
      await inboundCreateApi(payload);
      ElMessage.success("入库单创建成功");
    }
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "入库单保存失败");
  } finally {
    saving.value = false;
  }
}

async function openDetail(row) {
  try {
    detailData.value = await inboundDetailApi(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || "详情加载失败");
  }
}

async function submitRow(row) {
  try {
    let items = [];
    try {
      const detail = await inboundDetailApi(row.id);
      items = detail.items || [];
    } catch (_error) {
      // 详情读取失败时不阻断提交确认，仅展示基础信息。
    }
    const lineCount = items.length;
    const planTotal = items.reduce((sum, item) => sum + (item.planQty || 0), 0);
    const receivedTotal = items.reduce((sum, item) => sum + (item.receivedQty || 0), 0);
    const topItems = items.slice(0, 3)
      .map((item) => `<li>${escapeHtml(item.skuCode)} / ${escapeHtml(item.skuName)}：<span style="color:#b91c1c;font-weight:700">${item.planQty || 0}</span></li>`)
      .join("");
    const moreText = items.length > 3 ? `<div style="color:#6b7280">其余 ${items.length - 3} 条请在详情中查看</div>` : "";
    const html = `
      <div style="line-height:1.7">
        <div>入库单号：<span style="color:#b91c1c;font-weight:700">${escapeHtml(row.inboundNo)}</span></div>
        <div>明细条数：<span style="color:#b91c1c;font-weight:700">${lineCount}</span></div>
        <div>计划总数：<span style="color:#b91c1c;font-weight:700">${planTotal}</span></div>
        <div>当前实收总数：<span style="color:#b91c1c;font-weight:700">${receivedTotal}</span></div>
        <ul style="margin:6px 0 4px 18px;padding:0">${topItems || "<li>无明细</li>"}</ul>
        ${moreText}
        <div style="color:#7c2d12">提交后将不能继续编辑明细。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await inboundSubmitApi(row.id);
    ElMessage.success("提交成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "提交失败");
    }
  }
}

async function confirmRow(row) {
  try {
    let items = [];
    try {
      const detail = await inboundDetailApi(row.id);
      items = detail.items || [];
    } catch (_error) {
      // 详情读取失败时不阻断确认弹窗，仅展示基础信息。
    }
    const lineCount = items.length;
    const effectiveTotal = items.reduce((sum, item) => {
      const qty = item.receivedQty && item.receivedQty > 0 ? item.receivedQty : item.planQty || 0;
      return sum + qty;
    }, 0);
    const topItems = items.slice(0, 3)
      .map((item) => {
        const qty = item.receivedQty && item.receivedQty > 0 ? item.receivedQty : item.planQty || 0;
        return `<li>${escapeHtml(item.skuCode)} / ${escapeHtml(item.skuName)}：<span style="color:#b91c1c;font-weight:700">${qty}</span></li>`;
      })
      .join("");
    const moreText = items.length > 3 ? `<div style="color:#6b7280">其余 ${items.length - 3} 条请在详情中查看</div>` : "";
    const html = `
      <div style="line-height:1.7">
        <div>入库单号：<span style="color:#b91c1c;font-weight:700">${escapeHtml(row.inboundNo)}</span></div>
        <div>入库明细条数：<span style="color:#b91c1c;font-weight:700">${lineCount}</span></div>
        <div>本次入库总数：<span style="color:#b91c1c;font-weight:700">${effectiveTotal}</span></div>
        <ul style="margin:6px 0 4px 18px;padding:0">${topItems || "<li>无明细</li>"}</ul>
        ${moreText}
        <div style="color:#7c2d12">确认后系统将自动增加库存并写入流水。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await inboundConfirmApi(row.id);
    ElMessage.success("入库确认成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "入库确认失败");
    }
  }
}

async function deleteRow(row) {
  if (row.status !== 0) {
    ElMessage.warning("仅草稿状态可以删除");
    return;
  }
  try {
    // 删除前给出关键信息，避免误删草稿单据。
    let lineCount = "-";
    try {
      const detail = await inboundDetailApi(row.id);
      lineCount = (detail.items || []).length;
    } catch (_error) {
      // 删除确认不依赖详情成功，详情失败时仍允许用户取消或继续。
    }
    await ElMessageBox.confirm(
      `入库单号：${row.inboundNo}\n明细条数：${lineCount}\n删除后不可恢复，是否继续？`,
      "删除确认",
      { type: "warning", confirmButtonText: "确认删除", cancelButtonText: "取消" }
    );
    await inboundDeleteApi(row.id);
    ElMessage.success("删除成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "删除失败");
    }
  }
}

defineExpose({
  loadData
});
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="按入库单号/供应商搜索" class="wms-query-input-md" />
      <el-select v-model="query.status" clearable placeholder="状态" class="wms-query-select">
        <el-option :value="0" label="草稿" />
        <el-option :value="1" label="已提交" />
        <el-option :value="2" label="已入库" />
      </el-select>
      <el-button type="primary" @click="query.pageNo = 1; loadData()">查询</el-button>
      <el-button @click="openCreate">新建入库单</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="inboundNo" label="入库单号" min-width="170" />
      <el-table-column prop="supplierName" label="供应商" min-width="130" />
      <el-table-column prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ formatStatus(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="150">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" class="op-btn op-view" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 0" size="small" class="op-btn op-edit" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 0" size="small" class="op-btn op-submit" @click="submitRow(row)">提交</el-button>
          <el-button v-if="row.status === 0" size="small" class="op-btn op-delete" @click="deleteRow(row)">删除</el-button>
          <el-button v-if="row.status === 1" size="small" class="op-btn op-confirm" @click="confirmRow(row)">确认入库</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑入库单' : '新建入库单'" width="980px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="供应商" required>
              <el-select v-model="form.supplierId" filterable placeholder="请选择供应商" style="width: 100%">
                <el-option
                  v-for="item in supplierOptions"
                  :key="item.id"
                  :label="`${item.supplierCode} - ${item.supplierName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" required>
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
                <el-option
                  v-for="item in warehouseOptions"
                  :key="item.id"
                  :label="`${item.warehouseCode} - ${item.warehouseName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="item-header">
          <span>入库明细</span>
          <el-button size="small" @click="addItem">新增明细</el-button>
        </div>
        <el-table :data="form.items" border>
          <el-table-column label="SKU" min-width="260">
            <template #default="{ row }">
              <el-select v-model="row.skuId" filterable placeholder="选择商品" style="width: 100%">
                <el-option
                  v-for="item in skuOptions"
                  :key="item.id"
                  :label="`${item.skuCode} - ${item.skuName}`"
                  :value="item.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="计划数量" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.planQty" :min="1" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="实收数量" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.receivedQty" :min="0" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="170">
            <template #default="{ row }">
              <el-input v-model="row.remark" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="入库单详情" width="900px">
      <template v-if="detailData">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="入库单号">{{ detailData.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detailData.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detailData.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ formatStatus(detailData.status) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detailData.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detailData.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detailData.items || []" border style="margin-top: 12px">
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="140" />
          <el-table-column prop="planQty" label="计划数量" width="100" />
          <el-table-column prop="receivedQty" label="实收数量" width="100" />
          <el-table-column prop="remark" label="备注" min-width="140" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>




