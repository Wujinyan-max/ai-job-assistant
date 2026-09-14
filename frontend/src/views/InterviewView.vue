<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索职位 / 公司 / 面试官" clearable style="width: 240px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.result" placeholder="面试结果" clearable style="width: 140px" @change="load">
        <el-option label="待定" value="PENDING" />
        <el-option label="通过" value="PASS" />
        <el-option label="未通过" value="FAIL" />
      </el-select>
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

    <el-dialog v-model="dialog.visible" title="编辑面试记录" width="560px">
      <el-form :model="form" label-width="88px">
        <el-form-item label="轮次名称">
          <el-input v-model="form.roundName" />
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
          <el-date-picker v-model="form.interviewTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="面试官">
          <el-input v-model="form.interviewer" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="会议链接">
          <el-input v-model="form.meetingUrl" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { interviewApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const upcoming = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, result: '', keyword: '' })
const dialog = reactive({ visible: false, id: null })
const form = reactive({
  applicationId: null, roundName: '', interviewType: 'VIDEO', interviewTime: '',
  interviewer: '', location: '', meetingUrl: '', result: 'PENDING', review: ''
})

const typeLabel = (type) => ({ PHONE: '电话', VIDEO: '视频', ONSITE: '现场', WRITTEN: '笔试' }[type] || type)

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
    await interviewApi.update(dialog.id, { ...form, interviewTime: form.interviewTime || null })
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function changeResult(row, result) {
  await interviewApi.update(row.id, {
    applicationId: row.applicationId,
    roundNo: row.roundNo,
    roundName: row.roundName,
    interviewType: row.interviewType,
    interviewTime: row.interviewTime,
    interviewer: row.interviewer,
    location: row.location,
    meetingUrl: row.meetingUrl,
    review: row.review,
    result
  })
  ElMessage.success(result === 'FAIL' ? '已标记未通过，投递状态同步为「已拒绝」' : '结果已更新')
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm('确定删除这条面试记录吗？', '提示', { type: 'warning' })
  await interviewApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
