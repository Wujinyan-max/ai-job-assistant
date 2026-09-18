<template>
  <div class="resume-sheet" :class="sheetClasses" :style="styleVars">
    <header class="sheet-header" data-block="basics">
      <img v-if="showAvatar" class="sheet-avatar" :src="avatar" alt="简历头像" />
      <div class="sheet-name-row">
        <h1 class="sheet-name">{{ basics.name || '未识别到姓名' }}</h1>
        <span v-if="basics.label" class="sheet-label">{{ basics.label }}</span>
      </div>
      <div v-if="contacts.length" class="sheet-contacts">
        <span v-for="item in contacts" :key="item">{{ item }}</span>
      </div>
    </header>

    <section v-if="basics.summaryHtml || basics.summary" class="sheet-section" data-block="summary">
      <h2 class="sheet-title">
        <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><User /></el-icon></span>
        个人简介
      </h2>
      <div v-if="basics.summaryHtml" class="sheet-rich-text" v-html="safeSummaryHtml()"></div>
      <p v-else class="sheet-paragraph"><HighlightedText :text="basics.summary" :terms="accentTerms" /></p>
    </section>

    <section v-if="education.length" class="sheet-section" data-block="education">
      <h2 class="sheet-title">
        <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><School /></el-icon></span>
        教育背景
      </h2>
      <div v-for="(item, index) in education" :key="'edu' + index" class="sheet-item">
        <div class="sheet-item-head">
          <span class="sheet-item-main">{{ item.school || '—' }}<template v-if="item.major"> · {{ item.major }}</template></span>
          <span class="sheet-item-meta">{{ [item.degree, item.period].filter(Boolean).join(' | ') }}</span>
        </div>
        <div v-if="item.detailHtml" class="sheet-rich-text" v-html="safeEducationHtml(item)"></div>
        <p v-else-if="item.detail" class="sheet-detail"><HighlightedText :text="item.detail" :terms="accentTerms" /></p>
      </div>
    </section>

    <section v-if="work.length" class="sheet-section" data-block="work">
      <h2 class="sheet-title">
        <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><Suitcase /></el-icon></span>
        工作 / 实习经历
      </h2>
      <div v-for="(item, index) in work" :key="'work' + index" class="sheet-item">
        <div class="sheet-item-head">
          <span class="sheet-item-main">{{ item.company || '—' }}<template v-if="item.position"> · {{ item.position }}</template></span>
          <span class="sheet-item-meta">{{ item.period }}</span>
        </div>
        <div v-if="item.contentHtml" class="sheet-rich-text" v-html="safeWorkHtml(item)"></div>
        <ul v-else-if="item.bullets && item.bullets.length" class="sheet-bullets">
          <li v-for="(bullet, bulletIndex) in item.bullets" :key="bulletIndex"><HighlightedText :text="bullet" :terms="accentTerms" /></li>
        </ul>
      </div>
    </section>

    <section v-if="projects.length" class="sheet-section" data-block="projects">
      <h2 class="sheet-title">
        <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><Folder /></el-icon></span>
        项目经历
      </h2>
      <div v-for="(item, index) in projects" :key="'project' + index" class="sheet-item">
        <div class="sheet-item-head">
          <span class="sheet-item-main">{{ item.name || '—' }}<template v-if="item.role"> · {{ item.role }}</template></span>
          <span class="sheet-item-meta">{{ item.period }}</span>
        </div>
        <div v-if="item.contentHtml" class="sheet-rich-text" v-html="safeProjectHtml(item)"></div>
        <template v-else>
          <p v-if="item.summary" class="sheet-detail"><HighlightedText :text="item.summary" :terms="accentTerms" /></p>
          <ul v-if="item.bullets && item.bullets.length" class="sheet-bullets">
            <li v-for="(bullet, bulletIndex) in item.bullets" :key="bulletIndex"><HighlightedText :text="bullet" :terms="accentTerms" /></li>
          </ul>
        </template>
      </div>
    </section>

    <section v-if="skills.length" class="sheet-section" data-block="skills">
      <h2 class="sheet-title">
        <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><Tools /></el-icon></span>
        专业技能
      </h2>
      <ul class="sheet-bullets sheet-skills">
        <li v-for="(item, index) in skills" :key="index">
          <span v-if="looksRich(item)" class="sheet-rich-inline" v-html="safeListItemHtml(item)"></span>
          <HighlightedText v-else :text="item" :terms="accentTerms" />
        </li>
      </ul>
    </section>

    <section v-if="honors.length" class="sheet-section" data-block="honors">
      <h2 class="sheet-title">
        <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><Medal /></el-icon></span>
        荣誉证书
      </h2>
      <ul class="sheet-bullets">
        <li v-for="(item, index) in honors" :key="index">
          <span v-if="looksRich(item)" class="sheet-rich-inline" v-html="safeListItemHtml(item)"></span>
          <HighlightedText v-else :text="item" :terms="accentTerms" />
        </li>
      </ul>
    </section>

    <section v-for="(section, sectionIndex) in visibleSections" :key="'custom' + sectionIndex"
             class="sheet-section" data-block="custom">
      <div class="sheet-title-bar">
        <h2 class="sheet-title">
          <span v-if="showSectionNode" class="sheet-section-node" aria-hidden="true"><el-icon><Files /></el-icon></span>
          {{ section.title }}
        </h2>
      </div>
      <ul v-if="section.items && section.items.length" class="sheet-bullets">
        <li v-for="(item, itemIndex) in section.items" :key="itemIndex"><HighlightedText :text="item" :terms="accentTerms" /></li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { DEFAULT_RESUME_TEMPLATE, REPLICA_TEMPLATE, findResumeTemplate } from './resumeTemplates'
