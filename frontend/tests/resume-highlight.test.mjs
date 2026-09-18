import test from 'node:test'
import assert from 'node:assert/strict'
import { splitHighlightedText } from '../src/utils/resumeHighlight.js'

test('复刻简历只高亮 PDF 中实际着色的关键词', () => {
  const parts = splitHighlightedText(
    '熟悉 Python+Selenium 框架，了解 Redis 与 MySQL',
    ['Redis', 'Python+Selenium']
  )

  assert.deepEqual(parts, [
    { text: '熟悉 ', highlighted: false },
    { text: 'Python+Selenium', highlighted: true },
    { text: ' 框架，了解 ', highlighted: false },
    { text: 'Redis', highlighted: true },
    { text: ' 与 MySQL', highlighted: false }
  ])
})

test('重叠高亮词优先匹配更长的原文片段', () => {
  assert.deepEqual(splitHighlightedText('Spring Boot', ['Spring', 'Spring Boot']), [
    { text: 'Spring Boot', highlighted: true }
  ])
})
