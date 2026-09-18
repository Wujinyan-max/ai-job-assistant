<template>
  <div class="page" v-loading="loading">
    <section class="dashboard-hero">
      <div class="hero-main">
        <div class="page-kicker">Overview · 数据看板</div>
        <h1 class="hero-title">{{ greeting }}，{{ userName }}</h1>
        <p class="hero-desc">{{ today }} · 继续向前，机会会在认真准备的人身上发生。</p>
      </div>
      <div class="hero-quote-card">
        <div class="quote-label">今日一句</div>
        <div class="quote-text">"机会是留给准备充分的人。"</div>
      </div>
    </section>

    <!-- 面试提醒：有未来 7 天的面试才出现，点进去直接看面试列表 -->
    <div v-if="upcoming.length" class="interview-alert" @click="goInterviews">
      <span class="alert-dot">{{ upcoming.length }}</span>
      <span class="alert-text">
        {{ upcoming.length }} 场面试在 7 天内，最近一场
        {{ interviewTimeLabel(upcoming[0].interviewTime) }}
        <template v-if="upcoming[0].companyName"> · {{ upcoming[0].companyName }}</template>
      </span>
      <el-icon class="alert-arrow"><ArrowRight /></el-icon>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">总投递</div>
          <span class="stat-icon is-brand"><el-icon><Promotion /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.totalApplications }}</div>
        <div class="stat-extra">另有 {{ data.wishlistCount }} 个职位在收藏夹</div>
      </div>
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">进入面试</div>
          <span class="stat-icon is-brand"><el-icon><ChatLineSquare /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.interviewCount }}</div>
        <div class="stat-extra">面试率 {{ data.interviewRate }}%</div>
      </div>
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">拿到 Offer</div>
          <span class="stat-icon is-success"><el-icon><Trophy /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.offerCount }}</div>
        <div class="stat-extra">Offer 率 {{ data.offerRate }}%</div>
      </div>
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">被拒绝</div>
          <span class="stat-icon is-danger"><el-icon><CircleClose /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.rejectedCount }}</div>
        <div class="stat-extra">不要气馁，继续投</div>
      </div>
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="card">
        <div class="card-title">
          <span>最近 {{ trendDays }} 天投递趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="load">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
            <el-radio-button :value="90">90 天</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="trendRef" class="chart"></div>
      </div>

      <div class="card">
        <div class="card-title"><span>投递状态分布</span></div>
        <div ref="statusRef" class="chart"></div>
      </div>
    </div>

    <div class="grid-2" style="margin-top: 16px">
      <div class="card">
        <div class="card-title"><span>投递最多的公司</span></div>
        <div ref="companyRef" class="chart" style="height: 280px"></div>
      </div>

      <div class="card">
        <div class="card-title">
          <span>近期面试安排</span>
          <el-link type="primary" underline="never" @click="$router.push('/interviews')">查看全部</el-link>
        </div>
        <el-empty v-if="!upcoming.length" description="最近 7 天没有面试安排" :image-size="80" />
        <el-timeline v-else style="padding-left: 4px; margin-top: 8px">
          <el-timeline-item v-for="item in upcoming" :key="item.id" type="primary"
                            :timestamp="`${item.interviewTime} · ${typeLabel(item.interviewType)}`">
            <div class="interview-title">{{ item.companyName }} · {{ item.jobName }}</div>
            <div class="muted" style="font-size: 12px">
              {{ item.roundName }}
              <span v-if="item.interviewer"> · 面试官：{{ item.interviewer }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>

    <div class="card" style="margin-top: 16px">
      <div class="card-title">
        <span>简历效果分析</span>
        <el-radio-group v-model="reportDays" size="small" @change="loadReport">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
          <el-radio-button :value="90">90 天</el-radio-button>
        </el-radio-group>
      </div>
      <el-empty v-if="!report.length" description="先投递几份简历再来看看" :image-size="80" />
      <el-table v-else :data="report" size="small">
        <el-table-column label="简历版本" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: !row.resumeId }">{{ row.resumeTitle }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="applicationCount" label="投递" width="80" align="center" />
        <el-table-column prop="interviewCount" label="面试" width="80" align="center" />
        <el-table-column prop="offerCount" label="Offer" width="80" align="center" />
        <el-table-column label="面试率" width="110">
          <template #default="{ row }">
            <span class="rate-value">{{ row.interviewRate }}%</span>
            <el-progress :percentage="row.interviewRate" :show-text="false" :stroke-width="4" />
          </template>
        </el-table-column>
        <el-table-column label="Offer 率" width="110">
          <template #default="{ row }">
            <span class="rate-value is-offer">{{ row.offerRate }}%</span>
            <el-progress :percentage="row.offerRate" :show-text="false" :stroke-width="4"
                         :color="SUCCESS" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="stat-grid" style="margin-top: 16px">
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">目标公司</div>
          <span class="stat-icon"><el-icon><OfficeBuilding /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.companyCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">职位</div>
          <span class="stat-icon"><el-icon><Briefcase /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.jobCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">简历版本</div>
          <span class="stat-icon"><el-icon><Document /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.resumeCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-head">
          <div class="stat-label">题库题目</div>
          <span class="stat-icon"><el-icon><Notebook /></el-icon></span>
        </div>
        <div class="stat-value">{{ data.questionCount }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onMounted, reactive, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { dashboardApi, interviewApi } from '@/api'
import { useUserStore } from '@/store/user'
import { interviewTimeLabel } from '@/utils/datetime'
import { ArrowRight } from '@element-plus/icons-vue'
import {
  AXIS_LABEL,
  AXIS_LINE,
  BRAND,
  BRAND_HOVER,
  SPLIT_LINE_STYLE,
  SUCCESS,
  TEXT_REGULAR,
  statusColor
} from '@/utils/theme'

const loading = ref(false)
const router = useRouter()
const userStore = useUserStore()
const userName = computed(() => userStore.user?.nickname || userStore.user?.username || '求职者')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const today = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'
}).format(new Date())
const trendDays = ref(7)
const upcoming = ref([])
const reportDays = ref(30)
const report = ref([])
const data = reactive({
  totalApplications: 0,
  interviewCount: 0,
  offerCount: 0,
  rejectedCount: 0,
  wishlistCount: 0,
  companyCount: 0,
  jobCount: 0,
  resumeCount: 0,
  questionCount: 0,
  interviewRate: 0,
  offerRate: 0,
  statusDistribution: [],
  trend: [],
  topCompanies: []
})

