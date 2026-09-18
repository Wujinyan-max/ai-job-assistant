<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-kicker">Job Positions</div>
        <h2 class="page-title">职位管理</h2><div class="page-subtitle">集中管理岗位信息，并使用 AI 快速解析 JD</div></div>
    </div>
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
            <span v-if="row.salaryDesc">{{ row.salaryDesc }}</span>
            <span v-else-if="row.salaryMin || row.salaryMax">{{ row.salaryMin || '?' }}-{{ row.salaryMax || '?' }}K</span>
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
        <el-table-column label="操作" width="292" fixed="right">
          <template #default="{ row }">
            <div class="action-bar">
              <el-button link type="primary" :disabled="!canApply" @click="openApply(row)">投递</el-button>
              <el-button link type="primary" @click="openAi(row)">AI 解析</el-button>
              <el-button link type="primary" @click="$router.push('/ai')">简历匹配</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑职位' : '新增职位'" width="700px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="关联公司">
          <el-select v-model="form.companyId" placeholder="选择所属公司（可留空）" clearable filterable style="width: 100%">
            <el-option v-for="item in companies" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="例如：Java 后端开发工程师" />
        </el-form-item>
        <el-form-item label="薪资范围">
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input-number v-model="form.salaryMin" :min="0" :max="500" controls-position="right"
                             placeholder="最低" style="width: 130px" />
            <span class="muted">—</span>
            <el-input-number v-model="form.salaryMax" :min="0" :max="500" controls-position="right"
                             placeholder="最高" style="width: 130px" />
            <span class="muted">K / 月</span>
          </div>
        </el-form-item>
        <el-form-item label="薪资描述">
          <el-input v-model="form.salaryDesc" maxlength="50" show-word-limit
                    placeholder="选填，例如：15-30K·14薪；填了列表优先显示它" />
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

    <el-dialog v-model="apply.visible" title="一键投递" width="480px">
      <el-alert v-if="!apply.resumeId" type="warning" :closable="false" show-icon style="margin-bottom: 14px"
                title="未设置默认简历，请手动选择" />
      <el-form label-width="88px">
        <el-form-item label="职位">
          <el-input :model-value="apply.jobName" disabled />
        </el-form-item>
        <el-form-item label="使用简历">
          <el-select v-model="apply.resumeId" placeholder="选择本次投递用的简历" clearable style="width: 100%">
            <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <div v-if="!resumes.length" class="muted" style="font-size: 12px; line-height: 1.6">
            还没有简历，先去
            <el-link type="primary" :underline="false" @click="$router.push('/resumes')">简历管理</el-link>
            创建一份再投递
          </div>
        </el-form-item>
        <el-form-item label="投递渠道">
          <el-input v-model="apply.source" maxlength="50" placeholder="选填，例如：Boss 直聘" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="apply.remark" type="textarea" :rows="2" maxlength="500"
                    placeholder="选填，例如：内推码、投递时看到的重点要求" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="apply.visible = false">取消</el-button>
        <el-button type="primary" :disabled="!apply.resumeId" :loading="apply.saving" @click="submitApply">
          确认投递
        </el-button>
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
          <AiUsageBar :usage="ai.usage" :mocked="ai.mocked" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { aiApi, applicationApi, companyApi, jobApi, resumeApi } from '@/api'
import AiUsageBar from '@/components/AiUsageBar.vue'
import { ensureLocalAiNotice } from '@/utils/aiLocalNotice'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const companies = ref([])
const resumes = ref([])
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', companyId: null, status: '' })
const dialog = reactive({ visible: false, id: null })
const ai = reactive({ visible: false, loading: false, result: null, usage: null, mocked: false })
const apply = reactive({
  visible: false, saving: false, jobId: null, jobName: '', resumeId: null, source: '', remark: ''
})
const form = reactive({
  companyId: null, jobName: '', jobDescription: '', salaryMin: null, salaryMax: null, salaryDesc: '',
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

/** 有简历才允许投递，一份都没有时按钮直接置灰 */
const canApply = computed(() => resumes.value.length > 0)

async function loadResumes() {
  resumes.value = await resumeApi.all()
}

function openApply(row) {
  apply.jobId = row.id
  apply.jobName = row.jobName
  // 默认带出默认简历，用户改一下就能投，省掉「切页面 + 重新选职位」的步骤
  apply.resumeId = resumes.value.find((item) => item.isDefault === 1)?.id ?? null
  apply.source = ''
  apply.remark = ''
  apply.visible = true
}

async function submitApply() {
  if (!apply.resumeId) return
  apply.saving = true
  try {
    const id = await applicationApi.create({
      jobId: apply.jobId,
      resumeId: apply.resumeId,
      applicationStatus: 'APPLIED',
      source: apply.source,
      remark: apply.remark
    })
    ElMessage.success('投递成功，已跳到看板')
    apply.visible = false
    // 跳看板并带上刚创建的 id，看板会把新卡片高亮出来
    router.push({ path: '/board', query: { highlight: id } })
  } finally {
    apply.saving = false
  }
}

function openDialog(row) {
  dialog.id = row?.id ?? null
  dialog.visible = true
  // 打开表单时重新拉一次公司列表，保证刚创建的公司也能选到
  loadCompanies()
  Object.assign(form, {
    companyId: row?.companyId ?? null,
    jobName: row?.jobName || '',
    jobDescription: row?.jobDescription || '',
    salaryMin: row?.salaryMin ?? null,
    salaryMax: row?.salaryMax ?? null,
    salaryDesc: row?.salaryDesc || '',
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
  const proceed = await ensureLocalAiNotice({
    onConfigure: () => router.push({ path: '/ai', query: { configure: '1' } })
  })
  if (!proceed) return
  ai.visible = true
  ai.loading = true
  ai.result = null
  ai.usage = null
  try {
    const result = await aiApi.analyzeJd({ jobId: row.id })
    ai.result = result.data
    ai.usage = result.usage
    ai.mocked = result.mocked
  } finally {
    ai.loading = false
  }
}

onMounted(() => {
  load()
  loadCompanies()
  loadResumes()
})

// 页面被 keep-alive 缓存后，再次进入不会触发 onMounted，这里重新拉一次，
// 否则新建的公司不会出现在「关联公司」下拉框里
onActivated(() => {
  load()
  loadCompanies()
  loadResumes()
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
  color: var(--text-regular);
  font-size: 13px;
}
</style>
