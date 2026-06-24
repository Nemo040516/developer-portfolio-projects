<!--
文件速览：
1. 文件职责：求职者职位大厅，负责关键词搜索、胶囊筛选、智能匹配提示与职位卡片列表。
2. 页面入口：公共职位大厅路由 `/jobs`。
3. 关键结构：queryParams、search-wrapper、pill-filter-bar、category-panel、job-grid-hall。
4. 阅读建议：先看顶部搜索与筛选区，再看职位分类面板交互，最后看职位网格与断点样式。
-->
<template>
  <div class="job-hall-page">
    <!-- 1. 极简搜索 Header -->
    <header class="hall-header">
      <div class="search-hero">
        <h1 class="hall-title">探索你的职业新起点</h1>
        <div class="search-wrapper">
          <el-input
            v-model="queryParams.keyword"
            placeholder="搜索职位、公司或技术关键字..."
            size="large"
            class="apple-search-input"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon class="search-icon"><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" round class="search-btn" @click="handleSearch">搜索</el-button>
        </div>
      </div>

      <!-- 2. 胶囊筛选栏 (Pill Filters) -->
      <div class="pill-filter-bar">
        <div class="filter-pills">
          <el-select v-model="queryParams.degree" placeholder="学历要求" clearable @change="handleFilterChange" class="pill-select">
            <el-option v-for="item in degreeOptions" :key="item" :label="item" :value="item" />
          </el-select>
          
          <el-select v-model="queryParams.experience" placeholder="工作经验" clearable @change="handleFilterChange" class="pill-select">
            <el-option v-for="item in expOptions" :key="item" :label="item" :value="item" />
          </el-select>

          <div
            ref="categoryDropdownRef"
            class="category-dropdown"
          >
            <div
              class="category-pill-shell"
              tabindex="0"
              role="button"
              aria-label="职位分类"
              @click="toggleCategoryPanel"
              @keydown.enter.prevent="toggleCategoryPanel"
              @keydown.space.prevent="toggleCategoryPanel"
            >
              <div
                class="category-pill-select"
                :class="{ 'is-active': categoryPanelVisible, 'has-value': !!queryParams.categoryId }"
              >
                <span class="category-pill-text">{{ selectedCategoryLabel || '职位分类' }}</span>
                <button
                  v-if="queryParams.categoryId"
                  type="button"
                  class="category-pill-clear"
                  aria-label="清空职位分类"
                  @click.stop="clearCategoryFilter"
                >
                  <el-icon><Close /></el-icon>
                </button>
                <el-icon class="category-pill-arrow"><ArrowDown /></el-icon>
              </div>
            </div>

            <transition name="category-panel-fade">
              <div v-if="categoryPanelVisible" class="category-panel-pop">
                <div class="category-panel">
                  <div class="category-panel-top">
                    <div class="category-panel-headline">
                      <span class="category-panel-title">职位分类</span>
                      <span class="category-panel-tip">先选大类，再选细分类，避免长列表查找成本过高。</span>
                    </div>
                    <el-input
                      v-model="categoryKeyword"
                      clearable
                      placeholder="搜索细分类"
                      class="category-panel-search"
                    >
                      <template #prefix>
                        <el-icon><Search /></el-icon>
                      </template>
                    </el-input>
                  </div>

                  <div class="category-panel-body">
                    <div class="category-group-nav">
                      <button
                        v-for="group in categoryPanelGroups"
                        :key="group.id"
                        type="button"
                        class="category-group-tab"
                        :class="{ 'is-active': activeCategoryGroup?.id === group.id && !categoryKeyword }"
                        @click="setActiveCategoryGroup(group.id)"
                      >
                        <span>{{ group.label }}</span>
                        <span class="category-group-count">{{ group.options.length }}</span>
                      </button>
                    </div>

                    <div class="category-option-area">
                      <div class="category-option-header">
                        <span class="category-option-title">
                          {{ categoryKeyword ? '搜索结果' : (activeCategoryGroup?.label || '职位分类') }}
                        </span>
                        <span class="category-option-meta">{{ filteredCategoryOptions.length }} 项</span>
                      </div>

                      <div v-if="filteredCategoryOptions.length" ref="categoryOptionGridRef" class="category-option-grid">
                        <button
                          v-for="option in filteredCategoryOptions"
                          :key="option.id"
                          type="button"
                          class="category-choice"
                          :class="{ 'is-selected': queryParams.categoryId === option.id }"
                          @click="selectCategoryOption(option)"
                        >
                          <span class="category-choice-name">{{ option.label }}</span>
                          <span class="category-choice-meta">{{ option.parentLabel }}</span>
                          <span v-if="queryParams.categoryId === option.id" class="category-choice-badge">已选</span>
                        </button>
                      </div>

                      <div v-else class="category-option-empty">未找到匹配分类</div>
                    </div>
                  </div>
                </div>
              </div>
            </transition>
          </div>

          <el-select v-model="queryParams.industry" placeholder="所属行业" clearable @change="handleFilterChange" class="pill-select">
            <el-option v-for="item in industryOptions" :key="item" :label="item" :value="item" />
          </el-select>

          <el-select v-model="queryParams.sort" placeholder="智能排序" clearable @change="handleFilterChange" class="pill-select sort-pill">
            <el-option v-for="item in sortOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        
        <el-button link class="reset-link" @click="handleReset">重置全部</el-button>
      </div>

      <div v-if="isApplicant && queryParams.sort === 'smartMatch'" class="match-brief-bar">
        <div class="brief-left">
          <span class="brief-dot" />
          <span class="brief-text">{{ smartHintText }}</span>
        </div>
        <el-tooltip :content="matchDetailOpen ? '收起匹配详情' : '展开匹配详情'" placement="top">
          <el-button link class="brief-toggle-icon" @click="matchDetailOpen = !matchDetailOpen">
            <el-icon><InfoFilled /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <ElCollapseTransition>
        <div v-if="isApplicant && queryParams.sort === 'smartMatch' && matchDetailOpen" class="match-layer-panel">
          <div class="layer-row">
            <span class="layer-title">硬性条件（自动）</span>
            <div class="layer-tags">
              <span v-for="item in hardRuleTags" :key="item" class="layer-tag hard">{{ item }}</span>
              <span v-if="hardRuleTags.length === 0" class="layer-empty">未启用</span>
            </div>
          </div>
          <div class="layer-row">
            <span class="layer-title">手动筛选（用户）</span>
            <div class="layer-tags">
              <span v-for="item in manualRuleTags" :key="item" class="layer-tag soft">{{ item }}</span>
              <span v-if="manualRuleTags.length === 0" class="layer-empty">当前未额外设置</span>
            </div>
          </div>
        </div>
      </ElCollapseTransition>
    </header>

    <!-- 3. 职位列表 (紧凑网格) -->
    <main class="job-list-wrap" v-loading="loading">
      <div v-if="jobList.length" class="job-grid-hall">
        <div v-for="job in jobList" :key="job.id" class="job-hall-card" @click="toDetail(job.id)">
          <div class="card-header">
            <div class="job-info-meta">
              <h3 class="job-title-text">{{ job.title }}</h3>
              <p class="company-sub">{{ job.companyName }}</p>
            </div>
            <span class="job-price">{{ formatSalaryK(job.minSalary, job.maxSalary) }}</span>
          </div>

          <div class="tag-cloud">
            <span class="tag-item">{{ job.workLocation }}</span>
            <span class="tag-item">{{ job.experience }}</span>
            <span class="tag-item">{{ job.degree }}</span>
          </div>

          <div class="card-footer-hall">
            <div class="publisher">
              <el-avatar :size="20" :src="formatFileUrl(job.companyLogo)" />
              <span>{{ job.publisherName || 'HR' }}</span>
            </div>
            <div class="view-count">
              <el-icon><View /></el-icon>
              <span>{{ job.viewCount || 0 }}</span>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-else description="未找到匹配职位" />
    </main>

    <!-- 分页器 (Apple Style) -->
    <footer class="hall-footer" v-if="total > 0">
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :page-sizes="[12, 24, 48]"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="handlePageChange"
      />
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, reactive, computed, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { ElCollapseTransition } from 'element-plus';
import { searchJobs, getCategoryList } from '@/api/job';
import { getMyResume } from '@/api/applicant';
import { useUserStore } from '@/stores/user';
import { formatFileUrl } from '@/utils/file'
import { formatSalaryK } from '@/utils/format'
import { Search, View, InfoFilled, ArrowDown, Close } from '@element-plus/icons-vue';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const jobList = ref([]);
const total = ref(0);
const matchDetailOpen = ref(false);
const categoryPanelVisible = ref(false);
const categoryKeyword = ref('');
const activeCategoryGroupId = ref(null);
const categoryDropdownRef = ref(null);
const categoryOptionGridRef = ref(null);

