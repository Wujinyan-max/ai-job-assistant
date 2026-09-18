import test from 'node:test'
import assert from 'node:assert/strict'
import {
  localAiNoticeKey,
  markLocalAiNoticeShown,
  shouldShowLocalAiNotice
} from '../src/utils/aiLocalNoticeState.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value))
  }
}

test('本地 AI 提示按账号分别记录，真实模型不提示', () => {
  const storage = memoryStorage()
  const firstUser = { id: 17 }
  const secondUser = { id: 23 }

  assert.equal(shouldShowLocalAiNotice({ mock: true }, firstUser, storage), true)
  markLocalAiNoticeShown(firstUser, storage)
  assert.equal(shouldShowLocalAiNotice({ mock: true }, firstUser, storage), false)
  assert.equal(shouldShowLocalAiNotice({ mock: true }, secondUser, storage), true)
  assert.equal(shouldShowLocalAiNotice({ mock: false }, secondUser, storage), false)
  assert.notEqual(localAiNoticeKey(firstUser), localAiNoticeKey(secondUser))
})

test('用户信息损坏时仍使用稳定的匿名提示键', () => {
  assert.equal(localAiNoticeKey('{broken json'), 'jobpath:local-ai-notice:anonymous')
})
