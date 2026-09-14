<template>
  <el-container class="shell">
    <el-aside class="aside">
      <div class="logo">
        <span class="logo-mark">
          <el-icon :size="18"><Briefcase /></el-icon>
        </span>
        <span class="logo-text">AI 求职管理</span>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <span>{{ item.meta.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="body">
      <el-header class="header">
        <div class="header-title">{{ route.meta.title }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-avatar :size="30" class="user-avatar">
              {{ (userStore.user?.nickname || userStore.user?.username || 'U').slice(0, 1) }}
            </el-avatar>
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="5">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = computed(() =>
  router.getRoutes().filter((item) => item.meta?.title && item.meta?.icon && item.path !== '/login')
)

onMounted(() => {
  // 刷新页面后用 token 换回最新的用户信息
  userStore.loadCurrentUser().catch(() => {})
})

async function onCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.shell {
  height: 100%;
}

/* --------------------------------------------------------------- 侧边栏 */
.aside {
  width: var(--sider-width);
  background: var(--sider-bg);
  border-right: 1px solid var(--divider);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  flex: none;
}

.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--brand) 0%, var(--brand-deep) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(59, 114, 245, 0.32);
  flex: none;
}

.logo-text {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.2px;
  color: var(--text-primary);
}

.menu {
  border-right: none;
  background: transparent;
  padding: 6px 12px 12px;
  flex: 1;
  overflow-y: auto;
}

/* 菜单项做成圆角胶囊，选中时浅蓝底 + 蓝色图标 */
.menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin-bottom: 4px;
  padding-left: 12px !important;
  padding-right: 12px !important;
  border-radius: 10px;
  color: var(--text-regular);
  font-size: 14px;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.menu :deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 17px;
  color: var(--text-secondary);
  transition: color 0.18s ease;
}

.menu :deep(.el-menu-item:hover) {
  background: #e8edf6;
  color: var(--text-primary);
}

.menu :deep(.el-menu-item:hover .el-icon) {
  color: var(--text-regular);
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 600;
}

.menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--brand);
}

/* ----------------------------------------------------------------- 右侧 */
.body {
  min-width: 0;
}

.header {
  height: var(--header-height);
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 1;
}

.header-title {
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.2px;
  color: var(--text-primary);
}

.user {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  outline: none;
  padding: 5px 10px 5px 5px;
  border-radius: 20px;
  transition: background-color 0.18s ease;
}

.user:hover {
  background: #f5f7fb;
}

.user-avatar {
  background: linear-gradient(135deg, var(--brand) 0%, var(--brand-deep) 100%);
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: var(--text-primary);
}

.user-caret {
  font-size: 12px;
  color: var(--text-secondary);
}

.main {
  padding: 0;
  background: var(--page-bg);
  overflow-y: auto;
}
</style>