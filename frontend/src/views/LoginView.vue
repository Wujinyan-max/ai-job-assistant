<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <aside class="brand-side">
      <div class="brand-side-inner">
        <div class="brand-row" @click="$router.push('/')" style="cursor:pointer">
          <img class="brand-mark" src="@/assets/logo-mark.png" alt="职得 JobPath" />
          <span class="brand-name">职得 JobPath</span>
        </div>

        <div class="brand-body">
          <div class="page-kicker">AI-Powered Job Hunt</div>
          <h1 class="brand-title">把找工作的每一步，<br />都放进同一个地方。</h1>
          <p class="brand-desc">
            公司、职位、简历、投递、面试、Offer，一条完整的求职闭环。
          </p>
          <ul class="brand-points">
            <li v-for="p in points" :key="p">
              <el-icon :size="13" color="#9c7c3c"><CircleCheckFilled /></el-icon>
              <span>{{ p }}</span>
            </li>
          </ul>
        </div>

        <div class="brand-foot">职得 JobPath · 数据只属于你</div>
      </div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="form-side">
      <div class="login-card">
        <div class="card-head">
          <h2 class="card-title">{{ tab === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <p class="card-sub">
            {{ tab === 'login' ? '登录后继续你的求职进度' : '10 秒完成注册，开始使用' }}
          </p>
        </div>

        <div class="seg">
          <button
            v-for="t in ['login', 'register']"
            :key="t"
            class="seg-item"
            :class="{ active: tab === t }"
            @click="tab = t"
          >
            {{ t === 'login' ? '登录' : '注册' }}
          </button>
        </div>

        <el-form
          v-if="tab === 'login'"
          ref="loginRef"
          :model="loginForm"
          :rules="loginRules"
          size="large"
          class="form"
          @keyup.enter="onLogin"
        >
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="密码" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="onLogin">
            登 录
          </el-button>
          <div class="form-tip">
            还没有账号？<el-link type="primary" :underline="false" @click="tab = 'register'">免费注册</el-link>
          </div>
        </el-form>

        <el-form
          v-else
          ref="registerRef"
          :model="registerForm"
          :rules="registerRules"
          size="large"
          class="form"
          @keyup.enter="onRegister"
        >
          <el-form-item prop="username">
            <el-input v-model="registerForm.username" placeholder="用户名（4-20 位字母数字下划线）" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input v-model="registerForm.nickname" placeholder="昵称（可选）" :prefix-icon="Avatar" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="registerForm.password" type="password" show-password placeholder="密码（至少 6 位）" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="onRegister">
            注册并登录
          </el-button>
          <div class="form-tip">
            已有账号？<el-link type="primary" :underline="false" @click="tab = 'login'">直接登录</el-link>
          </div>
        </el-form>
      </div>
    </main>
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

const tab = ref(route.query.tab === 'register' ? 'register' : 'login')
const loading = ref(false)
const loginRef = ref()
const registerRef = ref()

const points = [
  'JD 粘贴即解析，技能要求自动抽取',
  '简历按岗位匹配打分，差距一眼看清',
  '面试题按 JD 生成，进题库随时刷'
]

const loginForm = reactive({ username: '', password: '' })
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
  background: var(--page-bg);
  overflow: hidden;
}

/* ======================== 左侧品牌区 ======================== */
.brand-side {
  flex: 1;
  min-width: 0;
  background: var(--sider-bg);
  border-right: 1px solid var(--sider-border);
  display: flex;
  flex-direction: column;
}

.brand-side-inner {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 28px 48px 32px;
  max-width: 560px;
  margin: 0 auto;
  width: 100%;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
}

.brand-mark {
  width: 28px;
  height: 28px;
  object-fit: contain;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-size: 14px;
  font-weight: 700;
}

.brand-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px 0;
}

.brand-title {
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.6px;
  line-height: 1.35;
  margin: 12px 0 14px;
}

.brand-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.8;
  margin: 0 0 26px;
}

.brand-points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13.5px;
  color: var(--text-regular);
}

.brand-foot {
  flex: none;
  font-size: 12px;
  color: var(--text-placeholder);
}

/* ======================== 右侧表单区 ======================== */
.form-side {
  width: 480px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  overflow-y: auto;
}

.login-card {
  width: 100%;
  max-width: 360px;
}

.card-head {
  margin-bottom: 24px;
}

.card-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.4px;
  margin: 0 0 6px;
}

.card-sub {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

/* 分段切换 */
.seg {
  display: flex;
  background: var(--info-soft);
  border-radius: 10px;
  padding: 3px;
  gap: 3px;
  margin-bottom: 22px;
}

.seg-item {
  flex: 1;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.seg-item.active {
  background: #fff;
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: var(--shadow-xs);
}

.form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.submit {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 0.1em;
}

.form-tip {
  margin-top: 18px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-secondary);
}

/* ======================== 响应式 ======================== */
@media (max-width: 900px) {
  .brand-side {
    display: none;
  }
  .form-side {
    width: 100%;
  }
}
</style>
