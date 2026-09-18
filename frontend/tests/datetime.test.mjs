import test from 'node:test'
import assert from 'node:assert/strict'
import { fromNowLabel, interviewTimeLabel, isWithinNextDays, parseTime } from '../src/utils/datetime.js'

/** 生成相对当前时间的「后端格式」时间串 yyyy-MM-dd HH:mm:ss */
function shifted({ days = 0, hours = 0, minutes = 0 } = {}) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(date.getHours() + hours, minutes, 0, 0)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

test('parseTime 能解析后端带空格的格式，Safari 也不会拿到 Invalid Date', () => {
  assert.ok(parseTime('2026-09-16 14:30:00') instanceof Date)
  assert.equal(parseTime(''), null)
  assert.equal(parseTime(null), null)
  assert.equal(parseTime('不是时间'), null)
})

test('fromNowLabel 把投递时间讲成人话', () => {
  assert.equal(fromNowLabel(shifted({ days: 0 })), '今天')
  assert.equal(fromNowLabel(shifted({ days: -1 })), '昨天')
  assert.equal(fromNowLabel(shifted({ days: -3 })), '3 天前')
  assert.equal(fromNowLabel(shifted({ days: -45 })), '1 个月前')
  assert.equal(fromNowLabel(shifted({ days: -400 })), '1 年前')
  assert.equal(fromNowLabel(''), '')
})

test('interviewTimeLabel 优先用「今天 / 明天」这种相对说法', () => {
  assert.match(interviewTimeLabel(shifted({ days: 0 })), /^今天 \d{2}:\d{2}$/)
  assert.match(interviewTimeLabel(shifted({ days: 1 })), /^明天 \d{2}:\d{2}$/)
  assert.match(interviewTimeLabel(shifted({ days: 3 })), /^3 天后 \d{2}:\d{2}$/)
  // 超过一周就退回具体日期，避免「15 天后」这种不好换算的说法
  assert.match(interviewTimeLabel(shifted({ days: 20 })), /^\d{2}-\d{2} \d{2}:\d{2}$/)
  assert.equal(interviewTimeLabel(null), '')
})

test('isWithinNextDays 只认未来，过去的时间不算提醒', () => {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  const at = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`

  const tomorrow = new Date(now.getTime() + 24 * 3600 * 1000)
  const yesterday = new Date(now.getTime() - 24 * 3600 * 1000)
  const inTenDays = new Date(now.getTime() + 10 * 24 * 3600 * 1000)

  assert.equal(isWithinNextDays(at(tomorrow), 7), true)
  assert.equal(isWithinNextDays(at(yesterday), 7), false)
  assert.equal(isWithinNextDays(at(inTenDays), 7), false)
  assert.equal(isWithinNextDays(null, 7), false)
})
