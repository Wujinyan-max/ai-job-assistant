import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据看板', icon: 'DataAnalysis' } },
      { path: 'companies', name: 'companies', component: () => import('@/views/CompanyView.vue'), meta: { title: '公司管理', icon: 'OfficeBuilding' } },
      { path: 'jobs', name: 'jobs', component: () => import('@/views/JobView.vue'), meta: { title: '职位管理', icon: 'Briefcase' } },
      { path: 'resumes', name: 'resumes', component: () => import('@/views/ResumeView.vue'), meta: { title: '简历管理', icon: 'Document' } },
      { path: 'applications', name: 'applications', component: () => import('@/views/ApplicationView.vue'), meta: { title: '投递记录', icon: 'Tickets' } },
      { path: 'board', name: 'board', component: () => import('@/views/BoardView.vue'), meta: { title: '求职看板', icon: 'Grid' } },
      { path: 'interviews', name: 'interviews', component: () => import('@/views/InterviewView.vue'), meta: { title: '面试管理', icon: 'Calendar' } },
      { path: 'questions', name: 'questions', component: () => import('@/views/QuestionView.vue'), meta: { title: '面试题库', icon: 'Notebook' } },
      { path: 'ai', name: 'ai', component: () => import('@/views/AiView.vue'), meta: { title: 'AI 助手', icon: 'MagicStick' } },
      { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人中心', icon: 'User' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/dashboard' }
  }
  document.title = to.meta.title ? `${to.meta.title} · AI 求职管理平台` : 'AI 求职管理平台'
  return true
})

export default router
