<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-kicker">Question Bank</div>
        <h2 class="page-title">面试题库</h2>
        <div class="page-subtitle">
          {{ activeCategory ? '标记掌握情况，展开可看参考答案' : '按技术方向练习，点进分类查看题目' }}
        </div>
      </div>
    </div>

    <!-- 一层：分类总览，一个分类一个框 -->
    <template v-if="!activeCategory">
      <div class="toolbar">
        <el-button :icon="Refresh" @click="loadStats">刷新</el-button>
        <div style="flex: 1"></div>
        <el-button type="primary" :icon="MagicStick" @click="$router.push('/ai')">去 AI 出题</el-button>
      </div>

      <el-empty v-if="!loading && !stats.length" description="题库还是空的，去 AI 助手生成一批面试题吧" />

      <div v-loading="loading" class="category-grid">
        <div v-for="item in stats" :key="item.category" class="card category-card"
             @click="openCategory(item.category)">
          <div class="category-head">
            <span class="category-name">{{ item.category }}</span>
            <el-icon class="category-arrow"><ArrowRight /></el-icon>
          </div>
          <div class="category-count"><strong>{{ item.total }}</strong> 道题</div>
          <el-progress :percentage="masteredPercent(item)" :stroke-width="6" :show-text="false" />
          <div class="category-meta">已掌握 {{ item.mastered }} · 未掌握 {{ item.total - item.mastered }}</div>
        </div>
      </div>
    </template>

    <!-- 二层：某个分类下的题目列表 -->
    <template v-else>
      <div class="toolbar">
        <el-button link :icon="Back" @click="backToCategories">全部分类</el-button>
        <el-tag effect="light">{{ activeCategory }}</el-tag>
        <span class="muted">共 {{ total }} 道</span>
        <div style="flex: 1"></div>
        <el-select v-model="query.mastered" placeholder="掌握情况" clearable style="width: 140px"
                   @change="reloadList">
          <el-option label="已掌握" :value="1" />
          <el-option label="未掌握" :value="0" />
        </el-select>
        <el-button :icon="Refresh" @click="reloadList">刷新</el-button>
        <el-button type="primary" :icon="MagicStick" @click="$router.push('/ai')">去 AI 出题</el-button>
      </div>

      <el-empty v-if="!loading && !rows.length" description="这个分类下还没有题目" />

      <div v-loading="loading" class="question-list">
        <div v-for="item in rows" :key="item.id" class="card question-card">
          <div class="question-head">
            <div>
              <el-tag size="small" :type="difficultyType(item.difficulty)" effect="plain">
                {{ difficultyLabel(item.difficulty) }}
              </el-tag>
              <el-tag v-if="item.mastered === 1" size="small" type="success" effect="light" style="margin-left: 6px">已掌握</el-tag>
            </div>
            <div>
              <el-switch :model-value="item.mastered === 1" inline-prompt active-text="会" inactive-text="不会"
                         @change="(value) => onMastered(item, value)" />
              <el-button link type="danger" style="margin-left: 10px" @click="onDelete(item)">删除</el-button>
            </div>
          </div>
          <div class="question-text">{{ item.question }}</div>
          <el-collapse>
            <el-collapse-item title="查看参考答案" name="answer">
              <div class="pre-wrap muted">{{ item.answer || '暂无参考答案' }}</div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

      <el-pagination v-if="total > query.pageSize" style="margin-top: 16px; justify-content: flex-end" background
                     layout="total, prev, pager, next, sizes" :total="total"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :page-sizes="[10, 20, 50]" @current-change="loadList" @size-change="loadList" />
    </template>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Back, MagicStick, Refresh } from '@element-plus/icons-vue'
import { questionApi } from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const stats = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, mastered: null })

// 当前分类放在 URL 上：刷新、浏览器前进后退都能回到原来的分类
const activeCategory = computed(() => (typeof route.query.category === 'string' ? route.query.category : ''))

const difficultyLabel = (value) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' }[value] || value)
const difficultyType = (value) => ({ EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[value] || 'info')
const masteredPercent = (item) => (item.total ? Math.round((item.mastered / item.total) * 100) : 0)

async function loadStats() {
  loading.value = true
  try {
    stats.value = await questionApi.categoryStats()
  } finally {
    loading.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await questionApi.page({ ...query, category: activeCategory.value })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 换分类或换筛选条件时页码要归零，否则会停在上一个分类的页码上查出空列表 */
function reloadList() {
  query.pageNum = 1
  return loadList()
}

function sync() {
  return activeCategory.value ? loadList() : loadStats()
}

function openCategory(category) {
  router.push({ query: { category } })
}

function backToCategories() {
  router.push({ query: {} })
}

async function onMastered(item, value) {
  await questionApi.markMastered(item.id, value)
  item.mastered = value ? 1 : 0
  ElMessage.success(value ? '已标记为掌握' : '已标记为未掌握')
}

async function onDelete(item) {
  await ElMessageBox.confirm('确定删除这道题吗？', '提示', { type: 'warning' })
  await questionApi.remove(item.id)
  ElMessage.success('已删除')
  loadList()
}

watch(() => route.query.category, sync)
onMounted(sync)
onActivated(sync)
</script>

<style scoped>
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 14px;
}

.category-card {
  cursor: pointer;
  transition: border-color .18s ease, transform .18s ease, box-shadow .18s ease;
}

.category-card:hover {
  border-color: var(--brand);
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.category-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.category-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.category-arrow { color: var(--text-placeholder); }

.category-count {
  margin: 10px 0 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.category-count strong {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-right: 2px;
}

.category-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-card {
  position: relative;
  border-left: 3px solid var(--brand-soft);
  transition: border-color .18s ease, transform .18s ease;
}

.question-card:hover { border-left-color: var(--brand); transform: translateX(2px); }

.question-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.question-text {
  font-size: 15px;
  font-weight: 500;
  line-height: 1.7;
  margin: 10px 0 4px;
}
</style>
