<!--
  @file 速览索引
  @summary 库存台账页面，负责展示仓库库存、库存流水、库位库存与库位流水，是库存查询总入口。
  @core 1. 展示库存汇总分页
  @core 2. 展示库存流水分页
  @core 3. 展示库位库存分页
  @core 4. 展示库位流水分页
  @entry 先看：loadData、各 tab 对应的查询方法、筛选状态定义
  @deps 关键依赖：api/inventory.js、api/warehouse.js、api/sku.js
  @state 关键状态：activeTab、query、stockRows、txnRows、locationStockRows、locationTxnRows
  @risk 高风险修改点：多 Tab 查询参数同步、分页状态、查询口径与后端接口字段一致性
  @link 相关文件：前端/src/api/inventory.js、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
-->
<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  inventoryLocationStockPageApi,
  inventoryLocationTxnPageApi,
  inventoryStockPageApi,
  inventoryTxnPageApi
} from "../api/inventory";

const activeTab = ref("stock");
const stockLoading = ref(false);
const txnLoading = ref(false);
const locationStockLoading = ref(false);
const locationTxnLoading = ref(false);
const stockRows = ref([]);
const txnRows = ref([]);
const locationStockRows = ref([]);
const locationTxnRows = ref([]);
const stockTotal = ref(0);
const txnTotal = ref(0);
const locationStockTotal = ref(0);
const locationTxnTotal = ref(0);

const stockQuery = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const txnQuery = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const locationStockQuery = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const locationTxnQuery = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const bizTypeLabelMap = {
  INBOUND: "入库确认",
  INBOUND_SUBMIT: "入库已提交（待确认）",
  PUTAWAY: "确认上架",
  PUTAWAY_SUBMIT: "上架已提交（待确认）"
};

function bizTypeLabel(code) {
  return bizTypeLabelMap[code] || code || "-";
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

async function loadStocks() {
  stockLoading.value = true;
  try {
    const data = await inventoryStockPageApi(stockQuery);
    stockRows.value = data.records || [];
    stockTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "库存列表加载失败");
  } finally {
    stockLoading.value = false;
  }
}

async function loadTxns() {
  txnLoading.value = true;
  try {
    const data = await inventoryTxnPageApi(txnQuery);
    txnRows.value = data.records || [];
    txnTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "库存流水加载失败");
  } finally {
    txnLoading.value = false;
  }
}

async function loadLocationStocks() {
  locationStockLoading.value = true;
  try {
    const data = await inventoryLocationStockPageApi(locationStockQuery);
    locationStockRows.value = data.records || [];
    locationStockTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "库位库存加载失败");
  } finally {
    locationStockLoading.value = false;
  }
}

async function loadLocationTxns() {
  locationTxnLoading.value = true;
  try {
    const data = await inventoryLocationTxnPageApi(locationTxnQuery);
    locationTxnRows.value = data.records || [];
    locationTxnTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "库位流水加载失败");
  } finally {
    locationTxnLoading.value = false;
  }
}

async function loadData() {
  if (activeTab.value === "stock") {
    await loadStocks();
    return;
  }
  if (activeTab.value === "txn") {
    await loadTxns();
    return;
  }
  if (activeTab.value === "locationStock") {
    await loadLocationStocks();
    return;
  }
  await loadLocationTxns();
}

async function handleTabChange() {
  await loadData();
}

defineExpose({
  loadData
});
</script>

