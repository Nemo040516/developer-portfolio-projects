<!--
  @file 速览索引
  @summary 库存盘点页面，负责盘点单分页、详情、新增编辑、提交确认与账实修正流程展示。
  @core 1. 展示盘点单列表与详情
  @core 2. 支持盘点单新增与编辑
  @core 3. 支持提交与确认流转
  @core 4. 联动账实数量差异并完成库存修正
  @entry 先看：loadData、save、submit、confirm、openDetail
  @deps 关键依赖：api/stocktake.js、api/inventory.js、api/warehouse.js、api/sku.js
  @state 关键状态：query、rows、detail、form、dialogVisible
  @risk 高风险修改点：账实差异字段、确认后的库存修正、状态流转与详情联动
  @link 相关文件：前端/src/api/stocktake.js、后端/src/main/java/com/wms/backend/stocktake/service/StocktakeService.java
-->
<script setup>
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  stocktakeBookStocksApi,
  stocktakeConfirmApi,
  stocktakeCreateApi,
  stocktakeDeleteApi,
  stocktakeDetailApi,
  stocktakePageApi,
  stocktakeSubmitApi,
  stocktakeUpdateApi
} from "../api/stocktake";
import { skuPageApi } from "../api/sku";
import { warehouseOptionsApi } from "../api/warehouse";
import { locationPageApi } from "../api/location";

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const detailVisible = ref(false);
const isEdit = ref(false);
const currentId = ref(null);
const rows = ref([]);
const total = ref(0);
const detailData = ref(null);
const warehouseOptions = ref([]);
const skuOptions = ref([]);
const locationOptions = ref([]);
const bookStockMap = ref({});

const query = reactive({
  keyword: "",
  status: null,
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  warehouseId: null,
  scopeType: "BY_WAREHOUSE",
  remark: "",
  items: []
});

const statusMap = {
  0: { text: "草稿", type: "info" },
  1: { text: "已提交", type: "warning" },
  2: { text: "已完成", type: "success" }
};

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

function resetForm() {
  form.warehouseId = null;
  form.scopeType = "BY_WAREHOUSE";
  form.remark = "";
  form.items = [{ skuId: null, locationId: null, countQty: 0, reason: "", remark: "" }];
  bookStockMap.value = {};
}

function createPayload() {
  return {
    warehouseId: form.warehouseId,
    scopeType: form.scopeType || "BY_WAREHOUSE",
    remark: form.remark || null,
    items: form.items.map((item) => ({
      skuId: item.skuId,
      locationId: item.locationId,
      countQty: item.countQty,
      reason: item.reason || null,
      remark: item.remark || null
    }))
  };
}

function validateForm() {
  if (!form.warehouseId) {
    ElMessage.warning("请选择仓库");
    return false;
  }
  if (!form.items.length) {
    ElMessage.warning("请至少添加一条盘点明细");
    return false;
  }
  for (const item of form.items) {
    if (!item.skuId || !item.locationId || item.countQty == null || item.countQty < 0) {
      ElMessage.warning("请完整填写明细（SKU、库位、实盘数量）");
      return false;
    }
  }
  return true;
}

function locationOptionsForRow(row) {
  if (!form.warehouseId) return [];
  const base = (locationOptions.value || []).filter((item) => item.status === 1 && item.warehouseId === form.warehouseId);
  if (!row?.skuId) {
    return base.map((item) => ({
      ...item,
      displayLabel: `${item.locationCode} (${item.areaName || "未分区"})`
    }));
  }
  const mapKey = row.skuId;
  const dynamicOptions = bookStockMap.value[mapKey] || [];
  if (!dynamicOptions.length) {
    return base.map((item) => ({
      ...item,
      displayLabel: `${item.locationCode} (${item.areaName || "未分区"})`
    }));
  }
  return dynamicOptions.map((item) => ({
    id: item.locationId,
    displayLabel: `${item.locationCode} (${item.areaName || "未分区"}) - 账面:${item.bookQty}`
  }));
}

