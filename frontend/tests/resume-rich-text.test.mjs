import test from 'node:test'
import assert from 'node:assert/strict'
import { htmlToPlainText, projectToHtml, sanitizeResumeHtml, textToHtml } from '../src/utils/resumeRichText.js'

test('旧项目描述和要点会合并成可编辑正文', () => {
  assert.equal(
    projectToHtml({ summary: 'Spring Boot 项目', bullets: ['负责接口测试', '响应时间降低 40%'] }),
    '<p>Spring Boot 项目</p><ol><li>负责接口测试</li><li>响应时间降低 40%</li></ol>'
  )
})

test('富文本只保留简历需要的格式并过滤危险内容', () => {
  const html = '<p onclick="bad()"><strong>成果</strong><script>alert(1)</script>'
    + '<a href="javascript:alert(2)">链接</a><a href="https://example.com">作品</a></p>'
    + '<div style="position:fixed;text-align:center"><font color="#e11d48">重点</font></div>'
    + '<img src="javascript:alert(3)"><img src="https://example.com/demo.png" alt="项目截图">'
  assert.equal(
    sanitizeResumeHtml(html),
    '<p><strong>成果</strong>alert(1)<a>链接</a><a href="https://example.com" target="_blank" rel="noopener noreferrer">作品</a></p>'
      + '<div style="text-align:center"><font color="#e11d48">重点</font></div>'
      + '<img><img src="https://example.com/demo.png" alt="项目截图">'
  )
})

test('普通文本可以迁移为富文本并反向提取保存摘要', () => {
  assert.equal(textToHtml('第一行\n第二行'), '<p>第一行<br>第二行</p>')
  assert.equal(htmlToPlainText('<p>第一行<br>第二行</p><ol><li>成果 A</li><li>成果 B</li></ol>'), '第一行\n第二行\n成果 A\n成果 B')
})
