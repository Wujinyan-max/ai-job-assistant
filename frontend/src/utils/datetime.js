/**
 * 时间展示工具。
 *
 * 后端返回的是 `yyyy-MM-dd HH:mm:ss` 字符串，直接丢给 `new Date()` 在 Safari 上会得到
 * Invalid Date，所以统一在这里把「空格」换成「T」再解析，全站只走这一条路径。
 */

/** 把后端返回的时间字符串解析成 Date，解析不出来返回 null */
export function parseTime(value) {
  if (!value) return null
  if (value instanceof Date) return value
  const text = String(value).trim().replace(' ', 'T')
  const date = new Date(text)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 是否同一天（按本地时区） */
function isSameDay(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

/** 相差的自然天数：今天 0，昨天 -1，明天 1 */
function diffInDays(target, base) {
  const start = new Date(target.getFullYear(), target.getMonth(), target.getDate())
  const end = new Date(base.getFullYear(), base.getMonth(), base.getDate())
  return Math.round((start - end) / 86400000)
}

const pad = (value) => String(value).padStart(2, '0')

/**
 * 相对今天的口语化描述，用于看板卡片的「距今天数」。
 * @param {string} value 后端时间字符串
 * @param {{ suffix?: string }} options suffix 会拼在结果后面，默认拼「前」
 * @returns {string} 例如「今天」「3 天前」「1 个月前」，无有效时间时返回空串
 */
export function fromNowLabel(value, options = {}) {
  const date = parseTime(value)
  if (!date) return ''
  const { suffix = '前' } = options
  const days = diffInDays(date, new Date())

  if (days === 0) return '今天'
  if (days === -1) return `昨天`
  if (days === 1) return '明天'

  const ago = Math.abs(days)
  if (ago < 30) return `${ago} 天${days < 0 ? suffix : '后'}`
  const months = Math.floor(ago / 30)
  if (months < 12) return `${months} 个月${days < 0 ? suffix : '后'}`
  return `${Math.floor(months / 12)} 年${days < 0 ? suffix : '后'}`
}

/**
 * 面试时间的口语化描述，用于看板卡片的「下次面试时间」。
 * @returns {string} 例如「明天 14:00」「今天 09:30」「3 天后 14:00」「09-20 14:00」
 */
export function interviewTimeLabel(value) {
  const date = parseTime(value)
  if (!date) return ''
  const clock = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  const days = diffInDays(date, new Date())

  if (days === 0) return `今天 ${clock}`
  if (days === 1) return `明天 ${clock}`
  if (days === -1) return `昨天 ${clock}`
  if (days > 1 && days <= 7) return `${days} 天后 ${clock}`
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${clock}`
}

/** 是否落在未来 N 天内（含今天，用于「7 天内面试」红点） */
export function isWithinNextDays(value, days) {
  const date = parseTime(value)
  if (!date) return false
  const now = new Date()
  return date >= now && diffInDays(date, now) < days
}

export { isSameDay, diffInDays }
