<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索职位名称 / 地点" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.companyId" placeholder="全部公司" clearable filterable style="width: 190px" @change="load">
        <el-option v-for="item in companies" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="职位状态" clearable style="width: 140px" @change="load">
        <el-option label="在招" value="OPEN" />
        <el-option label="已关闭" value="CLOSED" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增职位</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="jobName" label="职位名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="companyName" label="公司" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.companyName || '—' }}</template>
        </el-table-column>
        <el-table-column label="薪资" width="120">
          <template #default="{ row }">
            <span v-if="row.salaryMin || row.salaryMax">{{ row.salaryMin || '?' }}-{{ row.salaryMax || '?' }}K</span>
            <span v-else class="muted">面议</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" effect="light">
              {{ row.status === 'OPEN' ? '在招' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAi(row)">AI 解析</el-button>
            <el-button link type="primary" @click="$router.push('/ai')">简历匹配</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑职位' : '新增职位'" width="700px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="关联公司">
          <el-select v-model="form.companyId" placeholder="选择公司" clearable filterable style="width: 100%">
            <el-option v-for="item in companies" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="例如：Java 后端开发工程师" />
        </el-form-item>
        <el-form-item label="薪资范围">
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input-number v-model="form.salaryMin" :min="0" :max="500" controls-position="right" style="width: 130px" />
            <span class="muted">—</span>
            <el-input-number v-model="form.salaryMax" :min="0" :max="500" controls-position="right" style="width: 130px" />
            <span class="muted">K / 月</span>
          </div>
        </el-form-item>
        <el-form-item label="工作地点">
          <el-input v-model="form.location" placeholder="例如：北京·海淀区" />
        </el-form-item>
        <el-form-item label="职位链接">
          <el-input v-model="form.jobUrl" placeholder="https://" />
        </el-form-item>
        <el-form-item label="职位状态">
          <el-radio-group v-model="form.status">
            <el-radio value="OPEN">在招</el-radio>
            <el-radio value="CLOSED">已关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="职位描述" prop="jobDescription">
          <el-input v-model="form.jobDescription" type="textarea" :rows="12"
                    placeholder="把招聘网站上的 JD 整段粘贴进来，AI 解析和简历匹配都依赖它" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="ai.visible" title="AI 岗位解析" size="480px">
      <div v-loading="ai.loading">
        <template v-if="ai.result">
          <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
            <el-descriptions-item label="经验要求">{{ ai.result.experience }}</el-descriptions-item>
            <el-descriptions-item label="学历要求">{{ ai.result.education }}</el-descriptions-item>
            <el-descriptions-item label="职级判断">{{ ai.result.seniority }}</el-descriptions-item>
            <el-descriptions-item label="技能数量">{{ ai.result.skills.length }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title">技术要求</div>
          <el-tag v-for="skill in ai.result.skills" :key="skill" class="tag" type="primary" effect="light">{{ skill }}</el-tag>

          <div class="section-title">关键词 / 加分项</div>
          <el-tag v-for="word in ai.result.keywords" :key="word" class="tag" type="warning" effect="light">{{ word }}</el-tag>

          <div class="section-title">主要职责</div>
          <ul class="duty-list">
            <li v-for="(item, index) in ai.result.responsibilities" :key="index">{{ item }}</li>
          </ul>

          <div class="section-title">AI 总结</div>
          <el-alert :title="ai.result.summary" type="info" :closable="false" show-icon />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { aiApi, companyApi, jobApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const companies = ref([])
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', companyId: null, status: '' })
const dialog = reactive({ visible: false, id: null })
const ai = reactive({ visible: false, loading: false, result: null })
const form = reactive({
  companyId: null, jobName: '', jobDescription: '', salaryMin: null, salaryMax: null,
  location: '', jobUrl: '', status: 'OPEN'
})

const rules = {
  jobName: [{ required: true, message: '请输入职位名称', trigger: 'blur' }],
  jobDescription: [{ required: true, message: '请粘贴职位描述，否则 AI 功能无法使用', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await jobApi.page(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadCompanies() {
  companies.value = await companyApi.all()
}

function openDialog(row) {
  dialog.id = row?.id ?? null
  dialog.visible = true
  Object.assign(form, {
    companyId: row?.companyId ?? null,
    jobName: row?.jobName || '',
    jobDescription: row?.jobDescription || '',
    salaryMin: row?.salaryMin ?? null,
    salaryMax: row?.salaryMax ?? null,
    location: row?.location || '',
    jobUrl: row?.jobUrl || '',
    status: row?.status || 'OPEN'
  })
}

async function onSubmit() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (dialog.id) {
      await jobApi.update(dialog.id, form)
    } else {
      await jobApi.create(form)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除职位「${row.jobName}」吗？`, '提示', { type: 'warning' })
  await jobApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function openAi(row) {
  ai.visible = true
  ai.loading = true
  ai.result = null
  try {
    ai.result = await aiApi.analyzeJd({ jobId: row.id })
  } finally {
    ai.loading = false
  }
}

onMounted(() => {
  load()
  loadCompanies()
})
</script>

<style scoped>
.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
  font-size: 14px;
}

.tag {
  margin: 0 8px 8px 0;
}

.duty-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
  color: #4b5563;
  font-size: 13px;
}
</style>
