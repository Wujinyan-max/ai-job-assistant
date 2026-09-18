/**
 * 宣传海报一键生成
 *
 * 作用：用 Playwright 把 docs/poster 下的 HTML 模板渲染成 2 倍高清图，
 *       界面截图和海报都是真实渲染结果，不是拼贴。
 *
 * 用法（在 frontend 目录下执行，因为 playwright 装在这里）：
 *   node ../scripts/make-poster.cjs
 *
 * 想先更新界面截图：
 *   node tests/poster-shot.cjs      # 需要后端 8088 + 网关 8090 在跑
 */
const path = require('node:path')
const fs = require('node:fs')
const { createRequire } = require('node:module')

// playwright 装在 frontend 里，这里显式指向它，脚本放哪都能跑
const frontendRequire = createRequire(path.join(__dirname, '..', 'frontend', 'package.json'))
const { chromium } = frontendRequire('playwright')

const ROOT = path.resolve(__dirname, '..')
const POSTER_DIR = path.join(ROOT, 'docs', 'poster')

const TARGETS = [
  ['poster.html', 'jobpath-poster.png'],
  ['poster-portrait.html', 'jobpath-poster-portrait.png'],
  ['poster-ai-portrait.html', 'jobpath-ai-poster-portrait.png']
]

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({
    viewport: { width: 1240, height: 700 },
    deviceScaleFactor: 2
  })
  const page = await ctx.newPage()

  for (const [html, out] of TARGETS) {
    const src = path.join(POSTER_DIR, html)
    if (!fs.existsSync(src)) {
      console.log('skip (missing):', html)
      continue
    }
    await page.goto('file:///' + src.replace(/\\/g, '/'), { waitUntil: 'load', timeout: 40000 })
    await page.waitForTimeout(2200)

    const box = await page.evaluate(() => {
      const r = document.querySelector('.poster').getBoundingClientRect()
      return { w: Math.ceil(r.width), h: Math.ceil(r.height) }
    })

    const dest = path.join(POSTER_DIR, out)
    await page.screenshot({
      path: dest,
      clip: { x: 0, y: 0, width: box.w, height: box.h },
      fullPage: true,
      animations: 'disabled'
    })

    const st = fs.statSync(dest)
    console.log(`${out}  ${box.w * 2}x${box.h * 2}px  ${(st.size / 1024).toFixed(0)}KB`)
  }

  await browser.close()
})()