const queryParams = reactive({
  current: 1,
  size: 12,
  keyword: '',
  degree: '',
  experience: '',
  categoryId: null,
  minSalary: null,
  maxSalary: null,
  city: '',
  district: '',
  industry: '',
  scale: '',
  financing: '',
  sort: ''
});

const resumePreference = reactive({
  degree: '',
  experience: '',
  minSalary: null,
  maxSalary: null,
  city: ''
});

const degreeOptions = ['大专', '本科', '硕士', '博士', '不限'];
const expOptions = ['应届生', '1-3年', '3-5年', '5-10年', '10年以上', '不限'];
const industryOptions = ['互联网', '餐饮', '零售', '物流', '教育', '医疗健康', '文创', '汽车服务', '制造业', '跨境电商'];
const sortOptions = [
  { label: '智能匹配', value: 'smartMatch' },
  { label: '最新发布', value: 'latest' },
  { label: '薪资最高', value: 'salaryDesc' },
  { label: '薪资最低', value: 'salaryAsc' },
  { label: '热度最高', value: 'viewDesc' }
];

const categoryOptions = ref([]);
// 将树形分类转换为“左大类 + 右细分类”面板结构，外层仍保持筛选胶囊风格
const categoryPanelGroups = computed(() => {
  return (categoryOptions.value || []).map(parent => {
    const children = Array.isArray(parent.children) ? parent.children : []
    const options = children.length
      ? children.map(child => ({
          id: child.id,
          label: child.categoryName,
          parentId: parent.id,
          parentLabel: parent.categoryName
        }))
      : [{
          id: parent.id,
          label: parent.categoryName,
          parentId: parent.id,
          parentLabel: parent.categoryName
        }]
    return {
      id: parent.id,
      label: parent.categoryName,
      options
    }
  })
});
const categoryOwnerMap = computed(() => {
  const map = new Map();
  categoryPanelGroups.value.forEach(group => {
    group.options.forEach(option => map.set(option.id, group.id));
  });
  return map;
});
const categoryLabelMap = computed(() => {
  const map = new Map();
  categoryPanelGroups.value.forEach(group => {
    group.options.forEach(item => map.set(item.id, item.label));
  });
  return map;
});
const selectedCategoryLabel = computed(() => categoryLabelMap.value.get(queryParams.categoryId) || '');
const activeCategoryGroup = computed(() => {
  const groups = categoryPanelGroups.value;
  if (!groups.length) return null;
  const selectedGroupId = categoryOwnerMap.value.get(queryParams.categoryId);
  const targetId = activeCategoryGroupId.value || selectedGroupId || groups[0].id;
  return groups.find(group => group.id === targetId) || groups[0];
});
const filteredCategoryOptions = computed(() => {
  const keyword = categoryKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return activeCategoryGroup.value?.options || [];
  }
  return categoryPanelGroups.value.flatMap(group => group.options
    .filter(option => group.label.toLowerCase().includes(keyword) || option.label.toLowerCase().includes(keyword))
    .map(option => ({
      ...option,
      parentLabel: group.label
    }))
  );
});

