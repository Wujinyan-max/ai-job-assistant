<template>
  <div class="page" v-loading="loading">
    <section class="dashboard-hero">
      <div>
        <div class="hero-kicker">今日专注 · 保持节奏</div>
        <h1>{{ greeting }}，{{ userName }}</h1>
        <p>继续向前，机会会在认真准备的人身上发生。</p>
      </div>
      <div class="hero-side">
        <div class="hero-date">{{ today }}</div>
        <div class="hero-quote">“机会是留给准备充分的人。”</div>
      </div>
    </section>
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
import * as echarts from 'echarts'
import { dashboardApi, interviewApi } from '@/api'
import { useUserStore } from '@/store/user'
import {
  AXIS_LABEL,
  AXIS_LINE,
  BRAND,
  BRAND_HOVER,
  SPLIT_LINE_STYLE,
  TEXT_REGULAR,
  statusColor
} from '@/utils/theme'

const loading = ref(false)
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
const trendDays = ref(30)
const upcoming = ref([])
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

async function load() {
  loading.value = true
  try {
    const [overview, interviews] = await Promise.all([
      dashboardApi.overview(trendDays.value),
      interviewApi.upcoming(7)
    ])
    Object.assign(data, overview)
    upcoming.value = interviews
    await nextTick()
    render()
  } finally {
    loading.value = false
  }
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
            { offset: 0, color: 'rgba(59,114,245,0.28)' },
            { offset: 1, color: 'rgba(59,114,245,0.02)' }
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
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  padding: 4px 2px 2px;
}

.dashboard-hero::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -8px;
  width: 220px;
  height: 86px;
  pointer-events: none;
  background: radial-gradient(circle at 70% 60%, rgba(59,114,245,.13), transparent 68%);
}

.hero-kicker {
  color: var(--brand);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.2px;
}

.dashboard-hero h1 {
  margin: 5px 0 3px;
  font-size: 23px;
  letter-spacing: -.5px;
}

.dashboard-hero p, .hero-side { margin: 0; color: var(--text-secondary); font-size: 12px; }
.hero-side { position: relative; z-index: 1; text-align: right; line-height: 1.8; }
.hero-date { color: var(--text-regular); font-weight: 600; }
.hero-quote { color: var(--brand-deep); }

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

@media (max-width: 700px) {
  .dashboard-hero { align-items: flex-start; flex-direction: column; }
  .hero-side { text-align: left; }
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
</style>