<template>
  <div class="panel">
    <el-alert
      title="说明：库存台账分为仓库级与库位级。M3 阶段重点查看“库位库存/库位流水”用于验证上架结果。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="库存汇总" name="stock">
        <div class="toolbar">
          <el-input v-model="stockQuery.keyword" clearable placeholder="按仓库/SKU搜索" class="wms-query-input-md" />
          <el-button type="primary" @click="stockQuery.pageNo = 1; loadStocks()">查询</el-button>
        </div>
        <el-table v-loading="stockLoading" :data="stockRows" border>
          <el-table-column prop="warehouseCode" label="仓库编码" min-width="120" />
          <el-table-column prop="warehouseName" label="仓库名称" min-width="130" />
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="140" />
          <el-table-column prop="onHandQty" label="现存数量" min-width="100" />
          <el-table-column label="更新时间" min-width="150">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="stockQuery.pageNo"
            v-model:page-size="stockQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="stockTotal"
            @change="loadStocks"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="库存流水" name="txn">
        <div class="toolbar">
          <el-input v-model="txnQuery.keyword" clearable placeholder="按业务单号/SKU搜索" class="wms-query-input-md" />
          <el-button type="primary" @click="txnQuery.pageNo = 1; loadTxns()">查询</el-button>
        </div>
        <el-table v-loading="txnLoading" :data="txnRows" border>
          <el-table-column label="业务类型" min-width="170">
            <template #default="{ row }">
              {{ bizTypeLabel(row.bizType) }}
            </template>
          </el-table-column>
          <el-table-column prop="bizNo" label="业务单号" min-width="170" />
          <el-table-column prop="warehouseName" label="仓库" min-width="120" />
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="140" />
          <el-table-column prop="qtyChange" label="变更数量" min-width="90" />
          <el-table-column prop="beforeQty" label="变更前" min-width="90" />
          <el-table-column prop="afterQty" label="变更后" min-width="90" />
          <el-table-column prop="operatorName" label="操作人" min-width="90" />
          <el-table-column label="发生时间" min-width="150">
            <template #default="{ row }">
              {{ formatTime(row.occurredAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="txnQuery.pageNo"
            v-model:page-size="txnQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="txnTotal"
            @change="loadTxns"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="库位库存" name="locationStock">
        <div class="toolbar">
          <el-input v-model="locationStockQuery.keyword" clearable placeholder="按仓库/库位/SKU搜索" class="wms-query-input" />
          <el-button type="primary" @click="locationStockQuery.pageNo = 1; loadLocationStocks()">查询</el-button>
        </div>
        <el-table v-loading="locationStockLoading" :data="locationStockRows" border>
          <el-table-column prop="warehouseCode" label="仓库编码" min-width="110" />
          <el-table-column prop="warehouseName" label="仓库名称" min-width="120" />
          <el-table-column prop="locationCode" label="库位编码" min-width="110" />
          <el-table-column prop="areaName" label="库区" min-width="90" />
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="130" />
          <el-table-column prop="onHandQty" label="现存数量" min-width="90" />
          <el-table-column label="更新时间" min-width="150">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="locationStockQuery.pageNo"
            v-model:page-size="locationStockQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="locationStockTotal"
            @change="loadLocationStocks"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="库位流水" name="locationTxn">
        <div class="toolbar">
          <el-input v-model="locationTxnQuery.keyword" clearable placeholder="按业务单号/库位/SKU搜索" class="wms-query-input" />
          <el-button type="primary" @click="locationTxnQuery.pageNo = 1; loadLocationTxns()">查询</el-button>
        </div>
        <el-table v-loading="locationTxnLoading" :data="locationTxnRows" border>
          <el-table-column label="业务类型" min-width="160">
            <template #default="{ row }">
              {{ bizTypeLabel(row.bizType) }}
            </template>
          </el-table-column>
          <el-table-column prop="bizNo" label="业务单号" min-width="170" />
          <el-table-column prop="warehouseName" label="仓库" min-width="110" />
          <el-table-column prop="locationCode" label="库位" min-width="110" />
          <el-table-column prop="skuCode" label="SKU编码" min-width="120" />
          <el-table-column prop="skuName" label="SKU名称" min-width="130" />
          <el-table-column prop="qtyChange" label="变更数量" min-width="90" />
          <el-table-column prop="beforeQty" label="变更前" min-width="90" />
          <el-table-column prop="afterQty" label="变更后" min-width="90" />
          <el-table-column prop="operatorName" label="操作人" min-width="90" />
          <el-table-column label="发生时间" min-width="150">
            <template #default="{ row }">
              {{ formatTime(row.occurredAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="locationTxnQuery.pageNo"
            v-model:page-size="locationTxnQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="locationTxnTotal"
            @change="loadLocationTxns"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>




