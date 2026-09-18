/** Capture the current AI workspace for the AI-first poster. */
const path = require('node:path')
const { createRequire } = require('node:module')

const frontendRequire = createRequire(path.join(__dirname, '..', 'frontend', 'package.json'))
const { chromium } = frontendRequire('playwright')

const ROOT = path.resolve(__dirname, '..')
const BASE = process.env.BASE || 'http://localhost:5173'
const OUT = path.join(ROOT, 'docs', 'poster', 'ai-workspace-latest.png')
const USERNAME = process.env.DEMO_USER || 'demo'
const PASSWORD = process.env.DEMO_PASS || '123456'

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
    deviceScaleFactor: 2
  })
  const page = await ctx.newPage()
  page.setDefaultTimeout(25000)

  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 40000 })
  await page.fill('input[placeholder="用户名"]', USERNAME)
  await page.fill('input[placeholder="密码"]', PASSWORD)
  await page.click('button:has-text("登 录")')
  await page.waitForURL(/dashboard/, { timeout: 30000 })

  await page.goto(BASE + '/#/ai', { waitUntil: 'networkidle', timeout: 40000 })
  await page.getByRole('tab', { name: '简历匹配' }).click()
  await page.waitForTimeout(2500)

  // Element Plus keeps inactive tab panes in the DOM, so scope controls to
  // the visible pane rather than selecting the first hidden tab's fields.
  const selects = page.locator('.el-tab-pane:visible .el-select')
  await selects.nth(0).click()
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: 'AI测试工程师' }).click()
  await page.waitForTimeout(700)
  await selects.nth(1).click()
  await page.waitForTimeout(400)
  await page.getByRole('option', { name: '测试工程师', exact: true }).click()

  await page.waitForTimeout(3500)
  await page.screenshot({ path: OUT, animations: 'disabled', caret: 'hide' })
  console.log(OUT)
  await browser.close()
})().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