async function loadBookStocksBySku(skuId) {
  if (!form.warehouseId || !skuId) return;
  try {
    const data = await stocktakeBookStocksApi({ warehouseId: form.warehouseId, skuId });
    bookStockMap.value = { ...bookStockMap.value, [skuId]: data || [] };
  } catch (error) {
    ElMessage.error(error?.message || "账面库存加载失败");
  }
}

async function loadBaseOptions() {
  try {
    const [warehouseList, skuPage, locationPage] = await Promise.all([
      warehouseOptionsApi(),
      skuPageApi({ pageNo: 1, pageSize: 300, keyword: "" }),
      locationPageApi({ pageNo: 1, pageSize: 500, keyword: "" })
    ]);
    warehouseOptions.value = warehouseList || [];
    skuOptions.value = (skuPage.records || []).filter((item) => item.status === 1);
    locationOptions.value = locationPage.records || [];
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
    const data = await stocktakePageApi(params);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "盘点单列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function openCreate() {
  isEdit.value = false;
  currentId.value = null;
  resetForm();
  if (!warehouseOptions.value.length || !skuOptions.value.length || !locationOptions.value.length) {
    await loadBaseOptions();
  }
  dialogVisible.value = true;
}

async function openEdit(row) {
  if (row.status !== 0) {
    ElMessage.warning("仅草稿状态可以编辑");
    return;
  }
  isEdit.value = true;
  currentId.value = row.id;
  if (!warehouseOptions.value.length || !skuOptions.value.length || !locationOptions.value.length) {
    await loadBaseOptions();
  }
  try {
    const detail = await stocktakeDetailApi(row.id);
    form.warehouseId = detail.warehouseId;
    form.scopeType = detail.scopeType || "BY_WAREHOUSE";
    form.remark = detail.remark || "";
    form.items = (detail.items || []).map((item) => ({
      skuId: item.skuId,
      locationId: item.locationId,
      countQty: item.countQty,
      reason: item.reason || "",
      remark: item.remark || ""
    }));
    if (!form.items.length) {
      form.items = [{ skuId: null, locationId: null, countQty: 0, reason: "", remark: "" }];
    }
    const skuIds = [...new Set(form.items.map((item) => item.skuId).filter(Boolean))];
    await Promise.all(skuIds.map((skuId) => loadBookStocksBySku(skuId)));
    dialogVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || "盘点单详情加载失败");
  }
}

function addItem() {
  form.items.push({ skuId: null, locationId: null, countQty: 0, reason: "", remark: "" });
}

function onWarehouseChange() {
  bookStockMap.value = {};
  for (const item of form.items) {
    item.locationId = null;
  }
}

async function onSkuChange(row) {
  row.locationId = null;
  if (!row?.skuId) return;
  await loadBookStocksBySku(row.skuId);
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
      await stocktakeUpdateApi(currentId.value, payload);
      ElMessage.success("盘点单更新成功");
    } else {
      await stocktakeCreateApi(payload);
      ElMessage.success("盘点单创建成功");
    }
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "盘点单保存失败");
  } finally {
    saving.value = false;
  }
}

async function openDetail(row) {
  try {
    detailData.value = await stocktakeDetailApi(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || "详情加载失败");
  }
}

async function submitRow(row) {
  try {
    const detail = await stocktakeDetailApi(row.id);
    const lineCount = (detail.items || []).length;
    await ElMessageBox.confirm(
      `盘点单号：${row.stocktakeNo}\n明细条数：${lineCount}\n提交后将不能继续编辑明细。`,
      "二次确认",
      { type: "warning", confirmButtonText: "确认执行", cancelButtonText: "取消" }
    );
    await stocktakeSubmitApi(row.id);
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
    const detail = await stocktakeDetailApi(row.id);
    const lineCount = (detail.items || []).length;
    const diffTotal = (detail.items || []).reduce((sum, item) => sum + (item.diffQty || 0), 0);
    await ElMessageBox.confirm(
      `盘点单号：${row.stocktakeNo}\n明细条数：${lineCount}\n差异合计：${diffTotal}\n确认后将修正库位与仓库库存。`,
      "二次确认",
      { type: "warning", confirmButtonText: "确认执行", cancelButtonText: "取消" }
    );
    await stocktakeConfirmApi(row.id);
    ElMessage.success("确认盘点成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "确认盘点失败");
    }
  }
}

