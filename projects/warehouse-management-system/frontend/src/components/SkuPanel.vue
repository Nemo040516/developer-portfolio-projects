<!--
  @file 速览索引
  @summary SKU 主数据页面，负责商品分页、商品新增编辑、状态维护，是库存与单据模块的共享主数据入口。
  @core 1. 展示 SKU 分页列表
  @core 2. 支持 SKU 新增与编辑
  @core 3. 支持 SKU 状态切换
  @core 4. 为入库、出库、库存、补货提供商品基础数据
  @entry 先看：loadData、save、toggleStatus
  @deps 关键依赖：api/sku.js
  @state 关键状态：query、rows、form、dialogVisible
  @risk 高风险修改点：SKU 编码唯一性、状态字段、共享主数据被多模块依赖
  @link 相关文件：前端/src/api/sku.js、后端/src/main/java/com/wms/backend/sku/service/SkuService.java
-->
<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { inventoryStockPageApi } from "../api/inventory";
import { skuCreateApi, skuPageApi, skuStatusApi, skuUpdateApi } from "../api/sku";

const props = defineProps({
  roleCode: {
    type: String,
    default: ""
  }
});

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentId = ref(null);
const roleLabelMap = {
  ADMIN: "管理员",
  WAREHOUSE: "仓库员",
  PURCHASER: "采购员"
};
const canManageSku = computed(() => props.roleCode === "ADMIN" || props.roleCode === "WAREHOUSE");
const roleAccessHint = computed(() => {
  const roleLabel = roleLabelMap[props.roleCode] || "当前账号";
  if (canManageSku.value) {
    return {
      type: "success",
      title: `${roleLabel}可维护商品主数据（新增、编辑、状态变更）。`
    };
  }
  return {
    type: "warning",
    title: `${roleLabel}当前为只读模式，仅可查看商品信息。`
  };
});

const query = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  skuCode: "",
  skuName: "",
  specification: "",
  unit: "",
  safeStock: 0,
  remark: ""
});

function resetForm() {
  form.skuCode = "";
  form.skuName = "";
  form.specification = "";
  form.unit = "";
  form.safeStock = 0;
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

async function loadSkuStockSummary(skuCode) {
  let pageNo = 1;
  let total = 0;
  let records = [];
  do {
    const data = await inventoryStockPageApi({ keyword: skuCode, pageNo, pageSize: 50 });
    total = data.total || 0;
    records = records.concat(data.records || []);
    pageNo += 1;
  } while (records.length < total && pageNo <= 20);

  const matched = records.filter((item) => item.skuCode === skuCode);
  const warehouseCount = matched.length;
  const qtyTotal = matched.reduce((sum, item) => sum + (item.onHandQty || 0), 0);
  return { warehouseCount, qtyTotal };
}

async function loadData() {
  loading.value = true;
  try {
    const data = await skuPageApi(query);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "商品列表加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (!canManageSku.value) {
    return;
  }
  isEdit.value = false;
  currentId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row) {
  if (!canManageSku.value) {
    return;
  }
  isEdit.value = true;
  currentId.value = row.id;
  form.skuCode = row.skuCode || "";
  form.skuName = row.skuName || "";
  form.specification = row.specification || "";
  form.unit = row.unit || "";
  form.safeStock = row.safeStock || 0;
  form.remark = row.remark || "";
  dialogVisible.value = true;
}

async function save() {
  if (!canManageSku.value) {
    return;
  }
  if (!form.skuName || (!isEdit.value && !form.skuCode)) {
    ElMessage.warning("请填写必要字段");
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await skuUpdateApi(currentId.value, {
        skuName: form.skuName,
        specification: form.specification,
        unit: form.unit,
        safeStock: form.safeStock,
        remark: form.remark
      });
      ElMessage.success("商品更新成功");
    } else {
      await skuCreateApi({
        skuCode: form.skuCode,
        skuName: form.skuName,
        specification: form.specification,
        unit: form.unit,
        safeStock: form.safeStock,
        remark: form.remark
      });
      ElMessage.success("商品新增成功");
    }
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "商品保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row) {
  if (!canManageSku.value) {
    return;
  }
  try {
    const nextStatus = row.status === 1 ? 0 : 1;
    const actionText = nextStatus === 1 ? "启用" : "停用";
    let summary = { warehouseCount: "-", qtyTotal: "-" };
    try {
      summary = await loadSkuStockSummary(row.skuCode);
    } catch (_error) {
      // 库存汇总查询失败时不阻断主操作，仅显示空值提示。
    }
    const html = `
      <div style="line-height:1.7">
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${escapeHtml(row.skuName)}</span></div>
        <div>SKU编码：<span style="color:#b91c1c;font-weight:700">${escapeHtml(row.skuCode)}</span></div>
        <div>目标状态：<span style="color:#b91c1c;font-weight:700">${actionText}</span></div>
        <div>安全库存：<span style="color:#b91c1c;font-weight:700">${row.safeStock || 0}</span></div>
        <div>当前库存总量：<span style="color:#b91c1c;font-weight:700">${summary.qtyTotal}</span>（分布仓库数：<span style="color:#b91c1c;font-weight:700">${summary.warehouseCount}</span>）</div>
        <div style="color:#7c2d12">请确认该商品状态变更符合当前库存与采购计划。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await skuStatusApi(row.id, nextStatus);
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
    <el-alert
      :title="roleAccessHint.title"
      :type="roleAccessHint.type"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="按商品编码/名称搜索" class="wms-query-input-md" />
      <el-button type="primary" @click="query.pageNo = 1; loadData()">查询</el-button>
      <el-button v-if="canManageSku" @click="openCreate">新增商品</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border size="small">
      <el-table-column prop="skuCode" label="商品编码" min-width="108" show-overflow-tooltip />
      <el-table-column prop="skuName" label="商品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" min-width="96" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" min-width="70" />
      <el-table-column prop="safeStock" label="安全库存" min-width="82" />
      <el-table-column label="状态" width="84">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManageSku" label="操作" width="170" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑商品' : '新增商品'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="商品编码" required>
          <el-input v-model="form.skuCode" :disabled="isEdit" placeholder="例如：SKU002" />
        </el-form-item>
        <el-form-item label="商品名称" required>
          <el-input v-model="form.skuName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.specification" placeholder="例如：M6*25" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="例如：盒/件" />
        </el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="form.safeStock" :min="0" style="width: 100%" />
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




