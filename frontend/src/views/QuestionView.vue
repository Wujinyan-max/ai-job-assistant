<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.category" placeholder="全部题型" clearable style="width: 160px" @change="load">
        <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="query.mastered" placeholder="掌握情况" clearable style="width: 140px" @change="load">
        <el-option label="已掌握" :value="1" />
        <el-option label="未掌握" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" :icon="MagicStick" @click="$router.push('/ai')">去 AI 出题</el-button>
    </div>

    <el-empty v-if="!loading && !rows.length" description="题库还是空的，去 AI 助手生成一批面试题吧" />

    <div v-loading="loading" class="question-list">
      <div v-for="item in rows" :key="item.id" class="card question-card">
        <div class="question-head">
          <div>
            <el-tag size="small" effect="light">{{ item.category || '综合' }}</el-tag>
            <el-tag size="small" :type="difficultyType(item.difficulty)" effect="plain" style="margin-left: 6px">
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
                   :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Search } from '@element-plus/icons-vue'
import { questionApi } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, category: '', mastered: null })

const difficultyLabel = (value) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' }[value] || value)
const difficultyType = (value) => ({ EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[value] || 'info')

async function load() {
  loading.value = true
  try {
    const data = await questionApi.page(query)
    rows.value = data.records
    total.value = data.total
    categories.value = await questionApi.categories()
  } finally {
    loading.value = false
  }
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
  load()
}

onMounted(load)
</script>

<style scoped>
.question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

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