const isApplicant = computed(() => userStore.role === 'APPLICANT' && !!userStore.token);
const activeSmartPreference = computed(() => {
  const degree = queryParams.degree && queryParams.degree !== '不限'
    ? normalizeDegree(queryParams.degree)
    : resumePreference.degree;
  const experience = queryParams.experience && queryParams.experience !== '不限'
    ? normalizeExperience(queryParams.experience)
    : resumePreference.experience;
  const minSalary = Number(queryParams.minSalary || 0) > 0
    ? Number(queryParams.minSalary)
    : (Number(resumePreference.minSalary || 0) > 0 ? Number(resumePreference.minSalary) : null);
  return { degree, experience, minSalary };
});

const smartHintText = computed(() => {
  if (!isApplicant.value || queryParams.sort !== 'smartMatch') return '已启用智能匹配（硬性条件优先）。';
  const chips = [];
  if (activeSmartPreference.value.degree) chips.push(`学历不高于 ${activeSmartPreference.value.degree}`);
  if (activeSmartPreference.value.experience) chips.push(`经验不高于 ${activeSmartPreference.value.experience}`);
  if (activeSmartPreference.value.minSalary) chips.push(`薪资不低于 ${activeSmartPreference.value.minSalary}k`);
  if (chips.length === 0) return '已启用智能匹配（硬性条件优先）';
  return `已启用硬性匹配：${chips.join(' · ')}`;
});
const hardRuleTags = computed(() => {
  const tags = [];
  if (activeSmartPreference.value.degree) tags.push(`学历 ≤ ${activeSmartPreference.value.degree}`);
  if (activeSmartPreference.value.experience) tags.push(`经验 ≤ ${activeSmartPreference.value.experience}`);
  if (activeSmartPreference.value.minSalary) tags.push(`薪资 ≥ ${activeSmartPreference.value.minSalary}k`);
  return tags;
});
const manualRuleTags = computed(() => {
  const tags = [];
  // 展示用户主动设置的筛选条件，便于区分“自动”与“手动”来源
  if (queryParams.keyword) tags.push(`关键词：${queryParams.keyword}`);
  if (queryParams.categoryId) {
    tags.push(`分类：${categoryLabelMap.value.get(queryParams.categoryId) || queryParams.categoryId}`);
  }
  if (queryParams.industry) tags.push(`行业：${queryParams.industry}`);
  if (queryParams.city) tags.push(`城市：${queryParams.city}`);
  if (queryParams.district) tags.push(`区域：${queryParams.district}`);
  if (queryParams.degree && queryParams.degree !== '不限') tags.push(`学历筛选：${queryParams.degree}`);
  if (queryParams.experience && queryParams.experience !== '不限') tags.push(`经验筛选：${queryParams.experience}`);
  if (queryParams.minSalary) tags.push(`最低薪资：${queryParams.minSalary}k`);
  if (queryParams.maxSalary) tags.push(`最高薪资：${queryParams.maxSalary}k`);
  return tags;
});

