<template>
  <section class="resume-editor" aria-label="简历内容编辑器">
    <div class="re-intro">
      <div>
        <strong>按模块填写内容</strong>
        <p>上方简历会实时排版；不需要的内容可以留空或删除。</p>
      </div>
      <span class="re-progress">已填写 {{ completedCount }} / {{ MODULES.length }} 个模块</span>
    </div>

    <nav class="re-tabs" aria-label="简历模块">
      <button v-for="item in MODULES" :key="item.key" type="button" class="re-tab"
              :class="{ 'is-active': active === item.key }" @click="select(item.key)">
        {{ item.label }}
        <span v-if="countOf(item.key)" class="re-count">{{ countOf(item.key) }}</span>
      </button>
    </nav>

    <div v-if="active === 'basics'" class="re-panel">
      <div class="re-grid">
        <label class="re-field"><span class="re-label">姓名</span><el-input v-model="basics.name" :disabled="!editable" placeholder="例如：张三" /></label>
        <label class="re-field"><span class="re-label">求职意向</span><el-input v-model="basics.label" :disabled="!editable" placeholder="例如：Java 后端开发" /></label>
        <label class="re-field"><span class="re-label">联系电话</span><el-input v-model="basics.phone" :disabled="!editable" placeholder="例如：13800000000" /></label>
        <label class="re-field"><span class="re-label">联系邮箱</span><el-input v-model="basics.email" :disabled="!editable" placeholder="例如：zhangsan@example.com" /></label>
        <label class="re-field"><span class="re-label">所在城市</span><el-input v-model="basics.city" :disabled="!editable" placeholder="例如：广州" /></label>
        <label class="re-field"><span class="re-label">工作年限</span><el-input v-model="basics.workYears" :disabled="!editable" placeholder="例如：3 年" /></label>
      </div>
    </div>

    <div v-else-if="active === 'summary'" class="re-panel">
      <label class="re-field re-field-wide">
        <span class="re-label">自我评价</span>
        <ResumeRichTextEditor v-model="basics.summaryHtml" :disabled="!editable" />
      </label>
    </div>

    <div v-else class="re-panel re-split">
      <aside class="re-list">
        <button v-for="(item, index) in items" :key="index" type="button" class="re-list-item"
                :class="{ 'is-active': index === currentIndex }" @click="currentIndex = index">
          <strong>{{ primaryText(item) }}</strong>
          <span v-if="secondaryText(item)">{{ secondaryText(item) }}</span>
        </button>
        <button type="button" class="re-list-add" :disabled="!editable" @click="addEntry">
          + 新增{{ activeModule.entryLabel }}
        </button>
      </aside>

      <div class="re-form">
        <el-empty v-if="!items.length" :image-size="72" :description="`还没有内容，点左边新增${activeModule.entryLabel}`" />
        <template v-else>
          <div class="re-form-head">
            <span class="re-form-title">{{ activeModule.label }} {{ currentIndex + 1 }}</span>
            <span class="re-form-actions">
              <el-button link :disabled="!editable || currentIndex === 0" @click="moveEntry(-1)">上移</el-button>
              <el-button link :disabled="!editable || currentIndex === items.length - 1" @click="moveEntry(1)">下移</el-button>
              <el-button link type="danger" :disabled="!editable" @click="removeEntry">删除这条</el-button>
            </span>
          </div>

          <div v-if="active === 'education'" class="re-grid">
            <label class="re-field"><span class="re-label">学校名称</span><el-input v-model="current.school" :disabled="!editable" placeholder="例如：广东科技学院" /></label>
            <label class="re-field"><span class="re-label">所学专业</span><el-input v-model="current.major" :disabled="!editable" placeholder="例如：软件工程" /></label>
            <label class="re-field"><span class="re-label">学历</span><el-select v-model="current.degree" :disabled="!editable" clearable placeholder="请选择学历" style="width: 100%"><el-option v-for="degree in DEGREES" :key="degree" :label="degree" :value="degree" /></el-select></label>
            <label class="re-field"><span class="re-label">起止时间</span><el-input v-model="current.period" :disabled="!editable" placeholder="例如：2022.09 - 2026.07" /></label>
            <div class="re-field re-field-wide"><span class="re-label">成绩 / 主修课程</span><ResumeRichTextEditor v-model="current.detailHtml" :disabled="!editable" /></div>
          </div>

          <div v-else-if="active === 'work'" class="re-grid">
            <label class="re-field"><span class="re-label">公司名称</span><el-input v-model="current.company" :disabled="!editable" placeholder="例如：某某科技有限公司" /></label>
            <label class="re-field"><span class="re-label">职位名称</span><el-input v-model="current.position" :disabled="!editable" placeholder="例如：软件测试实习生" /></label>
            <label class="re-field re-field-wide"><span class="re-label">起止时间</span><el-input v-model="current.period" :disabled="!editable" placeholder="例如：2025.09 - 至今" /></label>
            <div class="re-field re-field-wide"><span class="re-label">工作内容</span><ResumeRichTextEditor v-model="current.contentHtml" :disabled="!editable" /></div>
          </div>

          <div v-else-if="active === 'projects'" class="re-grid">
            <label class="re-field"><span class="re-label">项目名称</span><el-input v-model="current.name" :disabled="!editable" placeholder="例如：电商后台管理系统" /></label>
            <label class="re-field"><span class="re-label">参与角色</span><el-input v-model="current.role" :disabled="!editable" placeholder="例如：软件测试实习生" /></label>
            <div class="re-field re-field-wide">
              <span class="re-label">项目时间</span>
              <div class="re-project-dates">
                <label><span>开始时间</span><el-date-picker v-model="current.startDate" type="month" value-format="YYYY-MM" format="YYYY-MM" :disabled="!editable" placeholder="选择月份" @change="syncProjectPeriod" /></label>
                <span class="re-date-separator">—</span>
                <label><span>结束时间</span><el-date-picker v-model="current.endDate" type="month" value-format="YYYY-MM" format="YYYY-MM" :disabled="!editable || current.present" placeholder="选择月份" @change="syncProjectPeriod" /></label>
                <el-checkbox v-model="current.present" :disabled="!editable" @change="syncProjectPeriod">至今</el-checkbox>
              </div>
            </div>
            <div class="re-field re-field-wide">
              <span class="re-label">项目内容</span>
              <ResumeRichTextEditor v-model="current.contentHtml" :disabled="!editable" />
            </div>
          </div>

          <div v-else-if="active === 'skills' || active === 'honors'" class="re-grid">
            <div class="re-field re-field-wide">
              <span class="re-label">{{ activeModule.entryLabel }}</span>
              <ResumeRichTextEditor v-model="items[currentIndex]" :disabled="!editable" />
            </div>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ResumeRichTextEditor from './ResumeRichTextEditor.vue'
