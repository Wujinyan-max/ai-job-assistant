/**
 * A4 排版模板登记表。
 *
 * 版式参考了下面这些开源项目，全部是 MIT 许可（允许商用、允许修改、保留版权声明即可）：
 *   jsonresume/jsonresume-theme-class        MIT  https://github.com/jsonresume/jsonresume-theme-class
 *   rbardini/jsonresume-theme-even           MIT  https://github.com/rbardini/jsonresume-theme-even
 *   davcd/jsonresume-theme-actual            MIT  https://github.com/davcd/jsonresume-theme-actual
 *   phoinixi/jsonresume-theme-stackoverflow  MIT  https://github.com/phoinixi/jsonresume-theme-stackoverflow
 *   mudassir0909/jsonresume-theme-elegant    MIT  https://github.com/mudassir0909/jsonresume-theme-elegant
 *
 * 只借版式思路，样式表由本项目自己写（src/assets/resume-templates.css），
 * 没有复制这些项目的代码，也没有引入它们的运行时依赖。
 *
 * 反面例子：WebPraktikos/universal-resume 是 CC BY-NC-SA 1.0，
 * 禁止商用且带相同方式共享的传染性，不能拿来做产品模板，别参考它。
 */

export const RESUME_TEMPLATES = [
  {
    id: 'classic',
    name: '经典单栏',
    hint: '姓名居中 + 蓝色小节条，最稳妥，国内投递通用',
    source: { label: 'jsonresume-theme-class', url: 'https://github.com/jsonresume/jsonresume-theme-class', license: 'MIT' }
  },
  {
    id: 'even',
    name: '扁平极简',
    hint: '灰色头部通栏 + 左标题右正文两栏，技能做成标签块',
    source: { label: 'jsonresume-theme-even', url: 'https://github.com/rbardini/jsonresume-theme-even', license: 'MIT' }
  },
  {
    id: 'actual',
    name: '单色留白',
    hint: '全大写小节标题 + 细分隔线，只有黑白灰，ATS 最友好',
    source: { label: 'jsonresume-theme-actual', url: 'https://github.com/davcd/jsonresume-theme-actual', license: 'MIT' }
  },
  {
    id: 'sidebar',
    name: '左侧深色栏',
    hint: '深色侧栏放姓名、联系方式和技能，右侧放经历正文',
    source: { label: 'jsonresume-theme-stackoverflow', url: 'https://github.com/phoinixi/jsonresume-theme-stackoverflow', license: 'MIT' }
  },
  {
    id: 'elegant',
    name: '时间轴',
    hint: '左侧竖轴加圆点，正式保守，适合工作年限长的简历',
    source: { label: 'jsonresume-theme-elegant', url: 'https://github.com/mudassir0909/jsonresume-theme-elegant', license: 'MIT' }
  }
]

export const DEFAULT_RESUME_TEMPLATE = 'classic'

/**
 * 「原版复刻」：不是一套固定版式，而是把用户导入的那份简历的配色、头像与页眉特征
 * 还原出来。它不参与常规的模板下拉框（避免用户手动选到它却没有任何样式数据），
 * 由排版页检测到简历里存了 styleJson 时自动启用。
 * 放在这里是为了让 isResumeTemplate 也认它，存进 content_json 后重开还能生效。
 */
export const REPLICA_TEMPLATE = {
  id: 'replica',
  name: '原版复刻',
  hint: '沿用你导入的简历里的配色、头像与页眉样式',
  // 版式不是抄来的，是解析你自己那份简历得到的，所以没有上游来源
  source: null
}

/** 找不到就退回默认模板，避免老简历里存了个已删除的模板 id 导致白屏 */
export function findResumeTemplate(id) {
  return RESUME_TEMPLATES.find((item) => item.id === id)
    || (id === REPLICA_TEMPLATE.id ? REPLICA_TEMPLATE : undefined)
    || RESUME_TEMPLATES.find((item) => item.id === DEFAULT_RESUME_TEMPLATE)
}

/** 模板 id 白名单，用于校验存进 content_json 的值 */
export function isResumeTemplate(id) {
  return id === REPLICA_TEMPLATE.id || RESUME_TEMPLATES.some((item) => item.id === id)
}