const DEGREE_RANK = { 大专: 1, 本科: 2, 硕士: 3, 博士: 4, 不限: 0 };
const EXPERIENCE_RANK = { 应届生: 1, '1-3年': 2, '3-5年': 3, '5-10年': 4, '10年以上': 5, 不限: 0 };

const normalizeDegree = (rawDegree) => {
  const text = String(rawDegree || '').trim();
  if (!text) return '';
  if (text.includes('博')) return '博士';
  if (text.includes('硕')) return '硕士';
  if (text.includes('本')) return '本科';
  if (text.includes('专')) return '大专';
  return '';
};

const normalizeExperience = (workYears) => {
  const text = String(workYears || '').trim();
  if (!text) return '';
  if (text.includes('不限')) return '不限';
  if (text.includes('应届') || text.includes('在校')) return '应届生';
  const match = text.match(/(\d+(\.\d+)?)/);
  if (!match) return '';
  const years = Number(match[1]);
  if (Number.isNaN(years) || years < 1) return '应届生';
  if (years < 3) return '1-3年';
  if (years < 5) return '3-5年';
  if (years < 10) return '5-10年';
  return '10年以上';
};

const parseSalaryRange = (basicInfo) => {
  const min = Number(basicInfo?.expectSalaryMin);
  const max = Number(basicInfo?.expectSalaryMax);
  if (Number.isFinite(min) && Number.isFinite(max) && min > 0 && max > 0) {
    return { minSalary: Math.min(min, max), maxSalary: Math.max(min, max) };
  }
  const rawSalary = String(basicInfo?.expectSalary || '').toLowerCase();
  const nums = rawSalary.match(/(\d+(\.\d+)?)/g);
  if (!nums || nums.length < 2) return { minSalary: null, maxSalary: null };
  const first = Number(nums[0]);
  const second = Number(nums[1]);
  if (!Number.isFinite(first) || !Number.isFinite(second)) return { minSalary: null, maxSalary: null };
  return { minSalary: Math.min(first, second), maxSalary: Math.max(first, second) };
};

const buildSmartScore = (job) => {
  const prefDegree = activeSmartPreference.value.degree;
  const prefExp = activeSmartPreference.value.experience;
  const prefMinSalary = activeSmartPreference.value.minSalary;
  let score = 0;
  const jobDegree = String(job?.degree || '').trim();
  const jobExperience = String(job?.experience || '').trim();
  const jobMin = Number(job?.minSalary ?? job?.min_salary ?? 0);
  const jobMax = Number(job?.maxSalary ?? job?.max_salary ?? 0);
  const viewCount = Number(job?.viewCount || 0);
  const rankGapDegree = (DEGREE_RANK[jobDegree] ?? 0) - (DEGREE_RANK[prefDegree] ?? 0);
  const rankGapExp = (EXPERIENCE_RANK[jobExperience] ?? 0) - (EXPERIENCE_RANK[prefExp] ?? 0);

  if (prefDegree) {
    if (!jobDegree || jobDegree === '不限') score += 12;
    else score += rankGapDegree >= 0 ? 26 : Math.max(0, 18 + rankGapDegree * 4);
  }
  if (prefExp) {
    if (!jobExperience || jobExperience === '不限') score += 10;
    else score += rankGapExp <= 0 ? 24 : Math.max(0, 16 - rankGapExp * 4);
  }
  if (prefMinSalary && resumePreference.maxSalary && jobMin > 0 && jobMax > 0) {
    const overlap = Math.min(jobMax, resumePreference.maxSalary) - Math.max(jobMin, prefMinSalary);
    score += overlap >= 0 ? 24 : (jobMax >= prefMinSalary ? 12 : 0);
  }
  if (resumePreference.city) {
    score += String(job?.workLocation || '').trim() === resumePreference.city ? 14 : 0;
  }
  score += Math.min(viewCount, 180) / 30;
  return score;
};

