<template>
  <el-container class="shell">
    <el-aside class="aside">
      <div class="logo">
        <img class="logo-mark" src="@/assets/logo-mark.png" alt="职得 JobPath" />
        <span class="logo-text">职得 JobPath</span>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <template v-for="group in menuGroups" :key="group.label">
          <div class="menu-group">{{ group.label }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <span>{{ item.meta.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="body">
      <el-header class="header">
        <div class="breadcrumb">
          <span class="breadcrumb-root">工作台</span>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">{{ route.meta.title }}</span>
        </div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-avatar :size="28" class="user-avatar">
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

const GROUPS = [
  { label: '工作台', paths: ['/dashboard', '/board', '/applications'] },
  { label: '资源库', paths: ['/jobs', '/companies', '/resumes', '/interviews'] },
  { label: '智能', paths: ['/ai', '/questions'] }
]

const menuGroups = computed(() => {
  const all = router.getRoutes().filter((r) => r.meta?.title && r.meta?.icon && r.path !== '/login')
  return GROUPS
    .map((g) => ({ label: g.label, items: g.paths.map((p) => all.find((r) => r.path === p)).filter(Boolean) }))
    .filter((g) => g.items.length)
})

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
  border-right: 1px solid var(--sider-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 18px;
  flex: none;
}

.logo-mark {
  width: 26px;
  height: 26px;
  /* 标记本身是透明底的方图，按内容等比缩放，不拉伸 */
  object-fit: contain;
  display: block;
  flex: none;
}

.logo-text {
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-primary);
}

.menu {
  border-right: none;
  background: transparent;
  padding: 10px 12px 16px;
  flex: 1;
  overflow-y: auto;
}

.menu-group {
  font-size: 10.5px;
  color: var(--sider-group);
  letter-spacing: 0.1em;
  padding: 0 10px;
  margin: 18px 0 6px;
  text-transform: uppercase;
}

.menu :deep(.el-menu-item) {
  height: 34px;
  line-height: 34px;
  margin-bottom: 1px;
  padding-left: 10px !important;
  padding-right: 10px !important;
  border-radius: 7px;
  color: var(--sider-menu-text);
  font-size: 13px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.menu :deep(.el-menu-item .el-icon) {
  margin-right: 9px;
  font-size: 15px;
  color: var(--sider-group);
  transition: color 0.15s ease;
}

.menu :deep(.el-menu-item:hover) {
  background: var(--sider-menu-hover-bg);
  color: var(--text-primary);
}

.menu :deep(.el-menu-item:hover .el-icon) {
  color: var(--text-regular);
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--sider-menu-active-bg);
  color: var(--text-primary);
  font-weight: 500;
}

.menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--text-primary);
}

/* ----------------------------------------------------------------- 右侧 */
.body {
  min-width: 0;
}

.header {
  height: var(--header-height);
  background: var(--sider-bg);
  border-bottom: 1px solid var(--sider-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  z-index: 1;
}

.breadcrumb {
  font-size: 12.5px;
  color: var(--text-placeholder);
  display: flex;
  align-items: center;
  gap: 8px;
}

.breadcrumb-sep {
  color: var(--text-placeholder);
}

.breadcrumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

.user {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  outline: none;
  padding: 4px 10px 4px 4px;
  border-radius: 20px;
  transition: background-color 0.15s ease;
}

.user:hover {
  background: var(--sider-menu-active-bg);
}

.user-avatar {
  background: #c8b89a;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
}

.user-name {
  font-size: 13px;
  color: var(--text-primary);
}

.user-caret {
  font-size: 12px;
  color: var(--text-placeholder);
}

.main {
  padding: 0;
  background: var(--page-bg);
  overflow-y: auto;
}

@media (max-width: 900px) {
  .logo { justify-content: center; padding: 0; }
  .logo-text, .menu :deep(.el-menu-item span), .menu-group { display: none; }
  .menu :deep(.el-menu-item) { justify-content: center; padding: 0 !important; }
  .menu :deep(.el-menu-item .el-icon) { margin: 0; }
  .breadcrumb { display: none; }
}
</style>
