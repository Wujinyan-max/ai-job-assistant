<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-kicker">Resumes</div>
        <h2 class="page-title">简历管理</h2><div class="page-subtitle">支持多份简历，针对不同岗位灵活切换</div></div>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索简历名称 / 技能" clearable style="width: 240px"
                @keyup.enter="load" @clear="load" />
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div style="flex: 1"></div>
      <el-upload ref="uploadRef" :auto-upload="false" :show-file-list="false"
                 accept=".pdf,.docx,.txt,.md" :on-change="onFileChange">
        <el-button :icon="Upload" :loading="importing">导入简历</el-button>
      </el-upload>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增简历</el-button>
    </div>

    <el-empty v-if="!loading && !rows.length" description="还没有简历，先创建一份吧" />
    <div class="resume-grid" v-loading="loading">
      <div v-for="item in rows" :key="item.id" class="card resume-card">
        <div class="resume-head">
          <div>
            <div class="resume-title">
              {{ item.title }}
              <el-tag v-if="item.isDefault === 1" size="small" type="success" effect="light">默认</el-tag>
            </div>
            <div class="muted" style="font-size: 12px; margin-top: 4px">
              {{ item.name || '未填写姓名' }} · {{ item.education || '未填写学历' }} ·
              {{ item.workYears ?? 0 }} 年经验
            </div>
          </div>
        </div>

        <div class="resume-skills">
          <el-tag v-for="skill in splitSkills(item.skills)" :key="skill" size="small" effect="plain" class="tag">{{ skill }}</el-tag>
        </div>

        <div class="resume-summary muted">{{ item.summary || '暂无个人简介' }}</div>

        <div class="resume-actions action-bar">
          <el-button link type="primary" @click="openDialog(item)">编辑</el-button>
          <el-button link type="primary" :disabled="item.isDefault === 1" @click="onSetDefault(item)">设为默认</el-button>
          <span class="action-gap"></span>
          <el-tooltip content="AI 匹配度分析" placement="top">
            <el-button link type="primary" class="is-icon-only" :icon="Aim"
                       aria-label="AI 匹配"
                       @click="$router.push({ path: '/ai', query: { resumeId: item.id } })" />
          </el-tooltip>
          <el-tooltip content="按岗位优化这份简历" placement="top">
            <el-button link type="primary" class="is-icon-only" :icon="MagicStick"
                       aria-label="按岗位优化"
                       @click="$router.push({ path: '/ai', query: { resumeId: item.id, tab: 'optimize' } })" />
          </el-tooltip>
          <el-tooltip content="A4 排版预览 / 导出 PDF" placement="top">
            <el-button link type="primary" class="is-icon-only" :icon="Printer"
                       aria-label="排版预览"
                       @click="$router.push({ path: '/ai', query: { resumeId: item.id, tab: 'structure' } })" />
          </el-tooltip>
          <el-button link type="danger" @click="onDelete(item)">删除</el-button>
        </div>
      </div>
    </div>

    <el-pagination v-if="total > query.pageSize" style="margin-top: 16px; justify-content: flex-end" background
                   layout="total, prev, pager, next" :total="total"
                   v-model:current-page="query.pageNum" @current-change="load" />

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑简历' : '新增简历'" width="720px" top="6vh">
      <el-alert v-if="importInfo" class="import-alert" type="success" show-icon :closable="false"
                :title="`已从《${importInfo.fileName}》读取到 ${importInfo.textLength} 字`"
                :description="`自动填充：${importInfo.filledFields.join('、')}，请核对无误后再保存`" />
      <!-- 头像和版式单独提示：这些是排版页「原版复刻」要用的，用户得知道已经读到了 -->
      <!-- 识别方式单独提示：模型看不了图时要让用户知道，并给出换模型的建议 -->
      <el-alert v-if="importInfo?.notice" class="import-alert" type="warning" show-icon :closable="false"
                title="识别方式有变动" :description="importInfo.notice" />
      <div v-if="form.avatar || form.styleJson" class="import-style-tip">
        <img v-if="form.avatar" class="import-avatar" :src="form.avatar" alt="从简历里读取到的头像" />
        <span class="muted">
          已读取到{{ form.avatar ? '头像' : '' }}{{ form.avatar && form.styleJson ? '和' : '' }}{{ form.styleJson ? '版式配色' : '' }}，
          保存后在「AI 助手 → 简历排版」里可以还原成这份简历原来的样子
        </span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="简历名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：Java 后端-社招版" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="姓名">
            <el-input v-model="form.name" placeholder="例如：张三" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="学历" label-width="60px">
            <el-select v-model="form.education" clearable placeholder="选择学历" style="width: 100%">
              <el-option v-for="item in ['大专', '本科', '硕士', '博士']" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="工作年限" label-width="80px">
            <el-input-number v-model="form.workYears" :min="0" :max="40" controls-position="right" style="width: 100%" />
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="电话">
            <el-input v-model="form.phone" placeholder="例如：13800000000" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="例如：zhangsan@example.com" />
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="技能标签">
          <el-input v-model="form.skills" placeholder="用英文逗号分隔，例如：Java,Spring Boot,MySQL,Redis" />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="2000" show-word-limit
                    placeholder="一句话说明你的方向、年限和优势，AI 匹配和出题都会参考" />
        </el-form-item>
        <el-form-item label="简历正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10"
                    placeholder="把简历的项目经历、职责、成果贴进来，AI 匹配和出题都会读这段内容" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
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
import { onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, MagicStick, Plus, Printer, Search, Upload } from '@element-plus/icons-vue'
import { resumeApi } from '@/api'

