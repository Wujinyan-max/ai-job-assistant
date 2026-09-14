<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card">
          <div class="page-title" style="margin-bottom: 16px">个人资料</div>
          <el-form :model="form" label-width="88px">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.user?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" />
            </el-form-item>
            <el-form-item label="学历">
              <el-select v-model="form.education" clearable style="width: 100%">
                <el-option v-for="item in ['大专', '本科', '硕士', '博士']" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="工作年限">
              <el-input-number v-model="form.workYears" :min="0" :max="40" controls-position="right" />
            </el-form-item>
            <el-button type="primary" :loading="saving" @click="onSaveProfile">保存资料</el-button>
          </el-form>
        </div>
      </el-col>

      <el-col :span="10">
        <div class="card">
          <div class="page-title" style="margin-bottom: 16px">修改密码</div>
          <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="88px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwd.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwd.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwd.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-button type="primary" :loading="changing" @click="onChangePassword">修改密码</el-button>
          </el-form>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const saving = ref(false)
const changing = ref(false)
const pwdRef = ref()

const form = reactive({ nickname: '', email: '', phone: '', education: '', workYears: 0 })
const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === pwd.newPassword ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}

async function onSaveProfile() {
  saving.value = true
  try {
    const data = await authApi.updateProfile(form)
    userStore.setUser(data)
    ElMessage.success('资料已更新')
  } finally {
    saving.value = false
  }
}

async function onChangePassword() {
  await pwdRef.value.validate()
  changing.value = true
  try {
    await authApi.changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    Object.assign(pwd, { oldPassword: '', newPassword: '', confirmPassword: '' })
  } finally {
    changing.value = false
  }
}

onMounted(async () => {
  const data = await userStore.loadCurrentUser()
  Object.assign(form, {
    nickname: data.nickname || '',
    email: data.email || '',
    phone: data.phone || '',
    education: data.education || '',
    workYears: data.workYears ?? 0
  })
})
</script>