import { htmlToPlainText, projectToHtml, textToHtml } from '@/utils/resumeRichText'

const MODULES = [
  { key: 'basics', label: '基本信息' },
  { key: 'education', label: '教育背景', entryLabel: '教育经历', itemPlaceholder: '' },
  { key: 'work', label: '工作经历', entryLabel: '工作经历', itemPlaceholder: '' },
  { key: 'projects', label: '项目经验', entryLabel: '项目经历', itemPlaceholder: '' },
  { key: 'skills', label: '技能特长', entryLabel: '技能', itemPlaceholder: '例如：熟悉 Java、Spring Boot、MySQL，掌握 Redis 与消息队列' },
  { key: 'honors', label: '荣誉证书', entryLabel: '荣誉或证书', itemPlaceholder: '例如：大学英语六级（CET-6）｜2025 年国家奖学金' },
  { key: 'summary', label: '自我评价' }
]

const DEGREES = ['博士后', '博士', '硕士', '研究生', '本科', '学士', '大专', '专科', '中专', '高中']

const props = defineProps({
  structure: { type: Object, required: true },
  editable: { type: Boolean, default: true }
})

const active = ref('basics')
const currentIndex = ref(0)
const basics = computed(() => props.structure.basics || {})
const activeModule = computed(() => MODULES.find((item) => item.key === active.value) || MODULES[0])
const items = computed(() => {
  const value = props.structure[active.value]
  return Array.isArray(value) ? value : []
})
const current = computed(() => items.value[currentIndex.value])
const completedCount = computed(() => MODULES.filter((item) => countOf(item.key) > 0).length)