const loadResumePreference = async () => {
  if (!isApplicant.value) return;
  try {
    const res = await getMyResume();
    const basicInfo = res?.data?.basicInfo || {};
    const degree = normalizeDegree(basicInfo.degree);
    const experience = normalizeExperience(basicInfo.workYears);
    const city = String(basicInfo.expectCity || '').trim();
    const salaryRange = parseSalaryRange(basicInfo);
    Object.assign(resumePreference, {
      degree,
      experience,
      city,
      minSalary: salaryRange.minSalary,
      maxSalary: salaryRange.maxSalary
    });
    if (!queryParams.sort) queryParams.sort = 'smartMatch';
  } catch (error) {
    console.warn('读取简历偏好失败，已降级为普通筛选模式', error);
  }
};

const handleSearch = async () => {
  loading.value = true;
  try {
    const payload = { ...queryParams };
    if (payload.degree === '不限') payload.degree = '';
    if (payload.experience === '不限') payload.experience = '';
    if (payload.sort === 'smartMatch') {
      payload.sort = 'latest';
      payload.smartDegreeCap = activeSmartPreference.value.degree || '';
      payload.smartExperienceCap = activeSmartPreference.value.experience || '';
      payload.smartMinSalary = activeSmartPreference.value.minSalary || null;
    }
    const res = await searchJobs(payload);
    let records = res.data?.records || [];
    if (queryParams.sort === 'smartMatch') {
      records.sort((a, b) => buildSmartScore(b) - buildSmartScore(a));
    }
    jobList.value = records;
    total.value = Number(res.data?.total) || 0;
  } finally { loading.value = false; }
};

const handleFilterChange = () => { queryParams.current = 1; handleSearch(); };
const handlePageChange = () => { handleSearch(); window.scrollTo({ top: 0, behavior: 'smooth' }); };
const resetCategoryOptionScroll = () => {
  nextTick(() => {
    if (categoryOptionGridRef.value) {
      categoryOptionGridRef.value.scrollTop = 0;
    }
  });
};

const toggleCategoryPanel = () => {
  if (!categoryPanelVisible.value) {
    const ownerId = categoryOwnerMap.value.get(queryParams.categoryId);
    if (ownerId) {
      activeCategoryGroupId.value = ownerId;
    }
  }
  categoryPanelVisible.value = !categoryPanelVisible.value;
  if (categoryPanelVisible.value) {
    resetCategoryOptionScroll();
  }
};
const closeCategoryPanel = () => {
  categoryPanelVisible.value = false;
  categoryKeyword.value = '';
};
const setActiveCategoryGroup = (groupId) => {
  activeCategoryGroupId.value = groupId;
  categoryKeyword.value = '';
  resetCategoryOptionScroll();
};
const selectCategoryOption = (option) => {
  if (!option?.id) return;
  queryParams.categoryId = option.id;
  activeCategoryGroupId.value = option.parentId || categoryOwnerMap.value.get(option.id) || activeCategoryGroupId.value;
  closeCategoryPanel();
  handleFilterChange();
};
const clearCategoryFilter = () => {
  if (!queryParams.categoryId) {
    closeCategoryPanel();
    return;
  }
  queryParams.categoryId = null;
  closeCategoryPanel();
  handleFilterChange();
};
const handleReset = () => {
  Object.keys(queryParams).forEach(key => queryParams[key] = key === 'current' ? 1 : (key === 'size' ? 12 : null));
  queryParams.keyword = ''; queryParams.city = ''; queryParams.district = ''; queryParams.industry = '';
  closeCategoryPanel();
  handleSearch();
};

const toDetail = (id) => {
  const routeData = router.resolve({ path: `/job/detail/${id}` });
  window.open(routeData.href, '_blank');
};

onMounted(async () => {
  await Promise.all([
    loadResumePreference(),
    getCategoryList().then(res => { categoryOptions.value = res.data || []; })
  ]);
  document.addEventListener('click', handleDocumentClick, true);
  document.addEventListener('keydown', handleDocumentKeydown);
  handleSearch();
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick, true);
  document.removeEventListener('keydown', handleDocumentKeydown);
});

watch(categoryPanelGroups, (groups) => {
  if (!groups.length) return;
  const hasActive = groups.some(group => group.id === activeCategoryGroupId.value);
  if (!hasActive) {
    activeCategoryGroupId.value = groups[0].id;
  }
}, { immediate: true });

watch(() => queryParams.categoryId, (categoryId) => {
  const ownerId = categoryOwnerMap.value.get(categoryId);
  if (ownerId) {
    activeCategoryGroupId.value = ownerId;
  }
});

watch(categoryKeyword, () => {
  if (!categoryPanelVisible.value) return;
  resetCategoryOptionScroll();
});

const handleDocumentClick = (event) => {
  if (!categoryPanelVisible.value) return;
  const root = categoryDropdownRef.value;
  if (!root) return;
  if (root.contains(event.target)) return;
  closeCategoryPanel();
};

