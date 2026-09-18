<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-kicker">Interviews</div>
        <h2 class="page-title">面试管理</h2><div class="page-subtitle">安排面试日程，沉淀每一轮复盘</div></div>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索职位 / 公司 / 面试官" clearable style="width: 240px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.result" placeholder="面试结果" clearable style="width: 140px" @change="load">
        <el-option label="待定" value="PENDING" />
        <el-option label="通过" value="PASS" />
        <el-option label="未通过" value="FAIL" />
      </el-select>
      <el-tag v-if="onlyUpcoming" type="warning" effect="light" closable @close="clearRange">
        只看未来 {{ query.upcomingDays }} 天
      </el-tag>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
    </div>

    <el-alert v-if="upcoming.length" type="warning" :closable="false" show-icon style="margin-bottom: 14px">
      <template #title>
        未来 7 天有 {{ upcoming.length }} 场面试：
        {{ upcoming.map((item) => `${item.interviewTime} ${item.companyName || ''} ${item.roundName}`).join('；') }}
      </template>
    </el-alert>

    <div class="card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="公司 / 职位" min-width="180">
          <template #default="{ row }">
            <div style="font-weight: 500">{{ row.companyName || '未关联公司' }}</div>
            <div class="muted" style="font-size: 12px">{{ row.jobName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="roundName" label="轮次" width="110" />
        <el-table-column label="形式" width="90">
          <template #default="{ row }">{{ typeLabel(row.interviewType) }}</template>
        </el-table-column>
        <el-table-column prop="interviewTime" label="时间" width="165">
          <template #default="{ row }">{{ row.interviewTime || '待定' }}</template>
        </el-table-column>
        <el-table-column prop="interviewer" label="面试官" width="110" />
        <el-table-column label="结果" width="120">
          <template #default="{ row }">
            <el-select :model-value="row.result" size="small" style="width: 100px"
                       @change="(value) => changeResult(row, value)">
              <el-option label="待定" value="PENDING" />
              <el-option label="通过" value="PASS" />
              <el-option label="未通过" value="FAIL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="review" label="复盘" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.review || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="action-bar">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background
                     layout="total, prev, pager, next, sizes" :total="total"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
    </div>

    <el-dialog v-model="dialog.visible" title="编辑面试记录" width="560px">
      <el-form :model="form" label-width="88px">
        <el-form-item label="轮次名称">
          <el-input v-model="form.roundName" placeholder="例如：技术一面" />
        </el-form-item>
        <el-form-item label="面试形式">
          <el-radio-group v-model="form.interviewType">
            <el-radio value="VIDEO">视频</el-radio>
            <el-radio value="PHONE">电话</el-radio>
            <el-radio value="ONSITE">现场</el-radio>
            <el-radio value="WRITTEN">笔试</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面试时间">
          <el-date-picker v-model="form.interviewTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="选择面试时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="面试官">
          <el-input v-model="form.interviewer" placeholder="例如：王工" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="例如：北京·海淀 / 公司 3 号楼" />
        </el-form-item>
        <el-form-item label="会议链接">
          <el-input v-model="form.meetingUrl" placeholder="线上会议链接，例如 https://meeting.tencent.com/xxx" />
        </el-form-item>
        <el-form-item label="面试结果">
          <el-radio-group v-model="form.result">
            <el-radio value="PENDING">待定</el-radio>
            <el-radio value="PASS">通过</el-radio>
            <el-radio value="FAIL">未通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复盘总结">
          <el-input v-model="form.review" type="textarea" :rows="5"
                    placeholder="记下面试官问到的问题、自己答得不好的地方，下次就能针对性准备" />
          <div class="review-actions">
            <el-button link type="primary" :icon="MagicStick" :loading="extracting"
                       :disabled="!canExtract" @click="onExtractQuestions">提取到题库</el-button>
            <span class="muted" style="font-size: 12px">
              {{ canExtract ? '把复盘里的问题拆成题库条目，自动关联职位分类' : '复盘写满 20 字后可用' }}
            </span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Search } from '@element-plus/icons-vue'
import { aiApi, interviewApi } from '@/api'
import { ensureLocalAiNotice } from '@/utils/aiLocalNotice'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const upcoming = ref([])
const extracting = ref(false)

/** 从首页红点跳过来时 URL 上带 range=7，直接作为初始筛选条件 */
const query = reactive({
  pageNum: 1, pageSize: 10, result: '', keyword: '',
  upcomingDays: route.query.range ? Number(route.query.range) : null
})
const dialog = reactive({ visible: false, id: null, jobId: null })
const form = reactive({
  applicationId: null, roundName: '', interviewType: 'VIDEO', interviewTime: '',
  interviewer: '', location: '', meetingUrl: '', result: 'PENDING', review: ''
})

const typeLabel = (type) => ({ PHONE: '电话', VIDEO: '视频', ONSITE: '现场', WRITTEN: '笔试' }[type] || type)

/** 从首页红点跳过来时只看未来 7 天的面试，其它入口进来就是全部 */
const onlyUpcoming = computed(() => query.upcomingDays != null)

/** URL 上的 range 参数决定是否只显示未来面试，刷新和浏览器返回都能保持 */
watch(() => route.query.range, (value) => {
  query.upcomingDays = value ? Number(value) : null
  query.pageNum = 1
  load()
})

/** 取消「只看未来 N 天」，回到全部面试 */
function clearRange() {
  router.push({ path: '/interviews', query: {} })
}

/** 复盘写满 20 字才允许提取，太短的内容拆不出有效题目 */
const canExtract = computed(() => (form.review || '').trim().length >= 20)

async function load() {
  loading.value = true
  try {
    const [page, list] = await Promise.all([interviewApi.page(query), interviewApi.upcoming(7)])
    rows.value = page.records
    total.value = page.total
    upcoming.value = list
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  dialog.id = row.id
  dialog.jobId = row.jobId ?? null
  dialog.visible = true
  Object.assign(form, {
    applicationId: row.applicationId,
    roundName: row.roundName || '',
    interviewType: row.interviewType || 'VIDEO',
    interviewTime: row.interviewTime || '',
    interviewer: row.interviewer || '',
    location: row.location || '',
    meetingUrl: row.meetingUrl || '',
    result: row.result || 'PENDING',
    review: row.review || ''
  })
}

async function onSubmit() {
  saving.value = true
  try {
    const payload = { ...form, interviewTime: form.interviewTime || null }
    const flow = await interviewApi.update(dialog.id, payload)
    ElMessage.success('保存成功')
    dialog.visible = false
    await saveNextStep(dialog.id, payload, flow)
    load()
  } finally {
    saving.value = false
  }
}

/**
 * 面试标记为通过后问一句「然后呢」。
 * <p>后端只在「从非通过改成通过」时返回 awaitingNextStep，
 * 所以补复盘内容再保存不会反复弹窗。取消不改任何状态。</p>
 */
async function saveNextStep(id, payload, flow) {
  if (!flow?.awaitingNextStep) {
    return
  }
  const step = await pickNextStep()
  if (!step) {
    return
  }
  const next = await interviewApi.update(id, { ...payload, nextStep: step })
  ElMessage.success(flowMessage('PASS', next))
}

async function changeResult(row, result) {
  // 只有「从非通过改成通过」才需要问下一步，已经是通过时改别的字段不该重复弹窗
  const step = result === 'PASS' && row.result !== 'PASS' ? await pickNextStep() : ''
  const flow = await interviewApi.update(row.id, {
    applicationId: row.applicationId,
    roundNo: row.roundNo,
    roundName: row.roundName,
    interviewType: row.interviewType,
    interviewTime: row.interviewTime,
    interviewer: row.interviewer,
    location: row.location,
    meetingUrl: row.meetingUrl,
    review: row.review,
    result,
    nextStep: step
  })
  ElMessage.success(flowMessage(result, flow))
  load()
}

/** 结果是通过时先问下一步，用户关掉弹窗则保持现状 */
async function pickNextStep() {
  try {
    await ElMessageBox.confirm(
      '选「已拿 Offer」投递状态会变成 Offer；选「进入下一轮」会自动建一条下一轮面试草稿。',
      '这轮通过了，下一步是？',
      { confirmButtonText: '已拿 Offer', cancelButtonText: '进入下一轮', type: 'success',
        distinguishCancelAndClose: true })
    return 'OFFER'
  } catch (action) {
    return action === 'cancel' ? 'NEXT_ROUND' : ''
  }
}

function flowMessage(result, flow) {
  if (result === 'FAIL') {
    return '已标记未通过，投递状态同步为「已拒绝」'
  }
  if (flow?.nextRoundNo) {
    return `已创建第 ${flow.nextRoundNo} 轮面试草稿，去补一下时间`
  }
  if (flow?.applicationStatusLabel) {
    return `结果已更新，投递状态为「${flow.applicationStatusLabel}」`
  }
  return '结果已更新'
}

async function onDelete(row) {
  await ElMessageBox.confirm('确定删除这条面试记录吗？', '提示', { type: 'warning' })
  await interviewApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

/**
 * 复盘一键入题库：把复盘文本交给 AI 拆成面试题并落库。
 * 复盘里提到的职位会一起带过去，题目就挂在这个岗位的分类下。
 */
async function onExtractQuestions() {
  if (!canExtract.value) return
  const proceed = await ensureLocalAiNotice({
    onConfigure: () => router.push({ path: '/ai', query: { configure: '1' } })
  })
  if (!proceed) return
  extracting.value = true
  try {
    const call = await aiApi.extractQuestions({
      review: form.review.trim(),
      jobId: dialog.jobId,
      save: true
    })
    const saved = call.data?.savedCount ?? 0
    if (!saved) {
      ElMessage.warning('这段复盘里没有识别出明确的问题，可以把面试官的原话记下来再试')
      return
    }
    ElMessage.success(`已提取 ${saved} 道题到题库`)
    dialog.visible = false
    await router.push({ path: '/questions' })
  } finally {
    extracting.value = false
  }
}

onMounted(load)
// keep-alive 缓存后再次进入不会触发 onMounted，这里补一次刷新
onActivated(load)
</script>

<style scoped>
.review-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}
</style>
