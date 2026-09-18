const NOTICE_PREFIX = 'jobpath:local-ai-notice:'

function parseUser(rawUser) {
  if (!rawUser) return null
  if (typeof rawUser === 'object') return rawUser
  try {
    return JSON.parse(rawUser)
  } catch {
    return null
  }
}

export function localAiNoticeKey(rawUser) {
  const user = parseUser(rawUser)
  const identity = user?.id ?? user?.userId ?? user?.username ?? 'anonymous'
  return `${NOTICE_PREFIX}${identity}`
}

export function shouldShowLocalAiNotice(config, rawUser, storage = localStorage) {
  return Boolean(config?.mock) && storage.getItem(localAiNoticeKey(rawUser)) !== '1'
}

export function markLocalAiNoticeShown(rawUser, storage = localStorage) {
  storage.setItem(localAiNoticeKey(rawUser), '1')
}
