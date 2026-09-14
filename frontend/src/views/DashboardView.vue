<template>
  <div class="page" v-loading="loading">
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">总投递</div>
        <div class="stat-value">{{ data.totalApplications }}</div>
        <div class="stat-extra">另有 {{ data.wishlistCount }} 个职位在收藏夹</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">进入面试</div>
        <div class="stat-value" style="color: #4f46e5">{{ data.interviewCount }}</div>
        <div class="stat-extra">面试率 {{ data.interviewRate }}%</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">拿到 Offer</div>
        <div class="stat-value" style="color: #16a34a">{{ data.offerCount }}</div>
        <div class="stat-extra">Offer 率 {{ data.offerRate }}%</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">被拒绝</div>
        <div class="stat-value" style="color: #ef4444">{{ data.rejectedCount }}</div>
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
        <div class="stat-label">目标公司</div>
        <div class="stat-value">{{ data.companyCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">职位</div>
        <div class="stat-value">{{ data.jobCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">简历版本</div>
        <div class="stat-value">{{ data.resumeCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">题库题目</div>
        <div class="stat-value">{{ data.questionCount }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onActivated, onBeforeUnmount, onMounted, reactive, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi, interviewApi } from '@/api'

const loading = ref(false)
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
      axisLabel: { color: '#8c939d' },
      axisLine: { lineStyle: { color: '#e5e7eb' } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f1f2f5' } },
      axisLabel: { color: '#8c939d' }
    },
    series: [
      {
        name: '投递量',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: data.trend.map((item) => item.value),
        itemStyle: { color: '#4f46e5' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(79,70,229,0.28)' },
            { offset: 1, color: 'rgba(79,70,229,0.02)' }
          ])
        }
      }
    ]
  })

  const statusChart = echarts.init(statusRef.value)
  statusChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#8c939d' } },
    color: ['#94a3b8', '#3b82f6', '#f59e0b', '#4f46e5', '#16a34a', '#ef4444', '#6b7280'],
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
          .map((item) => ({ name: item.label, value: item.value }))
      }
    ]
  })

  const companyChart = echarts.init(companyRef.value)
  companyChart.setOption({
    grid: { left: 90, right: 30, top: 20, bottom: 20 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f1f2f5' } } },
    yAxis: {
      type: 'category',
      inverse: true,
      data: data.topCompanies.map((item) => item.name),
      axisLabel: { color: '#4b5563' },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        itemStyle: { color: '#6366f1', borderRadius: [0, 7, 7, 0] },
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
.grid-2 {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
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
</style>
