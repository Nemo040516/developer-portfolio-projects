<!--
  @file 速览索引
  @summary 仓库基础资料页面，负责仓库分页展示、仓库新增编辑、状态维护与下游模块可用仓库基础来源。
  @core 1. 展示仓库分页列表
  @core 2. 支持仓库新增与编辑
  @core 3. 支持仓库状态切换
  @core 4. 为其他模块提供仓库主数据基础
  @entry 先看：loadData、save、toggleStatus
  @deps 关键依赖：api/warehouse.js
  @state 关键状态：query、rows、form、dialogVisible
  @risk 高风险修改点：仓库状态口径、仓库编码唯一性、表单字段与后端 DTO 对齐
  @link 相关文件：前端/src/api/warehouse.js、后端/src/main/java/com/wms/backend/warehouse/service/WarehouseService.java
-->
<script setup>
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { inventoryStockPageApi } from "../api/inventory";
import { warehouseCreateApi, warehousePageApi, warehouseStatusApi, warehouseUpdateApi } from "../api/warehouse";

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentId = ref(null);

const query = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  warehouseCode: "",
  warehouseName: "",
  address: "",
  managerName: "",
  contactPhone: "",
  remark: ""
});

function resetForm() {
  form.warehouseCode = "";
  form.warehouseName = "";
  form.address = "";
  form.managerName = "";
  form.contactPhone = "";
  form.remark = "";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function loadWarehouseStockSummary(warehouseCode) {
  let pageNo = 1;
  let total = 0;
  let records = [];
  do {
    const data = await inventoryStockPageApi({ keyword: warehouseCode, pageNo, pageSize: 50 });
    total = data.total || 0;
    records = records.concat(data.records || []);
    pageNo += 1;
  } while (records.length < total && pageNo <= 20);

  const matched = records.filter((item) => item.warehouseCode === warehouseCode);
  const skuCount = matched.length;
  const qtyTotal = matched.reduce((sum, item) => sum + (item.onHandQty || 0), 0);
  return { skuCount, qtyTotal };
}

async function loadData() {
  loading.value = true;
  try {
    const data = await warehousePageApi(query);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "仓库列表加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  isEdit.value = false;
  currentId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row) {
  isEdit.value = true;
  currentId.value = row.id;
  form.warehouseCode = row.warehouseCode;
  form.warehouseName = row.warehouseName || "";
  form.address = row.address || "";
  form.managerName = row.managerName || "";
  form.contactPhone = row.contactPhone || "";
  form.remark = row.remark || "";
  dialogVisible.value = true;
}

async function save() {
  if (!form.warehouseName || (!isEdit.value && !form.warehouseCode)) {
    ElMessage.warning("请填写必要字段");
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await warehouseUpdateApi(currentId.value, {
        warehouseName: form.warehouseName,
        address: form.address,
        managerName: form.managerName,
        contactPhone: form.contactPhone,
        remark: form.remark
      });
      ElMessage.success("仓库更新成功");
    } else {
      await warehouseCreateApi({
        warehouseCode: form.warehouseCode,
        warehouseName: form.warehouseName,
        address: form.address,
        managerName: form.managerName,
        contactPhone: form.contactPhone,
        remark: form.remark
      });
      ElMessage.success("仓库新增成功");
    }
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "仓库保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row) {
  try {
    const nextStatus = row.status === 1 ? 0 : 1;
    const actionText = nextStatus === 1 ? "启用" : "停用";
    let summary = { skuCount: "-", qtyTotal: "-" };
    try {
      summary = await loadWarehouseStockSummary(row.warehouseCode);
    } catch (_error) {
      // 库存汇总查询失败时不阻断主操作，仅显示空值提示。
    }
    const html = `
      <div style="line-height:1.7">
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${escapeHtml(row.warehouseName)}</span></div>
        <div>目标状态：<span style="color:#b91c1c;font-weight:700">${actionText}</span></div>
        <div>当前SKU种类：<span style="color:#b91c1c;font-weight:700">${summary.skuCount}</span></div>
        <div>当前库存总量：<span style="color:#b91c1c;font-weight:700">${summary.qtyTotal}</span></div>
        <div style="color:#7c2d12">请确认该操作不会影响当前仓内库存业务。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await warehouseStatusApi(row.id, nextStatus);
    ElMessage.success("状态更新成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "状态更新失败");
    }
  }
}

defineExpose({ loadData });
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="按仓库编码/名称搜索" class="wms-query-input-md" />
      <el-button type="primary" @click="query.pageNo = 1; loadData()">查询</el-button>
      <el-button @click="openCreate">新增仓库</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="warehouseCode" label="仓库编码" min-width="120" />
      <el-table-column prop="warehouseName" label="仓库名称" min-width="160" />
      <el-table-column prop="managerName" label="负责人" min-width="100" />
      <el-table-column prop="contactPhone" label="联系电话" min-width="130" />
      <el-table-column prop="address" label="仓库地址" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button size="small" class="op-btn op-edit" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" class="op-btn op-toggle" @click="toggleStatus(row)">
            {{ row.status === 1 ? "停用" : "启用" }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑仓库' : '新增仓库'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="仓库编码" required>
          <el-input v-model="form.warehouseCode" :disabled="isEdit" placeholder="例如：WH002" />
        </el-form-item>
        <el-form-item label="仓库名称" required>
          <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.managerName" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="手机号（11位）" />
        </el-form-item>
        <el-form-item label="仓库地址">
          <el-input v-model="form.address" placeholder="请输入仓库地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>




