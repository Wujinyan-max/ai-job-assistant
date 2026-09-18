/**
 * 宣传用界面截图
 *
 * 作用：登录 demo 账号，把各页面截成 2 倍高清图，供 docs/poster 的海报模板使用。
 *
 * 用法（先在 frontend 目录下装好依赖）：
 *   node ../scripts/make-shots.cjs
 *
 * 前置条件：后端 8088 与网关 8090 都在跑。
 *   后端：cd backend && java -jar target/job-assistant-1.0.0.jar
 *   网关：cd frontend && node serve.cjs 8090
 */
const path = require('node:path')
const { createRequire } = require('node:module')

const frontendRequire = createRequire(path.join(__dirname, '..', 'frontend', 'package.json'))
const { chromium } = frontendRequire('playwright')

const BASE = process.env.BASE || 'http://localhost:8090'
const OUT = path.join(__dirname, '..', 'docs', 'poster')
const USERNAME = process.env.DEMO_USER || 'demo'
const PASSWORD = process.env.DEMO_PASS || '123456'

const PAGES = ['dashboard', 'board', 'applications', 'jobs', 'resumes', 'interviews', 'questions', 'ai']

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
    deviceScaleFactor: 2
  })
  const page = await ctx.newPage()
  page.setDefaultTimeout(20000)

  const shoot = (name) => page.screenshot({
    path: path.join(OUT, name + '.png'),
    animations: 'disabled',
    caret: 'hide'
  })

  // 路由是 hash 模式，必须用 /#/xxx
  // 直接进登录页（路由是 hash 模式）；根路径会按登录态跳到首页或看板
  await page.goto(BASE + '/#/login', { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(1500)
  await page.waitForSelector('input[placeholder="用户名"]', { timeout: 30000 })

  await page.fill('input[placeholder="用户名"]', USERNAME)
  await page.fill('input[placeholder="密码"]', PASSWORD)
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(4500)

  if (!page.url().includes('dashboard')) {
    throw new Error('登录失败，请确认 demo 账号存在（可跑 scripts/seed-demo.ps1 灌数据）')
  }

  for (const name of PAGES) {
    try {
      await page.evaluate((h) => { location.hash = '#/' + h }, name)
      await page.waitForTimeout(3000)
      await shoot(name)
      console.log('ok', name)
    } catch (e) {
      console.log('fail', name, e.message.split('\n')[0])
    }
  }

  await browser.close()
})()
