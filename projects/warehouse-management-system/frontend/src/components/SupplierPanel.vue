<!--
  @file 速览索引
  @summary 供应商基础资料页面，负责供应商分页、新增编辑、状态维护，为采购与补货提供供应来源基础。
  @core 1. 展示供应商分页列表
  @core 2. 支持供应商新增与编辑
  @core 3. 支持供应商状态切换
  @core 4. 维护联系人、电话、交期等采购基础信息
  @entry 先看：loadData、save、toggleStatus
  @deps 关键依赖：api/supplier.js
  @state 关键状态：query、rows、form、dialogVisible
  @risk 高风险修改点：供应商编码唯一性、联系方式校验、交期字段、状态口径
  @link 相关文件：前端/src/api/supplier.js、后端/src/main/java/com/wms/backend/supplier/service/SupplierService.java
-->
<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { supplierCreateApi, supplierPageApi, supplierStatusApi, supplierUpdateApi } from "../api/supplier";

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
const canManageSupplier = computed(() => props.roleCode === "ADMIN" || props.roleCode === "PURCHASER");
const roleAccessHint = computed(() => {
  const roleLabel = roleLabelMap[props.roleCode] || "当前账号";
  if (canManageSupplier.value) {
    return {
      type: "success",
      title: `${roleLabel}可维护供应商主数据（新增、编辑、状态变更）。`
    };
  }
  return {
    type: "warning",
    title: `${roleLabel}当前为只读模式，仅可查看供应商信息。`
  };
});

const query = reactive({
  keyword: "",
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  supplierCode: "",
  supplierName: "",
  contactName: "",
  contactPhone: "",
  leadTimeDays: 0,
  remark: ""
});

function resetForm() {
  form.supplierCode = "";
  form.supplierName = "";
  form.contactName = "";
  form.contactPhone = "";
  form.leadTimeDays = 0;
  form.remark = "";
}

async function loadData() {
  loading.value = true;
  try {
    const data = await supplierPageApi(query);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "供应商列表加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (!canManageSupplier.value) {
    return;
  }
  isEdit.value = false;
  currentId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row) {
  if (!canManageSupplier.value) {
    return;
  }
  isEdit.value = true;
  currentId.value = row.id;
  form.supplierCode = row.supplierCode || "";
  form.supplierName = row.supplierName || "";
  form.contactName = row.contactName || "";
  form.contactPhone = row.contactPhone || "";
  form.leadTimeDays = row.leadTimeDays || 0;
  form.remark = row.remark || "";
  dialogVisible.value = true;
}

async function save() {
  if (!canManageSupplier.value) {
    return;
  }
  if (!form.supplierName || (!isEdit.value && !form.supplierCode)) {
    ElMessage.warning("请填写必要字段");
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await supplierUpdateApi(currentId.value, {
        supplierName: form.supplierName,
        contactName: form.contactName,
        contactPhone: form.contactPhone,
        leadTimeDays: form.leadTimeDays,
        remark: form.remark
      });
      ElMessage.success("供应商更新成功");
    } else {
      await supplierCreateApi({
        supplierCode: form.supplierCode,
        supplierName: form.supplierName,
        contactName: form.contactName,
        contactPhone: form.contactPhone,
        leadTimeDays: form.leadTimeDays,
        remark: form.remark
      });
      ElMessage.success("供应商新增成功");
    }
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "供应商保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row) {
  if (!canManageSupplier.value) {
    return;
  }
  try {
    const nextStatus = row.status === 1 ? 0 : 1;
    const actionText = nextStatus === 1 ? "启用" : "停用";
    const html = `
      <div style="line-height:1.7">
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${row.supplierName}</span></div>
        <div>目标状态：<span style="color:#b91c1c;font-weight:700">${actionText}</span></div>
        <div style="color:#7c2d12">请确认该供应商状态调整不会影响采购与入库流程。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await supplierStatusApi(row.id, nextStatus);
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
      <el-input v-model="query.keyword" clearable placeholder="按供应商编码/名称搜索" class="wms-query-input-md" />
      <el-button type="primary" @click="query.pageNo = 1; loadData()">查询</el-button>
      <el-button v-if="canManageSupplier" @click="openCreate">新增供应商</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border size="small">
      <el-table-column prop="supplierCode" label="供应商编码" min-width="108" show-overflow-tooltip />
      <el-table-column prop="supplierName" label="供应商名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="contactName" label="联系人" min-width="90" show-overflow-tooltip />
      <el-table-column prop="contactPhone" label="联系电话" min-width="120" />
      <el-table-column prop="leadTimeDays" label="交期(天)" min-width="82" />
      <el-table-column label="状态" width="84">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManageSupplier" label="操作" width="170" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑供应商' : '新增供应商'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="供应商编码" required>
          <el-input v-model="form.supplierCode" :disabled="isEdit" placeholder="例如：SUP002" />
        </el-form-item>
        <el-form-item label="供应商名称" required>
          <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="交期(天)">
          <el-input-number v-model="form.leadTimeDays" :min="0" style="width: 100%" />
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




