/**
 * 前端统一调色板
 *
 * 与 assets/main.css 中的设计令牌保持一致：图表、状态标签、评分色都从这里取值，
 * 避免每个页面各写一套 hex。
 */

/* 品牌色 */
export const BRAND = '#9c7c3c'
export const BRAND_HOVER = '#b08d47'

/* 语义色 */
export const SUCCESS = '#4a7c59'
export const WARNING = '#b5822a'
export const DANGER = '#b0503c'

/* 中性色 */
export const TEXT_REGULAR = '#3d4148'
export const TEXT_SECONDARY = '#8a8a85'
export const BORDER = '#e8e6e1'
export const SPLIT_LINE = '#f0eee8'

/* 坐标轴 / 分隔线的通用样式，直接丢给 ECharts */
export const AXIS_LABEL = { color: TEXT_SECONDARY }
export const AXIS_LINE = { lineStyle: { color: BORDER } }
export const SPLIT_LINE_STYLE = { lineStyle: { color: SPLIT_LINE } }

/* 投递状态色：与后端 ApplicationStatus 枚举一一对应 */
export const STATUS_COLORS = {
  WISHLIST: '#a8a49c',
  APPLIED: BRAND_HOVER,
  WRITTEN_TEST: WARNING,
  INTERVIEW: BRAND,
  OFFER: SUCCESS,
  REJECTED: DANGER,
  CLOSED: '#8a8578'
}

export const statusColor = (status) => STATUS_COLORS[status] || STATUS_COLORS.WISHLIST

/* 匹配度分档配色 */
export const scoreColor = (score) =>
  score >= 85 ? SUCCESS : score >= 70 ? BRAND : score >= 50 ? WARNING : DANGER
