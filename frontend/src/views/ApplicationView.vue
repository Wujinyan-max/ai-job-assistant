<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索职位 / 公司" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="load">
        <el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div style="flex: 1"></div>
      <el-button :icon="Grid" @click="$router.push('/board')">看板视图</el-button>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增投递</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="职位" min-width="180">
          <template #default="{ row }">
            <div style="font-weight: 500">{{ row.jobName || '—' }}</div>
            <div class="muted" style="font-size: 12px">{{ row.companyName || '未关联公司' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-select :model-value="row.applicationStatus" size="small" style="width: 120px"
                       @change="(value) => changeStatus(row, value)">
              <el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="resumeTitle" label="使用简历" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.resumeTitle || '—' }}</template>
        </el-table-column>
        <el-table-column prop="applyTime" label="投递时间" width="160">
          <template #default="{ row }">{{ row.applyTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="source" label="渠道" width="110" />
        <el-table-column label="面试" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.interviewCount > 0" size="small" type="warning" effect="light">{{ row.interviewCount }} 轮</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openInterview(row)">记录面试</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background
                     layout="total, prev, pager, next, sizes" :total="total"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑投递记录' : '新增投递记录'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="职位" prop="jobId">
          <el-select v-model="form.jobId" placeholder="选择要投递的职位" filterable style="width: 100%">
            <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用简历">
          <el-select v-model="form.resumeId" placeholder="选择简历" clearable style="width: 100%">
            <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="投递状态">
          <el-select v-model="form.applicationStatus" style="width: 100%">
            <el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="投递时间">
          <el-date-picker v-model="form.applyTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="留空则自动使用当前时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="投递渠道">
          <el-select v-model="form.source" placeholder="选择或输入" filterable allow-create clearable style="width: 100%">
            <el-option v-for="item in ['Boss直聘', '拉勾', '猎聘', '智联招聘', '内推', '官网']" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="interviewDialog.visible" title="记录一场面试" width="520px">
      <el-form :model="interviewForm" label-width="88px">
        <el-form-item label="轮次名称">
          <el-input v-model="interviewForm.roundName" placeholder="例如：技术一面" />
        </el-form-item>
        <el-form-item label="面试形式">
          <el-radio-group v-model="interviewForm.interviewType">
            <el-radio value="VIDEO">视频</el-radio>
            <el-radio value="PHONE">电话</el-radio>
            <el-radio value="ONSITE">现场</el-radio>
            <el-radio value="WRITTEN">笔试</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面试时间">
          <el-date-picker v-model="interviewForm.interviewTime" type="datetime"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="面试官">
          <el-input v-model="interviewForm.interviewer" />
        </el-form-item>
        <el-form-item label="会议链接">
          <el-input v-model="interviewForm.meetingUrl" placeholder="线上会议链接" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="interviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitInterview">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Grid, Plus, Search } from '@element-plus/icons-vue'
import { applicationApi, interviewApi, jobApi, resumeApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const jobs = ref([])
const resumes = ref([])
const statuses = ref({})
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 10, status: '', keyword: '' })
const dialog = reactive({ visible: false, id: null })
const interviewDialog = reactive({ visible: false, applicationId: null })

const form = reactive({
  jobId: null, resumeId: null, applicationStatus: 'APPLIED', applyTime: '', source: '', remark: ''
})
const interviewForm = reactive({
  roundName: '', interviewType: 'VIDEO', interviewTime: '', interviewer: '', meetingUrl: ''
})

const rules = { jobId: [{ required: true, message: '请选择职位', trigger: 'change' }] }

async function load() {
  loading.value = true
  try {
    const data = await applicationApi.page(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [jobPage, resumeList, statusMap] = await Promise.all([
    jobApi.page({ pageNum: 1, pageSize: 100 }),
    resumeApi.all(),
    applicationApi.statuses()
  ])
  jobs.value = jobPage.records
  resumes.value = resumeList
  statuses.value = statusMap
}

function openDialog(row) {
  dialog.id = row?.id ?? null
  dialog.visible = true
  Object.assign(form, {
    jobId: row?.jobId ?? null,
    resumeId: row?.resumeId ?? (resumes.value.find((item) => item.isDefault === 1)?.id ?? null),
    applicationStatus: row?.applicationStatus || 'APPLIED',
    applyTime: row?.applyTime || '',
    source: row?.source || '',
    remark: row?.remark || ''
  })
}

async function onSubmit() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, applyTime: form.applyTime || null }
    if (dialog.id) {
      await applicationApi.update(dialog.id, payload)
    } else {
      await applicationApi.create(payload)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function changeStatus(row, status) {
  await applicationApi.updateStatus(row.id, status)
  ElMessage.success('状态已更新')
  load()
}

function openInterview(row) {
  interviewDialog.applicationId = row.id
  interviewDialog.visible = true
  Object.assign(interviewForm, {
    roundName: `技术${row.interviewCount + 1}面`,
    interviewType: 'VIDEO',
    interviewTime: '',
    interviewer: '',
    meetingUrl: ''
  })
}

async function submitInterview() {
  saving.value = true
  try {
    await interviewApi.create({
      ...interviewForm,
      applicationId: interviewDialog.applicationId,
      interviewTime: interviewForm.interviewTime || null
    })
    ElMessage.success('已记录面试，投递状态自动推进为「面试」')
    interviewDialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.jobName}」的投递记录吗？`, '提示', { type: 'warning' })
  await applicationApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadOptions()
})
</script>