import HighlightedText from './HighlightedText.vue'
import { sanitizeResumeHtml } from '@/utils/resumeRichText'

const props = defineProps({
  structure: { type: Object, default: () => ({}) },
  template: { type: String, default: DEFAULT_RESUME_TEMPLATE },
  avatar: { type: String, default: '' },
  style: { type: Object, default: null }
})

const data = computed(() => props.structure || {})
const basics = computed(() => data.value.basics || {})
const list = (key) => computed(() => Array.isArray(data.value[key]) ? data.value[key] : [])
const education = list('education')
const work = list('work')
const projects = list('projects')
const skills = list('skills')
const honors = list('honors')
const sections = list('sections')
const visibleSections = computed(() => sections.value.filter((section) => section && (section.title || section.items?.length)))
const contacts = computed(() => [basics.value.phone, basics.value.email, basics.value.city, basics.value.workYears].filter(Boolean))
const safeSummaryHtml = () => sanitizeResumeHtml(basics.value.summaryHtml)
const safeEducationHtml = (item) => sanitizeResumeHtml(item?.detailHtml)
const safeWorkHtml = (item) => sanitizeResumeHtml(item?.contentHtml)
const safeProjectHtml = (item) => sanitizeResumeHtml(item?.contentHtml)
const safeListItemHtml = (item) => sanitizeResumeHtml(item)
const looksRich = (item) => typeof item === 'string' && /<\/?[a-z][^>]*>/i.test(item)

const templateClass = computed(() => 'tpl-' + findResumeTemplate(props.template).id)
const isReplica = computed(() => findResumeTemplate(props.template).id === REPLICA_TEMPLATE.id)
const showAvatar = computed(() => !!props.avatar)
const showSectionNode = computed(() => isReplica.value && !!props.style?.sectionBadge)
const accentTerms = computed(() => isReplica.value && Array.isArray(props.style?.accentTerms) ? props.style.accentTerms : [])
const sheetClasses = computed(() => [templateClass.value, {
  'has-header-band': isReplica.value && !!props.style?.headerBand,
  'avatar-left': isReplica.value && props.style?.avatarPosition === 'LEFT_TOP',
  'has-section-rail': isReplica.value && !!props.style?.sectionRail
}])

const styleVars = computed(() => {
  if (!isReplica.value || !props.style) return undefined
  const style = props.style
  const vars = {}
  const color = (value, name) => {
    if (typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value)) vars[name] = value
  }
  color(style.accentColor, '--sheet-accent')
  color(style.headingColor, '--sheet-heading')
  color(style.bodyColor, '--sheet-body')
  color(style.metaColor, '--sheet-meta')
  color(style.headerBandColor, '--sheet-band')
  color(style.headerTextColor, '--sheet-band-text')
  if (Number.isFinite(style.marginMm) && style.marginMm > 0) vars['--sheet-pad'] = `${style.marginMm}mm`
  if (Number.isFinite(style.baseFontSizePt) && style.baseFontSizePt > 0) vars['--sheet-font-size'] = `${style.baseFontSizePt}pt`
  if (style.avatarSizeMm > 0) vars['--sheet-avatar-size'] = `${style.avatarSizeMm}mm`
  if (Number.isFinite(style.sectionRailOffsetMm) && style.sectionRailOffsetMm > 0) vars['--sheet-rail-offset'] = `${style.sectionRailOffsetMm}mm`
  vars['--sheet-skills-columns'] = style.skillsColumns === 2 ? 2 : 1
  return vars
})
</script>

