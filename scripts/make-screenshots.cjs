/**
 * README 功能截图生成
 *
 * 作用：登录 demo 账号，把各页面截成 2 倍高清图，输出到 docs/screenshots/，
 *       供 README 的「功能截图」章节引用。
 *
 * 用法（先在 frontend 目录下装好依赖）：
 *   node ../scripts/make-screenshots.cjs
 *
 * 前置条件：后端 8088 与网关 8090 都在跑，且网关指向最新构建产物。
 *   后端：cd backend && java -jar target/job-assistant-1.0.0.jar
 *   前端：cd frontend && npm run build && node serve.cjs 8090
 *   演示数据：powershell -ExecutionPolicy Bypass -File scripts/seed-demo.ps1
 */
const path = require('node:path')
const { createRequire } = require('node:module')

const frontendRequire = createRequire(path.join(__dirname, '..', 'frontend', 'package.json'))
const { chromium } = frontendRequire('playwright')

const BASE = process.env.BASE || 'http://localhost:8090'
const OUT = path.join(__dirname, '..', 'docs', 'screenshots')
const USERNAME = process.env.DEMO_USER || 'demo'
const PASSWORD = process.env.DEMO_PASS || '123456'

// 文件名与 README「功能截图」章节一一对应，就地覆盖旧图
const PAGES = [
  ['01-dashboard', 'dashboard', '数据看板'],
  ['02-board', 'board', '求职看板'],
  ['03-applications', 'applications', '投递记录'],
  ['04-jobs', 'jobs', '职位管理'],
  ['09-companies', 'companies', '公司管理'],
  ['08-resumes', 'resumes', '简历管理'],
  ['10-profile', 'profile', '个人中心'],
  ['05-interviews', 'interviews', '面试管理'],
  ['06-questions', 'questions', '面试题库'],
  ['07-ai', 'ai', 'AI 助手']
]

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
    deviceScaleFactor: 2
  })
  const page = await ctx.newPage()
  page.setDefaultTimeout(25000)

  const shoot = (name) => page.screenshot({
    path: path.join(OUT, name + '.png'),
    animations: 'disabled',
    caret: 'hide'
  })

  // 路由是 hash 模式，必须用 /#/xxx
  await page.goto(BASE + '/#/login', { waitUntil: 'load', timeout: 40000 })
  await page.waitForTimeout(1500)
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 30000 })
  await shoot('00-login')
  console.log('ok', 'login')

  await page.fill('input[placeholder="用户名"]', USERNAME)
  await page.fill('input[placeholder="密码"]', PASSWORD)
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(4500)

  if (!page.url().includes('dashboard')) {
    throw new Error('登录失败，请确认 demo 账号存在（可跑 scripts/seed-demo.ps1 灌数据）')
  }

  for (const [file, name, label] of PAGES) {
    try {
      await page.evaluate((h) => { location.hash = '#/' + h }, name)
      await page.waitForTimeout(3000)
      await shoot(file)
      console.log('ok', name, label)
    } catch (e) {
      console.log('fail', name, e.message.split('\n')[0])
    }
  }

  await browser.close()
})()
