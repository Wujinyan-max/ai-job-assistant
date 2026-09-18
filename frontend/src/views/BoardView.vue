<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-kicker">Kanban Board</div>
        <h2 class="page-title">求职看板</h2>
        <div class="page-subtitle">按投递状态分列展示，卡片右下角可以直接把记录移动到其它状态</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="board" v-loading="loading">
      <div v-for="status in orderedStatuses" :key="status" class="column">
        <div class="column-head">
          <span class="dot" :style="{ background: statusColor(status) }"></span>
          <span class="column-title">{{ statuses[status] || status }}</span>
          <span class="column-count">{{ (board[status] || []).length }}</span>
        </div>
        <div class="column-body">
          <el-empty v-if="!(board[status] || []).length" description="暂无" :image-size="46" />
          <div v-for="item in board[status]" :key="item.id" class="kanban-card"
               :class="{ 'is-highlight': item.id === highlightId }">
            <div class="kanban-title">{{ item.jobName || '未知职位' }}</div>
            <div class="muted kanban-company">{{ item.companyName || '未关联公司' }}</div>

            <!-- 卡片信息增强：没有数据的字段直接不渲染，不留「—」占位 -->
            <div v-if="item.applyTime || item.nextInterviewTime || item.matchScore != null" class="kanban-facts">
              <span v-if="item.applyTime" class="kanban-fact">投递于{{ fromNowLabel(item.applyTime) }}</span>
              <span v-if="item.nextInterviewTime" class="kanban-fact is-interview">
                {{ interviewTimeLabel(item.nextInterviewTime) }} 面试
              </span>
              <span v-if="item.matchScore != null" class="kanban-fact is-score"
                    :style="{ color: scoreColor(item.matchScore) }">
                匹配 {{ item.matchScore }} 分
              </span>
            </div>

            <div class="kanban-meta">
              <el-tag v-if="item.interviewCount > 0" size="small" type="warning" effect="light">
                {{ item.interviewCount }} 轮面试
              </el-tag>
              <span v-if="item.source" class="muted kanban-source" :title="`via ${item.source}`">via {{ item.source }}</span>
              <el-dropdown class="kanban-move" trigger="click" @command="(value) => move(item, value)">
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
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { applicationApi } from '@/api'
import { scoreColor, statusColor } from '@/utils/theme'
import { fromNowLabel, interviewTimeLabel } from '@/utils/datetime'

const loading = ref(false)
const board = ref({})
const statuses = ref({})
const orderedStatuses = ref([])

const route = useRoute()
/** 从职位列表「一键投递」跳过来时，把新卡片高亮出来，方便用户确认投的是哪一条 */
const highlightId = ref(null)

watch(() => route.query.highlight, (value) => {
  highlightId.value = value ? Number(value) : null
}, { immediate: true })

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
onActivated(load)
</script>

<style scoped>
.board {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 12px;
  align-items: flex-start;
}

.column {
  position: relative;
  flex: 0 0 246px;
  /* 内容过长（如超长来源名）时也不撑宽列 */
  min-width: 0;
  background: #efede8;
  border: 1px solid #e6e3dc;
  border-radius: 10px;
  padding: 10px;
  min-height: 220px;
}

.column-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 1px 3px 9px;
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
  color: var(--text-secondary);
  font-weight: 400;
}

.column-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kanban-card {
  position: relative;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: var(--shadow-xs);
  transition: transform .18s ease, box-shadow .18s ease;
}

.kanban-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

/* 一键投递跳过来时短暂点亮新卡片 */
.kanban-card.is-highlight {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px var(--brand-soft);
  animation: card-flash 1.6s ease-out 2;
}

@keyframes card-flash {
  0%, 100% { background: #fff; }
  50% { background: var(--brand-softer); }
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
  gap: 6px;
  font-size: 12px;
  margin: 8px 0 0;
}

/* 距今天数 / 下次面试 / 匹配分，三行小字，没有数据的字段不渲染 */
.kanban-facts {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.kanban-fact.is-interview {
  color: var(--brand-deep);
  font-weight: 500;
}

.kanban-fact.is-score {
  font-weight: 600;
}

/* 来源名过长时省略，避免把「移动到」挤出对齐位置 */
.kanban-source {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 「移动到」固定在卡片右下角，和标题、tag、来源同一行对齐 */
.kanban-move {
  flex: none;
  margin-left: auto;
}

.board::-webkit-scrollbar { height: 6px; }
</style>