<style scoped>
.resume-sheet {
  --sheet-accent: #2563eb;
  width: 210mm;
  min-height: 297mm;
  padding: 14mm 16mm;
  box-sizing: border-box;
  background: #fff;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.7;
  box-shadow: 0 6px 26px rgba(15, 23, 42, 0.14);
}

.sheet-header { padding-bottom: 10px; border-bottom: 2px solid #1f2937; }
.sheet-name-row { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.sheet-name { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: 2px; }
.sheet-label { color: var(--sheet-accent, #2563eb); font-size: 14px; font-weight: 600; }
.sheet-contacts { display: flex; flex-wrap: wrap; gap: 4px 14px; margin-top: 6px; color: #4b5563; font-size: 12px; }
.sheet-section { margin-top: 16px; }
.sheet-title { margin: 0 0 8px; padding-left: 8px; border-left: 3px solid var(--sheet-accent, #2563eb); color: #111827; font-size: 14px; font-weight: 700; letter-spacing: 1px; }
.sheet-title-bar { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.sheet-title-bar .sheet-title { flex: 1; margin: 0; }
.sheet-item + .sheet-item { margin-top: 10px; }
.sheet-item-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.sheet-item-main { font-weight: 600; }
.sheet-item-meta { flex: none; color: #6b7280; font-size: 12px; }
.sheet-paragraph, .sheet-detail { margin: 4px 0 0; color: #374151; white-space: pre-wrap; }
.sheet-bullets { margin: 4px 0 0; padding-left: 18px; }
.sheet-bullets li { margin-top: 2px; }
.sheet-skills { columns: 2; column-gap: 24px; }
.sheet-rich-text { margin-top: 4px; color: #374151; }
.sheet-rich-text :deep(p), .sheet-rich-text :deep(div) { margin: 3px 0; }
.sheet-rich-text :deep(ol), .sheet-rich-text :deep(ul) { margin: 4px 0; padding-left: 18px; }
.sheet-rich-text :deep(li) { margin-top: 2px; }
.sheet-rich-text :deep(a) { color: var(--sheet-accent); text-decoration: underline; }
.sheet-rich-inline :deep(p), .sheet-rich-inline :deep(div) { display: inline; margin: 0; }
.sheet-rich-inline :deep(a) { color: var(--sheet-accent); text-decoration: underline; }
</style>

<style>
@import '../assets/resume-templates.css';

/*
 * 纸张边距一律交给模板自己的 padding（各模板不同，复刻模板还靠它做页眉出血），
 * @page 再留边距会叠成双份：正文比预览窄一圈，页眉的负边距出血也会被裁掉。
 */
@page {
  size: A4;
  margin: 0;
}

@media print {
  body { background: #fff !important; }
  body * { visibility: hidden; }
  .resume-sheet, .resume-sheet * { visibility: visible; }
  *:has(.resume-sheet) {
    position: static !important;
    transform: none !important;
    animation: none !important;
    overflow: visible !important;
  }
  /*
   * 固定成 A4 宽 + clone 断裂盒：跟预览同一套盒模型，版式不随窗口宽度漂移，
   * 分页后每一页也都补回模板的上下内边距，续页文字不会顶到纸边。
   */
  .resume-sheet {
    position: absolute !important;
    left: 0 !important;
    top: 0 !important;
    width: 210mm !important;
    min-height: 0 !important;
    margin: 0 !important;
    box-shadow: none !important;
    -webkit-box-decoration-break: clone !important;
    box-decoration-break: clone !important;
  }
  /* 深色页眉、徽章、强调色都靠背景色，默认的 economy 模式会把它们整片丢掉 */
  .resume-sheet, .resume-sheet * {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
}
</style>