/** 与后端 multipart 限制保持一致，避免白传一次超大文件 */
const MAX_IMPORT_SIZE = 10 * 1024 * 1024
const IMPORT_EXTENSIONS = ['pdf', 'docx', 'txt', 'md']

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const rows = ref([])
const total = ref(0)
const formRef = ref()
const uploadRef = ref()
/** 正在导入的文件解析结果，用来在表单顶部提示用户核对 */
const importInfo = ref(null)

const query = reactive({ pageNum: 1, pageSize: 12, keyword: '' })
const dialog = reactive({ visible: false, id: null })
const form = reactive({
  title: '', name: '', phone: '', email: '', education: '', workYears: 0,
  skills: '', summary: '', content: '', contentJson: '', isDefault: false,
  // 头像与导入时提取到的版式：从 PDF 里读到的，跟着简历一起存
  avatar: '', styleJson: ''
})

const rules = {
  title: [{ required: true, message: '请输入简历名称', trigger: 'blur' }],
  content: [{ required: true, message: '请填写简历正文，否则 AI 无法分析', trigger: 'blur' }]
}

const splitSkills = (skills) => (skills ? skills.split(/[,，]/).filter(Boolean).slice(0, 8) : [])

async function load() {
  loading.value = true
  try {
    const data = await resumeApi.page(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  importInfo.value = null
  dialog.id = row?.id ?? null
  dialog.visible = true
  Object.assign(form, {
    title: row?.title || '',
    name: row?.name || '',
    phone: row?.phone || '',
    email: row?.email || '',
    education: row?.education || '',
    workYears: row?.workYears ?? 0,
    skills: row?.skills || '',
    summary: row?.summary || '',
    content: row?.content || '',
    contentJson: row?.contentJson || '',
    isDefault: row?.isDefault === 1,
    avatar: row?.avatar || '',
    styleJson: row?.styleJson || ''
  })
}

/** 选完文件先做本地校验，再交给后端解析，解析结果直接回填到「新增简历」表单 */
async function onFileChange(uploadFile) {
  const file = uploadFile?.raw
  uploadRef.value?.clearFiles()
  if (!file) {
    return
  }
  const extension = (file.name.split('.').pop() || '').toLowerCase()
  if (extension === 'doc') {
    ElMessage.warning('暂不支持 .doc 老格式，请在 Word 里另存为 .docx 或 PDF 后再导入')
    return
  }
  if (!IMPORT_EXTENSIONS.includes(extension)) {
    ElMessage.warning('请上传 PDF、DOCX 或 TXT 格式的简历文件')
    return
  }
  if (file.size > MAX_IMPORT_SIZE) {
    ElMessage.warning('文件过大，请上传 10MB 以内的简历')
    return
  }

  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const data = await resumeApi.importFile(formData)
    applyImport(data)
    ElMessage.success(`已解析《${data.fileName}》，请核对后保存`)
  } finally {
    importing.value = false
  }
}

function applyImport(data) {
  importInfo.value = data
  dialog.id = null
  dialog.visible = true
  Object.assign(form, {
    title: data.title || data.fileName || '',
    name: data.name || '',
    phone: data.phone || '',
    email: data.email || '',
    education: data.education || '',
    workYears: data.workYears ?? 0,
    skills: data.skills || '',
    summary: data.summary || '',
    content: data.content || '',
    // 导入时已经识别出结构就直接带上，排版页不用再花一次 AI 调用
    contentJson: data.structure ? JSON.stringify(data.structure) : '',
    // 导入时一并取出头像和版式，排版页就能直接还原成原来的样子
    avatar: data.avatar || '',
    styleJson: data.style ? JSON.stringify(data.style) : '',
    // 第一份简历顺手设为默认，省得用户再点一次「设为默认」
    isDefault: rows.value.length === 0
  })
}

async function onSubmit() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (dialog.id) {
      await resumeApi.update(dialog.id, form)
    } else {
      await resumeApi.create(form)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onSetDefault(row) {
  await resumeApi.setDefault(row.id)
  ElMessage.success('已设为默认简历')
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除简历「${row.title}」吗？`, '提示', { type: 'warning' })
  await resumeApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
onActivated(load)
</script>

<style scoped>
.import-alert {
  margin-bottom: 14px;
}

/* 导入后提示「头像 + 版式」已读到：版式是排版页复刻外观的依据，得让用户看得见 */
.import-style-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: -4px 0 14px;
  font-size: 12px;
}

.import-avatar {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1px solid var(--border-light);
  object-fit: cover;
}

.resume-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 12px;
}

.resume-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: transform .2s ease, box-shadow .2s ease;
}

.resume-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--brand);
  opacity: .85;
}

.resume-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.resume-title {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.resume-skills {
  min-height: 24px;
}

.tag {
  margin: 0 6px 6px 0;
}

.resume-summary {
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resume-actions {
  border-top: 1px solid var(--divider);
  padding-top: 8px;
  margin-top: auto;
}
</style>