const trendRef = ref()
const statusRef = ref()
const companyRef = ref()
let charts = []

const typeLabel = (type) =>
  ({ PHONE: '电话', VIDEO: '视频', ONSITE: '现场', WRITTEN: '笔试' }[type] || type)

/** 红点点进去直接看面试列表，并带上「只看未来 7 天」的筛选 */
function goInterviews() {
  router.push({ path: '/interviews', query: { range: 7 } })
}

async function load() {
  loading.value = true
  try {
    const [overview, interviews] = await Promise.all([
      dashboardApi.overview(trendDays.value),
      interviewApi.upcoming(7)
    ])
    Object.assign(data, overview)
    upcoming.value = interviews
    syncTitle()
    loadReport()
    await nextTick()
    render()
  } finally {
    loading.value = false
  }
}

/** 简历效果分析：数据与投递记录实时同步，每次进页面都重新拉 */
async function loadReport() {
  report.value = await dashboardApi.resumePerformance(reportDays.value)
}

/**
 * 浏览器标签页标题带上未读面试数，切到别的标签也能看到提醒。
 * 路由守卫会按页面重置标题，这里只负责在首页把它改掉。
 */
function syncTitle() {
  const count = upcoming.value.length
  document.title = count ? `(${count}) 职得 JobPath` : '职得 JobPath'
}

function render() {
  if (!trendRef.value) return
  charts.forEach((chart) => chart.dispose())

  const trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.trend.map((item) => item.date.slice(5)),
      axisLabel: AXIS_LABEL,
      axisLine: AXIS_LINE
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: SPLIT_LINE_STYLE,
      axisLabel: AXIS_LABEL
    },
    series: [
      {
        name: '投递量',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: data.trend.map((item) => item.value),
        itemStyle: { color: BRAND },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(156,124,60,0.16)' },
            { offset: 1, color: 'rgba(156,124,60,0.01)' }
          ])
        }
      }
    ]
  })

  const statusChart = echarts.init(statusRef.value)
  statusChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle', textStyle: AXIS_LABEL },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{c}' },
        data: data.statusDistribution
          .filter((item) => item.value > 0)
          .map((item) => ({
            name: item.label,
            value: item.value,
            itemStyle: { color: statusColor(item.name) }
          }))
      }
    ]
  })

  const companyChart = echarts.init(companyRef.value)
  companyChart.setOption({
    grid: { left: 90, right: 30, top: 20, bottom: 20 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', minInterval: 1, splitLine: SPLIT_LINE_STYLE },
    yAxis: {
      type: 'category',
      inverse: true,
      data: data.topCompanies.map((item) => item.name),
      axisLabel: { color: TEXT_REGULAR },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        itemStyle: { color: BRAND_HOVER, borderRadius: [0, 7, 7, 0] },
        data: data.topCompanies.map((item) => item.value)
      }
    ]
  })

  charts = [trendChart, statusChart, companyChart]
}

function resize() {
  charts.forEach((chart) => chart.resize())
}

onMounted(() => {
  load()
  window.addEventListener('resize', resize)
})

onActivated(load)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  charts.forEach((chart) => chart.dispose())
})
</script>

<style scoped>
.dashboard-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
  padding: 22px 26px;
  background: var(--card-bg);
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.hero-title {
  margin: 4px 0 5px;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--text-primary);
}

.hero-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.hero-quote-card {
  flex: none;
  padding: 12px 18px;
  background: var(--brand-softer);
  border: 1px solid var(--brand-soft);
  border-radius: var(--radius-sm);
  max-width: 260px;
}

/* 7 天内面试提醒条：有面试才渲染，点击进面试列表 */
.interview-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 16px;
  margin-bottom: 16px;
  background: #fdf6ec;
  border: 1px solid #f0dcc0;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease;
}

.interview-alert:hover {
  background: #fbf0df;
  border-color: var(--warning);
}

.alert-dot {
  flex: none;
  min-width: 21px;
  height: 21px;
  padding: 0 6px;
  border-radius: 11px;
  background: var(--warning);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 21px;
  text-align: center;
}

.alert-text {
  flex: 1;
  font-size: 13px;
  color: var(--brand-deep);
}

.alert-arrow {
  flex: none;
  color: var(--warning);
}

.quote-label {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--brand);
  margin-bottom: 5px;
}

.quote-text {
  font-family: var(--font-display);
  font-size: 13.5px;
  color: var(--brand-deep);
  line-height: 1.65;
}

@media (max-width: 700px) {
  .dashboard-hero { flex-direction: column; align-items: flex-start; }
  .hero-quote-card { max-width: none; width: 100%; }
}

.grid-2 {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 12px;
}

@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}



.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 8px;
}

.interview-title {
  font-weight: 600;
  font-size: 14px;
}

/* 简历效果分析的百分比 + 细进度条 */
.rate-value {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand);
}

.rate-value.is-offer {
  color: var(--success);
}
</style>
