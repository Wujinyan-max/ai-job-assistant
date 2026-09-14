<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">求职看板</h2>
        <div class="page-subtitle">按投递状态分列展示，卡片右上角可以直接把记录移动到其它状态</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="board" v-loading="loading">
      <div v-for="status in orderedStatuses" :key="status" class="column">
        <div class="column-head">
          <span class="dot" :style="{ background: colorOf(status) }"></span>
          <span class="column-title">{{ statuses[status] || status }}</span>
          <span class="column-count">{{ (board[status] || []).length }}</span>
        </div>
        <div class="column-body">
          <el-empty v-if="!(board[status] || []).length" description="暂无" :image-size="46" />
          <div v-for="item in board[status]" :key="item.id" class="kanban-card">
            <div class="kanban-title">{{ item.jobName || '未知职位' }}</div>
            <div class="muted kanban-company">{{ item.companyName || '未关联公司' }}</div>
            <div class="kanban-meta">
              <el-tag v-if="item.interviewCount > 0" size="small" type="warning" effect="light">
                {{ item.interviewCount }} 轮面试
              </el-tag>
              <span v-if="item.source" class="muted">via {{ item.source }}</span>
            </div>
            <el-dropdown trigger="click" @command="(value) => move(item, value)">
              <el-button size="small" text type="primary">移动到<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="target in orderedStatuses" :key="target" :command="target"
                                    :disabled="target === item.applicationStatus">
                    {{ statuses[target] || target }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { applicationApi } from '@/api'

const loading = ref(false)
const board = ref({})
const statuses = ref({})
const orderedStatuses = ref([])

const COLORS = {
  WISHLIST: '#94a3b8',
  APPLIED: '#3b82f6',
  WRITTEN_TEST: '#f59e0b',
  INTERVIEW: '#4f46e5',
  OFFER: '#16a34a',
  REJECTED: '#ef4444',
  CLOSED: '#6b7280'
}

const colorOf = (status) => COLORS[status] || '#94a3b8'

async function load() {
  loading.value = true
  try {
    const [boardData, statusMap] = await Promise.all([applicationApi.board(), applicationApi.statuses()])
    board.value = boardData
    statuses.value = statusMap
    orderedStatuses.value = Object.keys(statusMap)
  } finally {
    loading.value = false
  }
}

async function move(item, target) {
  await applicationApi.updateStatus(item.id, target)
  ElMessage.success(`已移动到「${statuses.value[target]}」`)
  load()
}

onMounted(load)
</script>

<style scoped>
.board {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 12px;
  align-items: flex-start;
}

.column {
  flex: 0 0 236px;
  background: #f1f2f5;
  border-radius: 10px;
  padding: 10px;
  min-height: 220px;
}

.column-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px 10px;
  font-size: 13px;
  font-weight: 600;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.column-title {
  flex: 1;
}

.column-count {
  color: #8c939d;
  font-weight: 400;
}

.column-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kanban-card {
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.kanban-title {
  font-weight: 600;
  font-size: 13px;
  line-height: 1.4;
}

.kanban-company {
  font-size: 12px;
  margin-top: 2px;
}

.kanban-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 12px;
  margin: 8px 0 4px;
}
</style>
