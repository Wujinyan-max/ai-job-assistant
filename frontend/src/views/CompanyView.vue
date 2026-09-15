<template>
  <div class="page">
    <div class="page-header">
      <div><h2 class="page-title">公司管理</h2><div class="page-subtitle">维护目标公司资料与当前跟进状态</div></div>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索公司名称 / 行业" clearable style="width: 240px"
                @keyup.enter="load" @clear="load" />
      <el-select v-model="query.status" placeholder="公司状态" clearable style="width: 150px" @change="load">
        <el-option label="目标公司" value="TARGET" />
        <el-option label="已沟通" value="CONTACTED" />
        <el-option label="已放弃" value="CLOSED" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增公司</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="name" label="公司名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="scale" label="规模" width="130" />
        <el-table-column prop="city" label="城市" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
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

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑公司' : '新增公司'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="公司名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：字节跳动" />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-input v-model="form.industry" placeholder="例如：互联网" />
        </el-form-item>
        <el-form-item label="公司规模">
          <el-input v-model="form.scale" placeholder="例如：1000-9999人" />
        </el-form-item>
        <el-form-item label="所在城市">
          <el-input v-model="form.city" placeholder="例如：北京" />
        </el-form-item>
        <el-form-item label="官网">
          <el-input v-model="form.website" placeholder="https://" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="目标公司" value="TARGET" />
            <el-option label="已沟通" value="CONTACTED" />
            <el-option label="已放弃" value="CLOSED" />
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
  </div>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { companyApi } from '@/api'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
const dialog = reactive({ visible: false, id: null })
const form = reactive({ name: '', industry: '', scale: '', city: '', website: '', status: 'TARGET', remark: '' })

const rules = { name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }] }

const statusLabel = (status) => ({ TARGET: '目标公司', CONTACTED: '已沟通', CLOSED: '已放弃' }[status] || status)
const statusType = (status) => ({ TARGET: 'primary', CONTACTED: 'success', CLOSED: 'info' }[status] || 'info')

async function load() {
  loading.value = true
  try {
    const data = await companyApi.page(query)
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
    name: row?.name || '',
    industry: row?.industry || '',
    scale: row?.scale || '',
    city: row?.city || '',
    website: row?.website || '',
    status: row?.status || 'TARGET',
    remark: row?.remark || ''
  })
}

async function onSubmit() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (dialog.id) {
      await companyApi.update(dialog.id, form)
    } else {
      await companyApi.create(form)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', { type: 'warning' })
  await companyApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
onActivated(load)
</script>
