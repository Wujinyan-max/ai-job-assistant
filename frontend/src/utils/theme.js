/**
 * 前端统一调色板
 *
 * 与 assets/main.css 中的设计令牌保持一致：图表、状态标签、评分色都从这里取值，
 * 避免每个页面各写一套 hex。
 */

/* 品牌色 */
export const BRAND = '#3b72f5'
export const BRAND_HOVER = '#5a8bf7'

/* 语义色 */
export const SUCCESS = '#16a34a'
export const WARNING = '#e8a90c'
export const DANGER = '#dc4c4c'

/* 中性色 */
export const TEXT_REGULAR = '#4b5568'
export const TEXT_SECONDARY = '#8a90a2'
export const BORDER = '#e6eaf2'
export const SPLIT_LINE = '#f0f3f8'

/* 坐标轴 / 分隔线的通用样式，直接丢给 ECharts */
export const AXIS_LABEL = { color: TEXT_SECONDARY }
export const AXIS_LINE = { lineStyle: { color: BORDER } }
export const SPLIT_LINE_STYLE = { lineStyle: { color: SPLIT_LINE } }

/* 投递状态色：与后端 ApplicationStatus 枚举一一对应 */
export const STATUS_COLORS = {
  WISHLIST: '#a8aebd',
  APPLIED: BRAND_HOVER,
  WRITTEN_TEST: WARNING,
  INTERVIEW: BRAND,
  OFFER: SUCCESS,
  REJECTED: DANGER,
  CLOSED: '#6f7a8c'
}

export const statusColor = (status) => STATUS_COLORS[status] || STATUS_COLORS.WISHLIST

/* 匹配度分档配色 */
export const scoreColor = (score) =>
  score >= 85 ? SUCCESS : score >= 70 ? BRAND : score >= 50 ? WARNING : DANGER