const handleDocumentKeydown = (event) => {
  if (event.key === 'Escape' && categoryPanelVisible.value) {
    closeCategoryPanel();
  }
};
</script>

<style scoped>
.job-hall-page {
  position: relative;
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width));
  margin: 0 auto;
  padding: 32px 0 60px;
  overflow: visible;
}

.job-hall-page::before,
.job-hall-page::after {
  content: '';
  position: absolute;
  inset: auto;
  pointer-events: none;
  filter: blur(4px);
  z-index: 0;
}

.job-hall-page::before {
  top: 24px;
  left: 2%;
  width: min(26vw, 280px);
  height: min(26vw, 280px);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(10, 132, 255, 0.13), rgba(10, 132, 255, 0) 72%);
}

.job-hall-page::after {
  top: 140px;
  right: 4%;
  width: min(24vw, 240px);
  height: min(24vw, 240px);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(84, 160, 255, 0.12), rgba(84, 160, 255, 0) 72%);
}

/* --- 1. Hall Header --- */
.hall-header {
  position: relative;
  z-index: 12;
  isolation: isolate;
  margin-bottom: 40px;
  padding: clamp(20px, 2.2vw, 28px);
  border-radius: 28px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(255, 255, 255, 0.72)),
    linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0.03) 46%, rgba(138, 180, 248, 0.08));
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.08);
  text-align: center;
  overflow: visible;
}

.hall-header::before {
  content: '';
  position: absolute;
  inset: -10% auto auto -6%;
  width: min(30vw, 340px);
  height: min(30vw, 340px);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0) 70%);
  pointer-events: none;
}

.hall-title {
  position: relative;
  z-index: 1;
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 24px;
  letter-spacing: -0.5px;
}

.search-hero { max-width: min(880px, 100%); margin: 0 auto 32px; }
.search-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 252, 255, 0.94));
  padding: 8px 8px 8px 18px;
  border-radius: 999px;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.82);
  transition: all var(--ui-motion-slow) var(--ui-ease-standard);
}

.search-wrapper:focus-within {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(0, 113, 227, 0.12);
  border-color: #007aff;
}

.apple-search-input :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: none !important;
  border: none !important;
  padding: 0 !important;
}

.apple-search-input {
  flex: 1 1 320px;
  min-width: 0;
}

.apple-search-input :deep(.el-input__inner) {
  font-size: 16px;
  color: #1d1d1f;
  outline: none !important;
}

.search-icon { font-size: 18px; color: #86868b; }
.search-btn {
  height: 36px;
  padding: 0 24px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  transition: all var(--ui-motion-base) var(--ui-ease-standard);
}
.search-btn:hover { box-shadow: 0 8px 20px rgba(0, 113, 227, 0.25); }

/* --- 2. Pill Filter Bar (Apple 风格统一重塑) --- */
.pill-filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  position: relative;
  z-index: 4;
  padding: 12px;
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(135deg, rgba(244, 248, 255, 0.97), rgba(237, 244, 255, 0.94));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.filter-pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1 1 760px;
  min-width: 0;
}

.pill-select {
  flex: 1 1 140px;
  min-width: 140px;
  max-width: 220px;
}

.category-dropdown {
  position: relative;
  z-index: 8;
  flex: 1 1 184px;
  min-width: 184px;
  max-width: 248px;
  overflow: visible;
}

.category-pill-shell {
  display: block;
  width: 100%;
}

.category-pill-select {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 12px 0 16px;
  border-radius: 999px;
  background: #f2f2f7;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all var(--ui-motion-slow) var(--ui-ease-standard);
}

.category-pill-select:hover {
  background: #e5e5ea;
}

.category-pill-select.is-active {
  background: #ffffff;
  border-color: #007aff;
  box-shadow: 0 0 0 1px #007aff inset;
}

.category-pill-select.has-value {
  color: #0a84ff;
}

.category-pill-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  font-size: 13px;
}

.category-pill-clear {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.08);
  color: #6e6e73;
  cursor: pointer;
  flex-shrink: 0;
}

.category-pill-clear:hover {
  background: rgba(10, 132, 255, 0.12);
  color: #0a84ff;
}

.category-pill-arrow {
  color: #8e8e93;
  font-size: 13px;
  flex-shrink: 0;
  transition: transform var(--ui-motion-fast) var(--ui-ease-standard);
}

.category-pill-select.is-active .category-pill-arrow {
  transform: rotate(180deg);
}

.category-panel-pop {
  position: absolute;
  top: calc(100% + 14px);
  left: 0;
  z-index: 80;
}

.category-panel {
  position: relative;
  width: min(700px, calc(100vw - 40px));
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(18px);
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.16);
}

