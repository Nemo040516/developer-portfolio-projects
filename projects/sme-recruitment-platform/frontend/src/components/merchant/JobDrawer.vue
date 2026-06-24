<template>
  <el-drawer
    v-model="visible"
    :title="isEdit ? '编辑职位' : '发布新职位'"
    size="650px"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">

      <!-- 核心信息区块 (高亮背景) -->
      <div class="highlight-box">
        <el-form-item label="职位名称" prop="title">
          <el-input
            v-model="form.title"
            placeholder="例如：高级Java后端开发工程师"
            class="title-input"
          />
        </el-form-item>

        <div class="flex-row">
          <el-form-item label="职位分类" prop="category_id" class="flex-item">
            <el-cascader
              v-model="form.category_id"
              :options="categoryOptions"
              :props="cascaderProps"
              placeholder="请选择职位分类"
              style="width: 100%"
              filterable
              clearable
            />
          </el-form-item>

          <el-form-item label="薪资范围 (K)" required class="flex-item">
            <div class="salary-group">
              <el-input-number
                v-model="form.min_salary"
                :min="1"
                :controls="false"
                placeholder="Min"
                class="salary-input"
              />
              <span class="separator">-</span>
              <el-input-number
                v-model="form.max_salary"
                :min="form.min_salary"
                :controls="false"
                placeholder="Max"
                class="salary-input"
              />
            </div>
          </el-form-item>

          <el-form-item label="招聘人数" prop="headcount" class="flex-item">
            <el-input-number
              v-model="form.headcount"
              :min="1"
              :max="99"
              :controls="true"
              placeholder="人数"
              style="width: 100%"
            />
            <div class="form-tip">同一岗位需要多人时，请直接调整招聘人数，避免重复建岗。</div>
          </el-form-item>
        </div>
      </div>

      <!-- 详细要求区块 (Flex 布局) -->
      <div class="flex-row">
        <el-form-item label="工作城市" prop="work_location" class="flex-item">
           <el-input v-model="form.work_location" placeholder="例如：苏州" />
        </el-form-item>
        <el-form-item label="工作区域" prop="district" class="flex-item">
           <el-input v-model="form.district" placeholder="例如：虎丘区" />
        </el-form-item>
      </div>

      <div class="flex-row">
        <el-form-item label="经验要求" prop="experience" class="flex-item">
          <el-select v-model="form.experience" style="width: 100%">
            <el-option v-for="opt in experienceOptions" :key="opt" :label="opt" :value="opt"/>
          </el-select>
        </el-form-item>
        <el-form-item label="学历要求" prop="degree" class="flex-item">
           <el-select v-model="form.degree" style="width: 100%">
            <el-option v-for="opt in degreeOptions" :key="opt" :label="opt" :value="opt"/>
          </el-select>
        </el-form-item>
      </div>

      <!-- 标签输入 -->
      <el-form-item label="职位标签 (回车添加)" prop="tags">
        <div class="tags-container">
          <el-tag
            v-for="(tag, index) in form.tags"
            :key="index"
            closable
            @close="removeTag(index)"
            class="job-tag"
          >
            {{ tag }}
          </el-tag>
          <el-input
            v-model="tagInput"
            placeholder="输入福利关键词后按回车"
            @keyup.enter="addTag"
            @blur="addTag"
            class="tag-input"
          />
        </div>
        <div class="form-tip">热门：五险一金、双休、弹性工作、股票期权</div>
      </el-form-item>

      <!-- 轻量职位详情编辑区 -->
      <el-form-item label="职位详情" prop="description" class="editor-item">
        <div class="editor-wrapper">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="10"
            resize="vertical"
            maxlength="5000"
            show-word-limit
            placeholder="请按段落输入岗位职责与任职要求，换行将按段落保存。"
            class="description-textarea"
          />
          <div class="editor-tip">提示：已切换为轻量编辑模式，系统会自动将每行转换为一个段落保存。</div>
        </div>
      </el-form-item>

    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="submitForm">
          {{ isEdit ? '保存修改' : '立即发布' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { addJob, updateJob } from '@/api/job'

// ================= 常量定义 =================
const experienceOptions = ['应届生', '1-3年', '3-5年', '5-10年', '10年以上']
const degreeOptions = ['大专', '本科', '硕士', '博士', '不限']

// 级联选择器配置 (适配后端 /category/list 接口)
const cascaderProps = {
  emitPath: false,
  value: 'id',           // 后端字段: id
  label: 'categoryName', // 后端字段: categoryName
  children: 'children'   // 后端字段: children
}

// ================= Props & Emits =================
const props = defineProps({
  modelValue: Boolean,
  categoryOptions: { type: Array, default: () => [] },
  currentRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'success'])

// ================= State =================
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})
const loading = ref(false)
const isEdit = computed(() => !!props.currentRow && !!props.currentRow.id)
const formRef = ref(null)

// 表单数据
const form = reactive({
  id: null,
  title: '',
  category_id: null,
  min_salary: 0,
  max_salary: 0,
  headcount: 1,
  work_location: '',
  district: '',
  experience: '不限',
  degree: '不限',
  tags: [],
  description: '',
})

// 标签输入
const tagInput = ref('')

// ================= Methods =================

// 初始化/重置表单
const resetForm = () => {
  form.id = null
  form.title = ''
  form.category_id = null
  form.min_salary = 0
  form.max_salary = 0
  form.headcount = 1
  form.work_location = ''
  form.district = ''
  form.experience = '不限'
  form.degree = '不限'
  form.tags = []
  form.description = ''
  tagInput.value = ''

  if (formRef.value) {
    formRef.value.resetFields()
  }
}