function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function countOf(key) {
  if (key === 'basics') {
    return ['name', 'label', 'phone', 'email', 'city', 'workYears']
      .filter((field) => hasText(basics.value[field])).length
  }
  if (key === 'summary') {
    return hasText(basics.value.summary) ? 1 : 0
  }
  const value = props.structure[key]
  return Array.isArray(value) ? value.length : 0
}

function select(key) {
  active.value = key
  currentIndex.value = 0
}

function newEntry(key) {
  if (key === 'education') return { school: '', major: '', degree: '', period: '', detail: '' }
  if (key === 'work') return { company: '', position: '', period: '', bullets: [] }
  if (key === 'projects') return { name: '', role: '', period: '', startDate: '', endDate: '', present: false, contentHtml: '', summary: '', bullets: [] }
  return ''
}

function addEntry() {
  if (!props.editable || !Array.isArray(props.structure[active.value])) return
  props.structure[active.value].push(newEntry(active.value))
  currentIndex.value = props.structure[active.value].length - 1
}

function removeEntry() {
  if (!props.editable || !items.value.length) return
  items.value.splice(currentIndex.value, 1)
  currentIndex.value = Math.min(currentIndex.value, Math.max(0, items.value.length - 1))
}

function moveEntry(delta) {
  if (!props.editable) return
  const from = currentIndex.value
  const to = from + delta
  if (to < 0 || to >= items.value.length) return
  const [entry] = items.value.splice(from, 1)
  items.value.splice(to, 0, entry)
  currentIndex.value = to
}

function clip(value, fallback) {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) return fallback
  return text.length > 28 ? `${text.slice(0, 28)}…` : text
}

function primaryText(item) {
  if (typeof item === 'string') return clip(item, `未填写${activeModule.value.entryLabel}`)
  if (active.value === 'education') return clip(item.school, '未填写学校')
  if (active.value === 'work') return clip(item.company, '未填写公司')
  if (active.value === 'projects') return clip(item.name, '未填写项目名称')
  return '未填写'
}

function secondaryText(item) {
  if (!item || typeof item === 'string') return ''
  if (active.value === 'education') return [item.major, item.degree].filter(hasText).join(' · ')
  if (active.value === 'work') return item.position || ''
  if (active.value === 'projects') return item.role || ''
  return ''
}

function normalizeMonth(year, month) {
  return `${year}-${String(month).padStart(2, '0')}`
}

/** 兼容旧的 period/summary/bullets，打开项目时迁移到截图里的新表单字段。 */
function ensureProjectEntry(item) {
  if (!item || typeof item !== 'object') return
  if (!Object.prototype.hasOwnProperty.call(item, 'contentHtml')) {
    item.contentHtml = projectToHtml(item)
  }
  if (!Object.prototype.hasOwnProperty.call(item, 'startDate')) {
    const dates = String(item.period || '').match(/(\d{4})[.\-/](\d{1,2})(?:\D+?(\d{4})[.\-/](\d{1,2}))?/)
    item.startDate = dates ? normalizeMonth(dates[1], dates[2]) : ''
    item.endDate = dates?.[3] ? normalizeMonth(dates[3], dates[4]) : ''
    item.present = /至今|present/i.test(String(item.period || ''))
  }
}

function ensureActiveRichContent() {
  if (active.value === 'summary') {
    if (!Object.prototype.hasOwnProperty.call(basics.value, 'summaryHtml')) {
      basics.value.summaryHtml = textToHtml(basics.value.summary)
    }
    return
  }
  const item = current.value
  if (!item || typeof item !== 'object') return
  if (active.value === 'education' && !Object.prototype.hasOwnProperty.call(item, 'detailHtml')) {
    item.detailHtml = textToHtml(item.detail)
  }
  if (active.value === 'work' && !Object.prototype.hasOwnProperty.call(item, 'contentHtml')) {
    item.contentHtml = projectToHtml(item)
  }
  if (active.value === 'projects') ensureProjectEntry(item)
}