.category-panel::before {
  content: '';
  position: absolute;
  top: -7px;
  left: 32px;
  width: 14px;
  height: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  border-left: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.96);
  transform: rotate(45deg);
}

.category-panel-fade-enter-active,
.category-panel-fade-leave-active {
  transition:
    opacity var(--ui-motion-fast) var(--ui-ease-standard),
    transform var(--ui-motion-fast) var(--ui-ease-standard);
}

.category-panel-fade-enter-from,
.category-panel-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.category-panel-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.category-panel-headline {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.category-panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
}

.category-panel-tip {
  font-size: 12px;
  line-height: 1.5;
  color: #6e6e73;
}

.category-panel-search {
  width: clamp(200px, 30vw, 260px);
  flex-shrink: 0;
}

.category-panel-search :deep(.el-input__wrapper) {
  min-height: 38px;
  border-radius: 12px;
  background: #f7f8fb;
  box-shadow: none !important;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.category-panel-body {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 14px;
  min-height: 320px;
}

.category-group-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 340px;
  overflow: auto;
  padding-right: 4px;
}

.category-group-tab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid transparent;
  background: #f5f5f7;
  color: #3a3a3c;
  cursor: pointer;
  transition: all var(--ui-motion-fast) var(--ui-ease-standard);
}

.category-group-tab:hover {
  background: #ececf1;
}

.category-group-tab.is-active {
  background: rgba(10, 132, 255, 0.1);
  border-color: rgba(10, 132, 255, 0.18);
  color: #0a84ff;
}

.category-group-count {
  min-width: 22px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  font-size: 11px;
  color: #6e6e73;
}

.category-option-area {
  min-width: 0;
  padding: 12px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, #fcfcfe 0%, #f7f8fb 100%);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-option-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.category-option-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}

.category-option-meta {
  font-size: 12px;
  color: #8e8e93;
}

.category-option-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  gap: 10px;
  max-height: 280px;
  overflow: auto;
  padding-right: 4px;
}

.category-choice {
  position: relative;
  min-height: 82px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: all var(--ui-motion-fast) var(--ui-ease-standard);
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 8px;
}

.category-choice:hover {
  transform: translateY(-1px);
  border-color: rgba(10, 132, 255, 0.24);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.08);
}

.category-choice.is-selected {
  border-color: rgba(10, 132, 255, 0.28);
  background: linear-gradient(180deg, rgba(10, 132, 255, 0.08), rgba(255, 255, 255, 0.98));
}

.category-choice-name {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
  line-height: 1.5;
}

.category-choice-meta {
  font-size: 11px;
  color: #8e8e93;
}

.category-choice-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(10, 132, 255, 0.12);
  color: #0a84ff;
  font-size: 11px;
  font-weight: 600;
}

.category-option-empty {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  border: 1px dashed rgba(148, 163, 184, 0.5);
  background: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  color: #8e8e93;
}

/* 统一筛选胶囊控件（select / cascader）外观 */
.pill-select :deep(.el-select__wrapper),
.pill-select :deep(.el-input__wrapper) {
  border-radius: 999px !important;
  background-color: #f2f2f7 !important;
  box-shadow: none !important;
  border: 1px solid transparent !important;
  height: 34px !important;
  padding: 0 16px !important;
  min-height: 34px !important;
  line-height: 34px !important;
  font-size: 13px !important;
  transition: all var(--ui-motion-slow) var(--ui-ease-standard);
}

.pill-select :deep(.el-select__placeholder),
.pill-select :deep(.el-input__inner),
.pill-select :deep(.el-input__inner::placeholder) {
  color: #6e6e73 !important;
  font-size: 13px !important;
}

/* 悬浮效果 */
.pill-select :deep(.el-select__wrapper:hover),
.pill-select :deep(.el-input__wrapper:hover) {
  background-color: #e5e5ea !important;
}

/* 激活反馈 */
.pill-select :deep(.el-select__wrapper.is-focused),
.pill-select :deep(.el-input.is-focus .el-input__wrapper),
.pill-select :deep(.el-input__wrapper.is-focus) {
  background-color: #ffffff !important;
  border-color: #007aff !important;
  box-shadow: 0 0 0 1px #007aff inset !important;
}

/* 统一清空图标/箭头的视觉高度 */
.pill-select :deep(.el-select__caret),
.pill-select :deep(.el-input__suffix-inner) {
  color: #8e8e93 !important;
  font-size: 13px !important;
}

