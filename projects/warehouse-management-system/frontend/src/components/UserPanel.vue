<!--
  @file 速览索引
  @summary 用户与角色治理合并页，负责账号管理、角色状态配置、密码重置与角色筛选。
  @core 1. 展示用户分页列表并支持按角色筛选
  @core 2. 支持新增采购员/仓库员账号并设置初始密码
  @core 3. 支持用户启停与密码重置
  @core 4. 支持角色状态切换，并保护 ADMIN 角色不可停用
  @entry 先看：loadData、loadUsers、loadRoles、save、toggleRoleStatus
  @deps 关键依赖：api/user.js、api/role.js
  @state 关键状态：query、roleQuery、form、rows、roleRows、roleOptions
  @risk 高风险修改点：ADMIN 角色保护、可新增角色范围、默认密码口径、用户与角色联动刷新
  @link 相关文件：前端/src/api/user.js、前端/src/api/role.js、后端/src/main/java/com/wms/backend/user/service/UserService.java
-->
<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { roleOptionsApi, rolePageApi, roleStatusApi } from "../api/role";
import { userCreateApi, userPageApi, userResetPasswordApi, userStatusApi } from "../api/user";

const loading = ref(false);
const roleLoading = ref(false);
const saving = ref(false);
const rows = ref([]);
const total = ref(0);
const roleRows = ref([]);
const roleOptions = ref([]);
const dialogVisible = ref(false);
const roleQuery = reactive({
  keyword: ""
});

const query = reactive({
  keyword: "",
  roleId: null,
  pageNo: 1,
  pageSize: 10
});

const form = reactive({
  username: "",
  password: "",
  realName: "",
  mobile: "",
  email: "",
  roleId: null
});

// 用户新增仅开放“仓库员/采购员”，管理员账号统一走数据库受控维护。
const createRoleOptions = computed(() => {
  return (roleOptions.value || []).filter((item) => item.roleCode === "WAREHOUSE" || item.roleCode === "PURCHASER");
});

function resetForm() {
  form.username = "";
  form.password = "";
  form.realName = "";
  form.mobile = "";
  form.email = "";
  form.roleId = null;
}

async function loadRoleOptions() {
  try {
    roleOptions.value = await roleOptionsApi();
  } catch (error) {
    ElMessage.error(error?.message || "角色选项加载失败");
  }
}

async function loadUsers() {
  loading.value = true;
  try {
    const data = await userPageApi(query);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || "用户列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadRoles() {
  roleLoading.value = true;
  try {
    const data = await rolePageApi({
      keyword: roleQuery.keyword || "",
      pageNo: 1,
      pageSize: 50
    });
    roleRows.value = data.records || [];
  } catch (error) {
    ElMessage.error(error?.message || "角色配置加载失败");
  } finally {
    roleLoading.value = false;
  }
}

// 合并页入口：一次刷新用户与角色配置，减少管理员在两页间切换。
async function loadData() {
  await Promise.all([loadUsers(), loadRoles(), loadRoleOptions()]);
}

function openCreate() {
  resetForm();
  dialogVisible.value = true;
}

async function save() {
  if (!form.username || !form.realName || !form.roleId) {
    ElMessage.warning("请填写必要字段");
    return;
  }
  const selectedRole = createRoleOptions.value.find((item) => item.id === form.roleId);
  if (!selectedRole) {
    ElMessage.warning("仅允许新增仓库员或采购员账号");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      username: form.username,
      password: form.password && form.password.trim() ? form.password.trim() : undefined,
      realName: form.realName,
      mobile: form.mobile,
      email: form.email,
      roleId: form.roleId
    };
    await userCreateApi(payload);
    ElMessage.success(payload.password ? "用户新增成功（已设置初始密码）" : "用户新增成功（默认密码：12345）");
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error?.message || "用户保存失败");
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
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${row.username}</span></div>
        <div>目标状态：<span style="color:#b91c1c;font-weight:700">${actionText}</span></div>
        <div style="color:#7c2d12">用户状态变更会影响该账号的登录使用。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await userStatusApi(row.id, nextStatus);
    ElMessage.success("状态更新成功");
    await loadUsers();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "状态更新失败");
    }
  }
}