function syncProjectPeriod() {
  const item = current.value
  if (active.value !== 'projects' || !item) return
  const end = item.present ? '至今' : item.endDate
  item.period = [item.startDate, end].filter(Boolean).join(' - ')
}

watch(() => items.value.length, (length) => {
  if (currentIndex.value >= length) currentIndex.value = Math.max(0, length - 1)
})

watch([active, currentIndex], ensureActiveRichContent, { immediate: true })
watch(() => basics.value.summaryHtml, (html) => {
  if (typeof html === 'string') basics.value.summary = htmlToPlainText(html)
})
</script>

<style scoped>
.resume-editor {
  margin-top: 14px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.re-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--border-light);
}

.re-intro strong { color: var(--text-primary); font-size: 15px; }
.re-intro p { margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; }
.re-progress { flex: none; color: var(--brand); font-size: 12px; font-weight: 600; }

.re-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 12px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border-light);
  background: #faf9f6;
}

.re-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding: 8px 11px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: color .18s ease, border-color .18s ease, background .18s ease;
}

.re-tab:hover { color: var(--text-primary); background: #fff; }
.re-tab.is-active { border-color: var(--brand-soft); background: #fff; color: var(--brand-deep); font-weight: 600; }
.re-count { display: grid; place-items: center; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: var(--brand-soft); color: var(--brand-deep); font-size: 10px; }

.re-panel { min-height: 250px; padding: 20px; }
.re-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.re-field { display: flex; min-width: 0; flex-direction: column; gap: 7px; }
.re-field-wide { grid-column: 1 / -1; }
.re-label { color: var(--text-regular); font-size: 12px; font-weight: 600; }

.re-split { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 0; padding: 0; }
.re-list { min-height: 330px; padding: 12px; border-right: 1px solid var(--border-light); background: #faf9f6; }
.re-list-item { display: flex; width: 100%; flex-direction: column; gap: 4px; margin-bottom: 6px; padding: 10px 11px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--text-regular); text-align: left; cursor: pointer; }
.re-list-item:hover { background: #fff; }
.re-list-item.is-active { border-color: var(--brand-soft); background: #fff; box-shadow: var(--shadow-xs); }
.re-list-item strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.re-list-item span { overflow: hidden; color: var(--text-secondary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.re-list-add { width: 100%; margin-top: 6px; padding: 9px; border: 1px dashed #cfc6b3; border-radius: 8px; background: transparent; color: var(--brand-deep); font: inherit; font-size: 12px; cursor: pointer; }
.re-list-add:hover:not(:disabled) { border-color: var(--brand); background: var(--brand-softer); }
.re-list-add:disabled { cursor: not-allowed; opacity: .5; }

.re-form { min-width: 0; padding: 18px 20px 22px; }
.re-form-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 18px; padding-bottom: 12px; border-bottom: 1px solid var(--border-light); }
.re-form-title { font-size: 14px; font-weight: 650; }
.re-form-actions { display: flex; align-items: center; }
.re-form-actions :deep(.el-button + .el-button) { margin-left: 8px; }
.re-bullets { display: grid; gap: 9px; }
.re-bullet { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: start; }
.re-project-dates { display: flex; align-items: flex-end; gap: 9px; flex-wrap: wrap; }
.re-project-dates > label { display: flex; flex-direction: column; gap: 5px; color: var(--text-secondary); font-size: 11px; }
.re-project-dates :deep(.el-date-editor) { width: 150px; }
.re-date-separator { padding-bottom: 8px; color: var(--text-placeholder); }
.re-project-dates :deep(.el-checkbox) { margin-bottom: 5px; }

@media (max-width: 820px) {
  .re-intro { align-items: flex-start; flex-direction: column; }
  .re-grid { grid-template-columns: 1fr; }
  .re-field-wide { grid-column: auto; }
  .re-split { grid-template-columns: 1fr; }
  .re-list { display: flex; min-height: auto; gap: 6px; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--border-light); }
  .re-list-item, .re-list-add { flex: 0 0 170px; margin: 0; }
  .re-project-dates { align-items: flex-start; flex-direction: column; }
  .re-date-separator { display: none; }
}
</style>
