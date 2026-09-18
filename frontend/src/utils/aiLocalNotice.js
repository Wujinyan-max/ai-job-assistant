import { ElMessageBox } from 'element-plus'
import { aiApi } from '@/api'
import { markLocalAiNoticeShown, shouldShowLocalAiNotice } from '@/utils/aiLocalNoticeState'

/**
 * 在真正发起 AI 请求前提示一次本地降级模式。返回 false 时，调用方应中止本次请求。
 */
export async function ensureLocalAiNotice({ config, onConfigure } = {}) {
  const currentConfig = config || await aiApi.config().catch(() => null)
  const user = localStorage.getItem('user')
  if (!shouldShowLocalAiNotice(currentConfig, user, localStorage)) {
    return true
  }

  // 弹窗出现就算已经完成首次告知；按账号记录，避免不同账号互相影响。
  markLocalAiNoticeShown(user, localStorage)
  try {
    await ElMessageBox.confirm(
      '当前使用的是本地规则引擎，可以离线体验基础功能。若需要更好的 AI 效果和更准确的语义分析，请配置 API Key。',
      '当前为本地 AI 模式',
      {
        confirmButtonText: '继续使用本地模式',
        cancelButtonText: '去配置 API Key',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    )
    return true
  } catch (action) {
    if (action === 'cancel') {
      onConfigure?.()
    }
    return false
  }
}
