/**
 * AI 长任务进行中的提示文案。
 *
 * 页面顶部的提示条和「离开页面」的二次确认共用这一份定义，避免两处说法对不上。
 * 后端会把结果落到 ai_analysis，但前端只有 JD 解析和简历匹配做了历史回填，
 * 所以按「离开后还能不能找回结果」分三档，提示的语气也不一样。
 */

/** 本页会自动回填历史结果，切走基本不用重跑 */
export const RETENTION_HISTORY = 'history'
/** 结果写回简历的 content_json，重新选中这份简历就能找回 */
export const RETENTION_RESUME = 'resume'
/** 结果只留在内存里，切走或刷新就没了 */
export const RETENTION_DISCARD = 'discard'

const DURATION_HINT = 'AI 跑一次大概几十秒到几分钟，'

const RETENTION_DETAIL = {
  [RETENTION_HISTORY]: '跑完前请留在本页。切走不会中断后台计算，回到本页会自动带出上次结果。',
  [RETENTION_RESUME]: '跑完前请留在本页。切走后重新选中这份简历，就能找回识别结果。',
  [RETENTION_DISCARD]: '跑完前请留在本页。切走或刷新会让这次结果直接丢掉，得重新跑一次（再花一次 token）。'
}

const RETENTION_LEAVE = {
  [RETENTION_HISTORY]: '现在切走不会中断后台计算，回来后会自动带出结果，不额外花 token。确定离开吗？',
  [RETENTION_RESUME]: '现在切走，回来后要重新选中这份简历才能看到识别结果。确定离开吗？',
  [RETENTION_DISCARD]: '现在切走，这次结果会直接丢掉，需要重新跑一次（再花一次 token）。确定离开吗？'
}

/** 从轻到重排序，多个任务同时跑时按最重的那档提示，宁可多说一句 */
const RETENTION_WEIGHT = {
  [RETENTION_HISTORY]: 1,
  [RETENTION_RESUME]: 2,
  [RETENTION_DISCARD]: 3
}

/** 数组顺序即优先级：多个任务同时在跑时，提示条只细说排在最前面那个 */
export const AI_RUNNING_TASKS = [
  { key: 'jd', tab: 'jd', tabLabel: 'JD 解析', action: '正在解析 JD', retention: RETENTION_HISTORY },
  { key: 'match', tab: 'match', tabLabel: '简历匹配', action: '正在分析简历匹配度', retention: RETENTION_HISTORY },
  { key: 'gap', tab: 'optimize', tabLabel: '简历优化', action: '正在导入匹配缺口', retention: RETENTION_DISCARD },
  { key: 'optimize', tab: 'optimize', tabLabel: '简历优化', action: '正在按岗位优化简历', retention: RETENTION_DISCARD },
  { key: 'structure', tab: 'structure', tabLabel: '简历排版', action: '正在识别简历结构', retention: RETENTION_RESUME },
  { key: 'quiz', tab: 'question', tabLabel: 'AI 出题', action: '正在生成面试题', retention: RETENTION_DISCARD }
]

/** 挑出正在跑的任务：state 是各任务 loading 状态的布尔映射 */
export function pickRunningTasks(state) {
  return AI_RUNNING_TASKS.filter((item) => Boolean(state?.[item.key]))
}

/**
 * 组装提示条与离开确认要用的文案。没有任务在跑时返回 null，调用方直接不显示。
 */
export function buildRunningNotice(state) {
  const tasks = pickRunningTasks(state)
  if (!tasks.length) {
    return null
  }
  const first = tasks[0]
  const labels = tasks.map((item) => item.tabLabel)
  const retention = tasks
    .map((item) => item.retention)
    .reduce((worst, item) => (RETENTION_WEIGHT[item] > RETENTION_WEIGHT[worst] ? item : worst), RETENTION_HISTORY)
  return {
    count: tasks.length,
    title: tasks.length > 1
      ? `${tasks.length} 个 AI 任务正在执行：${labels.join('、')}`
      : `AI 正在执行：${first.action}（${first.tabLabel}）`,
    detail: DURATION_HINT + RETENTION_DETAIL[retention],
    focusTab: first.tab,
    focusLabel: first.tabLabel,
    leaveTitle: 'AI 还在执行',
    leaveMessage: `${labels.map((label) => `「${label}」`).join('')}还没跑完。${RETENTION_LEAVE[retention]}`
  }
}
