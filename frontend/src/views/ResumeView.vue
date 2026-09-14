<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索简历名称 / 技能" clearable style="width: 240px"
                @keyup.enter="load" @clear="load" />
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div style="flex: 1"></div>
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

        <div class="resume-actions">
          <el-button link type="primary" @click="openDialog(item)">编辑</el-button>
          <el-button link type="primary" :disabled="item.isDefault === 1" @click="onSetDefault(item)">设为默认</el-button>
          <el-button link type="primary" @click="$router.push({ path: '/ai', query: { resumeId: item.id } })">AI 匹配</el-button>
          <el-button link type="danger" @click="onDelete(item)">删除</el-button>
        </div>
      </div>
    </div>

    <el-pagination v-if="total > query.pageSize" style="margin-top: 16px; justify-content: flex-end" background
                   layout="total, prev, pager, next" :total="total"
                   v-model:current-page="query.pageNum" @current-change="load" />

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑简历' : '新增简历'" width="720px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="简历名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：Java 后端-社招版" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="学历" label-width="60px">
            <el-select v-model="form.education" clearable style="width: 100%">
              <el-option v-for="item in ['大专', '本科', '硕士', '博士']" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="工作年限" label-width="80px">
            <el-input-number v-model="form.workYears" :min="0" :max="40" controls-position="right" style="width: 100%" />
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="技能标签">
          <el-input v-model="form.skills" placeholder="用英文逗号分隔，例如：Java,Spring Boot,MySQL,Redis" />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="2000" show-word-limit />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { resumeApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 12, keyword: '' })
const dialog = reactive({ visible: false, id: null })
const form = reactive({
  title: '', name: '', phone: '', email: '', education: '', workYears: 0,
  skills: '', summary: '', content: '', isDefault: false
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
    isDefault: row?.isDefault === 1
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
</script>

<style scoped>
.resume-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 16px;
}

.resume-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
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
  border-top: 1px solid #f0f3f8;
  padding-top: 8px;
  margin-top: auto;
}
</style>
