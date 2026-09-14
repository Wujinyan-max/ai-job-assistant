<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <el-icon :size="30" color="#3b72f5"><Briefcase /></el-icon>
        <h1>AI 求职管理平台</h1>
        <p>统一管理投递、简历与面试，用 AI 提升求职效率</p>
      </div>

      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" size="large" @keyup.enter="onLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" show-password
                        placeholder="密码" :prefix-icon="Lock" />
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form ref="registerRef" :model="registerForm" :rules="registerRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="用户名（4-20 位字母数字下划线）" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" show-password
                        placeholder="密码（至少 6 位）" :prefix-icon="Lock" />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="昵称（可选）" :prefix-icon="Avatar" />
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onRegister">
              注册并登录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Avatar, Lock, User } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tab = ref('login')
const loading = ref(false)
const loginRef = ref()
const registerRef = ref()

const loginForm = reactive({ username: 'demo', password: '123456' })
const registerForm = reactive({ username: '', password: '', nickname: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '4-20 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6-32 位', trigger: 'blur' }
  ]
}

async function submit(action, formRef, payload) {
  await formRef.validate()
  loading.value = true
  try {
    const data = await action(payload)
    userStore.setLogin(data)
    ElMessage.success(`欢迎回来，${data.user.nickname || data.user.username}`)
    router.push(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}

const onLogin = () => submit(authApi.login, loginRef.value, loginForm)
const onRegister = () => submit(authApi.register, registerRef.value, registerForm)
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8effd 0%, #f3f6fa 45%, #e3eefb 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 32px 32px 24px;
  box-shadow: 0 12px 40px rgba(59, 114, 245, 0.12);
}

.brand {
  text-align: center;
  margin-bottom: 12px;
}

.brand h1 {
  font-size: 20px;
  margin: 10px 0 6px;
}

.brand p {
  color: #8a90a2;
  font-size: 13px;
  margin: 0 0 8px;
}
</style>
