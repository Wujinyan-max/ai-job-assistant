<template>
  <el-container style="height: 100%">
    <el-aside width="212px" class="aside">
      <div class="logo">
        <el-icon :size="22"><Briefcase /></el-icon>
        <span>AI 求职管理</span>
      </div>
      <el-menu :default-active="route.path" router class="menu" background-color="#1f2937"
               text-color="#c9cdd4" active-text-color="#fff">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <span>{{ item.meta.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta.title }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-avatar :size="28" style="background: var(--brand)">
              {{ (userStore.user?.nickname || userStore.user?.username || 'U').slice(0, 1) }}
            </el-avatar>
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
            <el-icon><ArrowDown /></el-icon>
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
.aside {
  background: #1f2937;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu {
  border-right: none;
  flex: 1;
}

.header {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
  z-index: 1;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: 14px;
  color: #1f2937;
}

.main {
  padding: 0;
  background: var(--page-bg);
  overflow-y: auto;
}
</style>