/**
 * @description 简易 HTML 转纯文本，兼容历史富文本详情回显。
 */
const htmlToPlainText = (html) => {
  if (!html) return ''
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>\s*<p>/gi, '\n')
    .replace(/<\/?p[^>]*>/gi, '')
    .replace(/<\/?[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .trim()
}

/**
 * @description 纯文本转 HTML 段落，确保后端仍按 HTML 字段存储。
 */
const plainTextToHtml = (text) => {
  const safeText = (text || '').trim()
  if (!safeText) return ''
  return safeText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) =>
      `<p>${line
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')}</p>`
    )
    .join('')
}

// 监听 currentRow 变化进行回显
watch(() => props.currentRow, (newVal) => {
  if (newVal && Object.keys(newVal).length > 0) {
    // 回显基础字段
    Object.assign(form, {
      id: newVal.id,
      title: newVal.title,
      category_id: newVal.category_id,
      min_salary: newVal.min_salary,
      max_salary: newVal.max_salary,
      headcount: newVal.headcount ?? 1,
      work_location: newVal.work_location,
      district: newVal.district,
      experience: newVal.experience || '不限',
      degree: newVal.degree || '不限',
      description: htmlToPlainText(newVal.description)
    })

    // 处理 Tags (兼容字符串和数组)
    if (Array.isArray(newVal.tags)) {
      form.tags = [...newVal.tags]
    } else if (typeof newVal.tags === 'string' && newVal.tags.trim()) {
      form.tags = newVal.tags.split(',').filter(Boolean)
    } else {
      form.tags = []
    }
  } else {
    resetForm()
  }
}, { immediate: true })

const handleClose = () => {
  resetForm()
}

// 标签操作
const addTag = () => {
  const val = tagInput.value.trim()
  if (val) {
    if (!form.tags.includes(val)) {
      form.tags.push(val)
    }
    tagInput.value = ''
  }
}
const removeTag = (index) => {
  form.tags.splice(index, 1)
}

// 表单校验规则
const rules = {
  title: [{ required: true, message: '请输入职位名称', trigger: 'blur' }],
  category_id: [{ required: true, message: '请选择职位分类', trigger: 'change' }],
  min_salary: [{ required: true, validator: (rule, value, callback) => {
    if (!value) callback(new Error('请输入最低薪资'))
    else if (value > form.max_salary && form.max_salary !== 0) {
      callback(new Error('最低薪资不能高于最高薪资'))
    } else {
      callback()
    }
  }, trigger: 'blur' }],
  headcount: [{ required: true, validator: (rule, value, callback) => {
    if (!value || value < 1) callback(new Error('请输入招聘人数'))
    else callback()
  }, trigger: 'blur' }],
  work_location: [{ required: true, message: '请输入工作城市', trigger: 'blur' }],
  description: [{
    required: true,
    validator: (rule, value, callback) => {
      if (!value || !value.trim()) callback(new Error('请填写职位描述'))
      else callback()
    },
    trigger: 'blur'
  }]
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true

      const payload = {
        ...form,
        tags: form.tags.join(','), // 数组转字符串提交
        description: plainTextToHtml(form.description)
      }

      try {
        const api = isEdit.value ? updateJob : addJob
        const res = await api(payload)

        if (res.code === 200) {
          ElMessage.success(isEdit.value ? '修改成功' : '发布成功')
          emit('success')
          visible.value = false
        } else {
          ElMessage.error(res.message || '操作失败')
        }
      } catch (error) {
        console.error(error)
        ElMessage.error('网络错误，请稍后重试')
      } finally {
        loading.value = false
      }
    }
  })
}

</script>

<style scoped>
/* 核心信息高亮区块 */
.highlight-box {
  background-color: #f5f5f7;
  padding: 20px;
  border-radius: 14px;
  margin-bottom: 24px;
  border: 1px solid #ebebef;
}

/* Flex 布局替代 Grid */
.flex-row {
  display: flex;
  gap: 20px;
  width: 100%;
}

.flex-item {
  flex: 1;
  min-width: 0; /* 防止内容撑开 flex item */
}

/* 职位名称输入框 */
.title-input :deep(.el-input__inner) {
  font-weight: bold;
  font-size: 16px;
  height: 40px;
}

/* 薪资控件优化 */
.salary-group {
  display: flex;
  align-items: center;
  background-color: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb; /* 统一边框 */
  width: 100%; /* 确保占满父容器 */
  overflow: hidden;
}

.salary-input {
  flex: 1;
  min-width: 0; /* 关键：允许缩小，防止撑破容器 */
}

/* 覆盖 el-input-number 默认样式，使其看起来像一个整体 */
.salary-input :deep(.el-input__wrapper) {
  box-shadow: none !important; /* 去掉输入框自带边框 */
  background-color: transparent;
  padding: 0 5px;
}
.salary-input :deep(.el-input__inner) {
  text-align: center;
}

.separator {
  padding: 0 8px;
  color: #98a2b3;
  font-weight: bold;
}

/* 标签容器 */
.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.job-tag {
  margin-right: 0; /* 使用 gap 控制间距 */
}

.tag-input {
  width: 200px;
}

.form-tip {
  font-size: 12px;
  color: #98a2b3;
  margin-top: 4px;
}

/* 富文本编辑器容器 */
.editor-wrapper {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 10px;
  background: #fff;
}

.description-textarea :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
  min-height: 220px !important;
}

.editor-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #98a2b3;
}

/* 强制编辑器占满宽度 */
.editor-item :deep(.el-form-item__content) {
  display: block;
  width: 100%;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