.reset-link { font-size: 13px; color: #86868b; margin-left: 0; flex-shrink: 0; }
.match-brief-bar {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid rgba(10, 132, 255, 0.12);
  background: linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0.03) 52%, rgba(191, 219, 254, 0.12));
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.brief-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.brief-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #0a84ff;
  flex-shrink: 0;
}
.brief-text {
  font-size: 12px;
  color: #3a3a3c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.brief-toggle-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #0a84ff;
  background: rgba(10, 132, 255, 0.1);
  border: 1px solid rgba(10, 132, 255, 0.2);
}
.brief-toggle-icon :deep(.el-icon) {
  font-size: 13px;
}
.brief-toggle-icon:hover {
  background: rgba(10, 132, 255, 0.16);
}
.match-layer-panel {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid #e5e5ea;
  border-radius: 10px;
  background: #fafafd;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.layer-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.layer-title {
  min-width: 92px;
  font-size: 12px;
  color: #6e6e73;
  line-height: 24px;
}
.layer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.layer-tag {
  font-size: 12px;
  line-height: 18px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
}
.layer-tag.hard {
  color: #0a84ff;
  background: rgba(10, 132, 255, 0.1);
  border-color: rgba(10, 132, 255, 0.25);
}
.layer-tag.soft {
  color: #30a46c;
  background: rgba(48, 164, 108, 0.1);
  border-color: rgba(48, 164, 108, 0.25);
}
.layer-empty {
  font-size: 12px;
  color: #8e8e93;
  line-height: 24px;
}

/* --- 3. Job Grid --- */
.job-grid-hall {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: clamp(18px, 1.5vw, 22px);
  position: relative;
  z-index: 1;
}

.job-hall-card {
  --card-accent: #0a84ff;
  --card-glow: rgba(10, 132, 255, 0.14);
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(249, 250, 252, 0.94));
  border-radius: 20px;
  padding: 20px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.02);
  cursor: pointer;
  transition: all var(--ui-motion-slow) var(--ui-ease-standard);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.job-hall-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, var(--card-accent, #0a84ff), rgba(255, 255, 255, 0));
}

.job-hall-card::after {
  content: '';
  position: absolute;
  top: -42px;
  right: -36px;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--card-glow, rgba(10, 132, 255, 0.16)), rgba(255, 255, 255, 0) 68%);
  pointer-events: none;
}

.job-hall-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.1);
  border-color: rgba(10, 132, 255, 0.18);
}

.card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.job-title-text { font-size: 16px; font-weight: 700; color: #1d1d1f; margin: 0; }
.company-sub { font-size: 13px; color: #86868b; margin: 2px 0 0; }
.job-price { font-size: 16px; font-weight: 700; color: var(--card-accent, #0a84ff); }

.tag-cloud { display: flex; gap: 6px; flex-wrap: wrap; }
.tag-item {
  font-size: 11px;
  color: #5c6470;
  background: rgba(255, 255, 255, 0.76);
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.card-footer-hall {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #f5f5f7;
}

.publisher { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #1d1d1f; }
.view-count { display: flex; align-items: center; gap: 4px; font-size: 11px; color: #8e8e93; }

.hall-footer { margin-top: 40px; display: flex; justify-content: center; }

@media (max-width: 900px) {
  .job-hall-page {
    padding: 24px 0 48px;
  }

  .hall-header {
    text-align: left;
  }

  .pill-filter-bar {
    align-items: stretch;
  }

  .reset-link {
    width: 100%;
    text-align: left;
  }

  .brief-text {
    white-space: normal;
  }

  .layer-row {
    flex-direction: column;
    gap: 4px;
  }

  .layer-title {
    min-width: 0;
    line-height: 1.5;
  }
}

@media (max-width: 640px) {
  .hall-title {
    font-size: 24px;
    margin-bottom: 18px;
  }

  .search-wrapper {
    border-radius: 24px;
    padding: 12px 12px 12px 16px;
  }

  .apple-search-input {
    flex-basis: 100%;
  }

  .search-btn {
    width: 100%;
    height: 40px;
  }

  .pill-select {
    max-width: none;
    min-width: 0;
    flex-basis: calc(50% - 4px);
  }

  .category-pill-shell {
    min-width: 0;
  }

  .category-dropdown {
    max-width: none;
    flex-basis: calc(50% - 4px);
  }

  .category-panel-pop {
    left: 0;
  }

  .category-panel {
    width: min(100vw - 24px, 680px);
    padding: 14px;
  }

  .category-panel-top {
    flex-direction: column;
  }

  .category-panel-search {
    width: 100%;
  }

  .category-panel-body {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .category-group-nav {
    flex-direction: row;
    overflow: auto hidden;
    max-height: none;
    padding-right: 0;
    padding-bottom: 4px;
  }

  .category-group-tab {
    width: auto;
    min-width: max-content;
  }
}

@media (max-width: 480px) {
  .pill-select {
    flex-basis: 100%;
  }

  .category-pill-shell {
    width: 100%;
  }

  .category-dropdown {
    flex-basis: 100%;
  }

  .match-brief-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .brief-toggle-icon {
    align-self: flex-end;
  }
}

</style>

