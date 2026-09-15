import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('公共布局采用原型的紧凑工作台比例', async () => {
  const css = await read('src/assets/main.css')
  assert.match(css, /--sider-width:\s*196px/)
  assert.match(css, /--content-max:\s*1440px/)
  assert.match(css, /\.page-enter/)
})

test('首页包含原型中的问候与日期信息区', async () => {
  const dashboard = await read('src/views/DashboardView.vue')
  assert.match(dashboard, /dashboard-hero/)
  assert.match(dashboard, /下午好/)
  assert.match(dashboard, /今日专注/)
})

test('管理页在内容区展示明确的页面标题', async () => {
  const pages = [
    ['CompanyView.vue', '公司管理'],
    ['JobView.vue', '职位管理'],
    ['ApplicationView.vue', '投递记录'],
    ['InterviewView.vue', '面试管理'],
    ['QuestionView.vue', '面试题库']
  ]

  for (const [file, title] of pages) {
    const source = await read(`src/views/${file}`)
    assert.match(source, new RegExp(`<h2 class="page-title">${title}</h2>`), file)
  }
})

test('AI 助手提供用户级厂商和双协议配置入口', async () => {
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /配置模型/)
  assert.match(ai, /CHAT_COMPLETIONS/)
  assert.match(ai, /RESPONSES/)
  assert.match(ai, /API Key/)
  assert.match(ai, /测试连接/)
})