async function resetPassword(row) {
  try {
    const html = `
      <div style="line-height:1.7">
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${row.username}</span></div>
        <div>执行操作：<span style="color:#b91c1c;font-weight:700">重置密码</span></div>
        <div>重置结果：密码将变为 <span style="color:#b91c1c;font-weight:700">12345</span></div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await userResetPasswordApi(row.id);
    ElMessage.success("密码已重置为 12345");
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "密码重置失败");
    }
  }
}

async function toggleRoleStatus(row) {
  if (row.roleCode === "ADMIN") {
    ElMessage.warning("管理员角色为系统内置角色，禁止停用");
    return;
  }
  try {
    const nextStatus = row.status === 1 ? 0 : 1;
    const actionText = nextStatus === 1 ? "启用" : "停用";
    const html = `
      <div style="line-height:1.7">
        <div>操作对象：<span style="color:#b91c1c;font-weight:700">${row.roleName}</span></div>
        <div>目标状态：<span style="color:#b91c1c;font-weight:700">${actionText}</span></div>
        <div style="color:#7c2d12">角色状态变更会影响关联账号登录与权限。</div>
      </div>
    `;
    await ElMessageBox.confirm(html, "二次确认", {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true
    });
    await roleStatusApi(row.id, nextStatus);
    ElMessage.success("角色状态更新成功");
    await Promise.all([loadRoles(), loadRoleOptions()]);
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error?.message || "角色状态更新失败");
    }
  }
}

defineExpose({ loadData });

onMounted(async () => {
  await loadData();
});
</script>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-input v-model="query.keyword" clearable placeholder="按账号/姓名搜索" class="wms-query-input-md" />
      <el-select v-model="query.roleId" clearable placeholder="按角色筛选" class="wms-query-select-lg">
        <el-option v-for="item in roleOptions" :key="item.id" :label="item.roleName" :value="item.id" />
      </el-select>
      <el-button type="primary" @click="query.pageNo = 1; loadUsers()">查询</el-button>
      <el-button @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="username" label="账号" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="mobile" label="手机号" min-width="130" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="roleName" label="角色" min-width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button size="small" class="op-btn op-reset" @click="resetPassword(row)">重置密码</el-button>
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
        @change="loadUsers"
      />
    </div>

    <el-divider content-position="left">角色状态配置</el-divider>
    <div class="toolbar">
      <el-input v-model="roleQuery.keyword" clearable placeholder="按角色编码/名称搜索" class="wms-query-input-md" />
      <el-button type="primary" @click="loadRoles">查询角色</el-button>
    </div>
    <div style="margin: -4px 0 8px; color: #667085; font-size: 12px">
      角色配置已并入用户管理页，管理员角色为系统内置且不可停用。
    </div>
    <el-table v-loading="roleLoading" :data="roleRows" border>
      <el-table-column prop="roleCode" label="角色编码" min-width="130" />
      <el-table-column prop="roleName" label="角色名称" min-width="150" />
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            class="op-btn op-toggle"
            :disabled="row.roleCode === 'ADMIN'"
            @click="toggleRoleStatus(row)"
          >
            {{ row.roleCode === "ADMIN" ? "系统内置" : row.status === 1 ? "停用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新增用户" width="560px">
      <el-form label-width="100px">
        <el-form-item label="账号" required>
          <el-input v-model="form.username" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="可选，不填则默认 12345"
          />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.mobile" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="item in createRoleOptions"
              :key="item.id"
              :label="`${item.roleName} (${item.roleCode})`"
              :value="item.id"
            />
          </el-select>
          <div style="margin-top: 6px; color: #667085; font-size: 12px">
            页面仅支持新增仓库员/采购员账号；管理员账号请通过数据库维护。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>




