import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import {
  AI_RUNNING_TASKS,
  RETENTION_HISTORY,
  RETENTION_RESUME,
  RETENTION_DISCARD,
  buildRunningNotice,
  pickRunningTasks
} from '../src/utils/aiRunningNotice.js'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('没有任务在跑时不显示提示', () => {
  assert.equal(buildRunningNotice({}), null)
  assert.equal(buildRunningNotice(), null)
  assert.deepEqual(pickRunningTasks({ jd: false, match: false, quiz: false }), [])
})

test('能找回结果的任务提示「回来还有」，找不回的明确说要重跑', () => {
  const match = buildRunningNotice({ match: true })
  assert.match(match.title, /简历匹配/)
  assert.match(match.detail, /切走不会中断/)

  const optimize = buildRunningNotice({ optimize: true })
  assert.match(optimize.title, /按岗位优化简历/)
  assert.match(optimize.detail, /切走或刷新会让这次结果直接丢掉/)
  assert.match(optimize.leaveMessage, /再花一次 token/)

  const structure = buildRunningNotice({ structure: true })
  assert.match(structure.detail, /重新选中这份简历/)
})

test('多个任务同时跑时按最严重的一档提示，并给出跳转的页签', () => {
  const notice = buildRunningNotice({ jd: true, optimize: true })
  assert.equal(notice.count, 2)
  assert.match(notice.title, /2 个 AI 任务/)
  assert.match(notice.title, /JD 解析/)
  assert.match(notice.title, /简历优化/)
  // JD 可回填、优化不可，按后者提示
  assert.match(notice.detail, /直接丢掉/)
  assert.equal(notice.focusTab, 'jd')
})

test('每个任务的字段完整，页签名与提示条跳转对得上', () => {
  for (const task of AI_RUNNING_TASKS) {
    assert.ok(task.key && task.tab && task.tabLabel && task.action, task.key)
    assert.ok([RETENTION_HISTORY, RETENTION_RESUME, RETENTION_DISCARD].includes(task.retention), task.key)
  }
  assert.deepEqual(AI_RUNNING_TASKS.map((item) => item.tab),
    ['jd', 'match', 'optimize', 'optimize', 'structure', 'question'])
})

test('AI 助手页接入进行中提示，并在离开页面前二次确认', async () => {
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /import \{ buildRunningNotice \} from '@\/utils\/aiRunningNotice'/)
  assert.match(ai, /const runningNotice = computed\(\(\) => buildRunningNotice/)
  assert.match(ai, /AI 跑一次大概|runningNotice\.detail/)
  assert.match(ai, /onBeforeRouteLeave/)
  assert.match(ai, /await ElMessageBox\.confirm\(runningNotice\.value\.leaveMessage/)
  // 六个入口的 loading 都要汇总进去，漏一个就会出现「跑着却提示没跑」
  for (const key of ['jd', 'match', 'gap', 'optimize', 'structure', 'quiz']) {
    assert.match(ai, new RegExp(`${key}: ${key === 'gap' ? 'optimize\\.gapLoading' : `${key}\\.loading`}`), key)
  }
})

test('离开确认只拦有任务在跑的情况，不打扰普通切页', async () => {
  const ai = await read('src/views/AiView.vue')
  const guard = ai.slice(ai.indexOf('onBeforeRouteLeave'))
  assert.match(guard, /if \(!runningNotice\.value\) \{\s*return true/)
  assert.match(guard, /留下等结果/)
  assert.match(guard, /return false/)
})