async function deleteRow(row) {
  if (row.status !== 0) {
    ElMessage.warning("仅草稿状态可以删除");
    return;
  }
  try {
    // 删除盘点单前展示明细数，提醒删除后不可回滚。
    let lineCount = "-";
    try {
      const detail = await stocktakeDetailApi(row.id);
      lineCount = (detail.items || []).length;
    } catch (_error) {
      // 详情失败不阻断删除确认。
    }
    await ElMessageBox.confirm(
      `盘点单号：${row.stocktakeNo}\n明细条数：${lineCount}\n删除后不可恢复，是否继续？`,
      "删除确认",
      { type: "warning", confirmButtonText: "确认删除", cancelButtonText: "取消" }
    );
    await stocktakeDeleteApi(row.id);
    ElMessage.success("删除成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "删除失败");
    }
  }
}

defineExpose({ loadData });
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="按盘点单号/仓库搜索" class="wms-query-input" />
      <el-select v-model="query.status" clearable placeholder="状态" class="wms-query-select">
        <el-option :value="0" label="草稿" />
        <el-option :value="1" label="已提交" />
        <el-option :value="2" label="已完成" />
      </el-select>
      <el-button type="primary" @click="query.pageNo = 1; loadData()">查询</el-button>
      <el-button @click="openCreate">新建盘点单</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="stocktakeNo" label="盘点单号" min-width="180" />
      <el-table-column prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column prop="scopeType" label="范围" min-width="120" />
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ formatStatus(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="160">
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
          <el-button v-if="row.status === 1" size="small" class="op-btn op-confirm" @click="confirmRow(row)">确认盘点</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑盘点单' : '新建盘点单'" width="980px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="仓库" required>
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%" @change="onWarehouseChange">
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
            <el-form-item label="盘点范围">
              <el-select v-model="form.scopeType" style="width: 100%">
                <el-option value="BY_WAREHOUSE" label="按仓库" />
                <el-option value="BY_LOCATION" label="按库位" />
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
          <span>盘点明细</span>
          <el-button size="small" @click="addItem">新增明细</el-button>
        </div>
        <el-table :data="form.items" border>
          <el-table-column label="SKU" min-width="240">
            <template #default="{ row }">
              <el-select v-model="row.skuId" filterable placeholder="选择商品" style="width: 100%" @change="onSkuChange(row)">
                <el-option
                  v-for="item in skuOptions"
                  :key="item.id"
                  :label="`${item.skuCode} - ${item.skuName}`"
                  :value="item.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="库位" min-width="220">
            <template #default="{ row }">
              <el-select v-model="row.locationId" filterable placeholder="选择库位" style="width: 100%">
                <el-option v-for="item in locationOptionsForRow(row)" :key="item.id" :label="item.displayLabel" :value="item.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="实盘数量" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.countQty" :min="0" :step="1" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="差异原因" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.reason" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="160">
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

    <el-dialog v-model="detailVisible" title="盘点单详情" width="940px">
      <template v-if="detailData">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="盘点单号">{{ detailData.stocktakeNo }}</el-descriptions-item>
          <el-descriptions-item label="盘点范围">{{ detailData.scopeType }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detailData.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ formatStatus(detailData.status) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detailData.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailData.remark || "-" }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detailData.items || []" border style="margin-top: 12px">
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="140" />
          <el-table-column prop="locationCode" label="库位编码" min-width="120" />
          <el-table-column prop="bookQty" label="账面数量" width="100" />
          <el-table-column prop="countQty" label="实盘数量" width="100" />
          <el-table-column prop="diffQty" label="差异数量" width="100" />
          <el-table-column prop="reason" label="差异原因" min-width="120" />
          <el-table-column prop="remark" label="备注" min-width="140" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>




