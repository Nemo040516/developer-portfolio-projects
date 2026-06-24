<!--
  @file 速览索引
  @summary 库位基础资料页面，负责库位分页、库位新增编辑、状态维护与仓库关联管理。
  @core 1. 展示库位分页数据
  @core 2. 支持库位新增与编辑
  @core 3. 支持库位状态切换
  @core 4. 维护库位与仓库的绑定关系
  @entry 先看：loadData、save、toggleStatus
  @deps 关键依赖：api/location.js、api/warehouse.js
  @state 关键状态：query、rows、warehouseOptions、form
  @risk 高风险修改点：仓库-库位绑定、状态字段、容量口径、表单与后端字段一致性
  @link 相关文件：前端/src/api/location.js、后端/src/main/java/com/wms/backend/location/service/LocationService.java
-->
<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { locationCreateApi, locationPageApi, locationStatusApi, locationUpdateApi } from "../api/location";
import { warehouseOptionsApi } from "../api/warehouse";

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const warehouseOptions = ref([]);
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentId = ref(null);

const query = reactive({
  keyword: "",
  warehouseId: null,
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  warehouseId: null,
  locationCode: "",
  areaName: "",
  locationType: "",
  capacity: null,
  remark: ""
});

function resetForm() {
  form.warehouseId = null;
  form.locationCode = "";
  form.areaName = "";
  form.locationType = "";
  form.capacity = null;
  form.remark = "";
}

async function loadWarehouseOptions() {
  try {
    warehouseOptions.value = await warehouseOptionsApi();
  } catch (error) {
    ElMessage.error(error?.message || "仓库选项加载失败");
  }
}

async function loadData() {
  loading.value = true;
  try {
    const data = await locationPageApi(query);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "库位列表加载失败");
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
  form.warehouseId = row.warehouseId;
  form.locationCode = row.locationCode || "";
  form.areaName = row.areaName || "";
  form.locationType = row.locationType || "";
  form.capacity = row.capacity;
  form.remark = row.remark || "";
  dialogVisible.value = true;
}

async function save() {
  if (!form.warehouseId || (!isEdit.value && !form.locationCode)) {
    ElMessage.warning("请填写必要字段");
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await locationUpdateApi(currentId.value, {
        warehouseId: form.warehouseId,
        areaName: form.areaName,
        locationType: form.locationType,
        capacity: form.capacity,
        remark: form.remark
      });
      ElMessage.success("库位更新成功");
    } else {
      await locationCreateApi({
        warehouseId: form.warehouseId,
        locationCode: form.locationCode,
        areaName: form.areaName,
        locationType: form.locationType,
        capacity: form.capacity,
        remark: form.remark
      });
      ElMessage.success("库位新增成功");
    }
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "库位保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row) {
  try {
    const nextStatus = row.status === 1 ? 0 : 1;
    const actionText = nextStatus === 1 ? "启用" : "停用";
    const html = `
      <div style="line-height:1.7">
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${row.locationCode}</span></div>
        <div>目标状态：<span style="color:#b91c1c;font-weight:700">${actionText}</span></div>
        <div style="color:#7c2d12">请确认该库位状态变更不会影响在途任务。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await locationStatusApi(row.id, nextStatus);
    ElMessage.success("状态更新成功");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "状态更新失败");
    }
  }
}

defineExpose({ loadData });

onMounted(async () => {
  await loadWarehouseOptions();
});
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="按库位编码/库区搜索" class="wms-query-input-sm" />
      <el-select v-model="query.warehouseId" clearable placeholder="选择仓库" class="wms-query-select-lg">
        <el-option v-for="item in warehouseOptions" :key="item.id" :label="`${item.warehouseName} (${item.warehouseCode})`" :value="item.id" />
      </el-select>
      <el-button type="primary" @click="query.pageNo = 1; loadData()">查询</el-button>
      <el-button @click="openCreate">新增库位</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="locationCode" label="库位编码" min-width="130" />
      <el-table-column prop="warehouseName" label="所属仓库" min-width="160" />
      <el-table-column prop="areaName" label="库区" min-width="100" />
      <el-table-column prop="locationType" label="类型" min-width="100" />
      <el-table-column prop="capacity" label="容量上限" min-width="100" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑库位' : '新增库位'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="所属仓库" required>
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="`${item.warehouseName} (${item.warehouseCode})`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位编码" required>
          <el-input v-model="form.locationCode" :disabled="isEdit" placeholder="例如：A01-02" />
        </el-form-item>
        <el-form-item label="库区">
          <el-input v-model="form.areaName" placeholder="例如：A区" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="form.locationType" placeholder="例如：标准货架位" />
        </el-form-item>
        <el-form-item label="容量上限">
          <el-input-number v-model="form.capacity" :min="0" :precision="2" style="width: 100%" />
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




