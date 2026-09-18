<template>
  <div class="rrt" :class="{ 'is-disabled': disabled }">
    <div class="rrt-toolbar" role="toolbar" aria-label="正文格式工具栏">
      <button type="button" title="撤销" aria-label="撤销" :disabled="disabled" @mousedown.prevent="run('undo')">↶</button>
      <button type="button" title="重做" aria-label="重做" :disabled="disabled" @mousedown.prevent="run('redo')">↷</button>
      <select title="字体大小" aria-label="字体大小" :disabled="disabled" @change="setFontSize">
        <option value="3">字体大小</option>
        <option value="2">小</option>
        <option value="3">正文</option>
        <option value="4">大</option>
        <option value="5">标题</option>
      </select>
      <span class="rrt-color-wrap">
        <button type="button" class="rrt-color" title="文字颜色" aria-label="文字颜色" :disabled="disabled"
                :style="{ '--rrt-active-color': activeColor }" @mousedown.prevent="togglePalette">A</button>
        <span v-if="paletteOpen" class="rrt-palette" role="grid" aria-label="文字颜色色板">
          <button v-for="color in COLOR_PALETTE" :key="color" type="button" role="gridcell"
                  :title="color" :aria-label="`使用颜色 ${color}`" :style="{ backgroundColor: color }"
                  @mousedown.prevent="applyColor(color)"></button>
        </span>
      </span>
      <span class="rrt-divider"></span>
      <button type="button" class="is-bold" title="粗体" aria-label="粗体" :disabled="disabled" @mousedown.prevent="run('bold')">B</button>
      <button type="button" class="is-italic" title="斜体" aria-label="斜体" :disabled="disabled" @mousedown.prevent="run('italic')">I</button>
      <button type="button" class="is-underline" title="下划线" aria-label="下划线" :disabled="disabled" @mousedown.prevent="run('underline')">U</button>
      <button type="button" title="有序列表" aria-label="有序列表" :disabled="disabled" @mousedown.prevent="run('insertOrderedList')">1.</button>
      <button type="button" title="无序列表" aria-label="无序列表" :disabled="disabled" @mousedown.prevent="run('insertUnorderedList')">•</button>
      <button type="button" title="左对齐" aria-label="左对齐" :disabled="disabled" @mousedown.prevent="run('justifyLeft')">≡</button>
      <button type="button" title="居中" aria-label="居中" :disabled="disabled" @mousedown.prevent="run('justifyCenter')">≣</button>
      <button type="button" title="右对齐" aria-label="右对齐" :disabled="disabled" @mousedown.prevent="run('justifyRight')">≡</button>
      <button type="button" title="插入图片" aria-label="插入图片" :disabled="disabled" @mousedown.prevent="insertImage">▧</button>
      <button type="button" title="插入链接" aria-label="插入链接" :disabled="disabled" @mousedown.prevent="createLink">🔗</button>
      <button type="button" title="清除格式" aria-label="清除格式" :disabled="disabled" @mousedown.prevent="run('removeFormat')">Tₓ</button>
      <span class="rrt-hint">设置格式前，请先选中文字</span>
    </div>
    <div ref="editor" class="rrt-content" :contenteditable="!disabled" role="textbox" aria-multiline="true"
         data-placeholder="输入项目架构、项目描述和工作内容；可使用编号组织成果"
         @input="onInput" @mouseup="rememberSelection" @keyup="rememberSelection" @blur="onBlur"></div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { sanitizeResumeHtml } from '@/utils/resumeRichText'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
const editor = ref(null)
const paletteOpen = ref(false)
const activeColor = ref('#e11d48')
const savedRange = ref(null)

const COLOR_PALETTE = [
  '#64748b', '#94a3b8', '#ef4444', '#f97316', '#eab308', '#22c55e', '#14b8a6', '#3b82f6', '#6366f1', '#a855f7',
  '#475569', '#78716c', '#dc2626', '#ea580c', '#ca8a04', '#16a34a', '#0d9488', '#2563eb', '#4f46e5', '#9333ea',
  '#1f2937', '#292524', '#991b1b', '#9a3412', '#854d0e', '#166534', '#115e59', '#1e3a8a', '#312e81', '#581c87',
  '#111827', '#000000', '#7f1d1d', '#7c2d12', '#713f12', '#14532d', '#134e4a', '#172554', '#1e1b4b', '#3b0764'
]

function renderValue(value) {
  if (!editor.value) return
  const safe = sanitizeResumeHtml(value)
  if (editor.value.innerHTML !== safe) editor.value.innerHTML = safe
}

function sync() {
  if (!editor.value) return
  const safe = sanitizeResumeHtml(editor.value.innerHTML)
  if (editor.value.innerHTML !== safe) editor.value.innerHTML = safe
  emit('update:modelValue', safe === '<br>' ? '' : safe)
}

function rememberSelection() {
  const selection = window.getSelection()
  if (!editor.value || !selection?.rangeCount) return
  const range = selection.getRangeAt(0)
  if (editor.value.contains(range.commonAncestorContainer)) savedRange.value = range.cloneRange()
}

function restoreSelection() {
  if (!savedRange.value) return
  try {
    const selection = window.getSelection()
    selection.removeAllRanges()
    selection.addRange(savedRange.value)
  } catch {
    savedRange.value = null
  }
}

function onInput() {
  rememberSelection()
  sync()
}

function onBlur() {
  rememberSelection()
  sync()
}

function run(command, value = null) {
  if (props.disabled || !editor.value) return
  editor.value.focus()
  restoreSelection()
  if (command === 'foreColor') document.execCommand('styleWithCSS', false, false)
  document.execCommand(command, false, value)
  rememberSelection()
  sync()
}

function setFontSize(event) {
  run('fontSize', event.target.value)
  event.target.value = '3'
}

function togglePalette() {
  rememberSelection()
  paletteOpen.value = !paletteOpen.value
}

function applyColor(color) {
  activeColor.value = color
  run('foreColor', color)
  paletteOpen.value = false
}

function insertImage() {
  if (props.disabled) return
  const value = window.prompt('输入 HTTPS 图片地址')
  if (!value || !/^https:\/\//i.test(value)) return
  run('insertImage', value)
}

function createLink() {
  if (props.disabled) return
  const value = window.prompt('输入链接地址')
  if (!value) return
  const href = /^(https?:\/\/|mailto:)/i.test(value) ? value : `https://${value}`
  run('createLink', href)
}

onMounted(() => renderValue(props.modelValue))
watch(() => props.modelValue, (value) => {
  if (document.activeElement !== editor.value) renderValue(value)
})
</script>

<style scoped>
.rrt { overflow: hidden; border: 1px solid var(--border); border-radius: 10px; background: #fff; transition: border-color .18s ease, box-shadow .18s ease; }
.rrt:focus-within { border-color: var(--brand); box-shadow: 0 0 0 2px var(--brand-softer); }
.rrt-toolbar { display: flex; align-items: center; gap: 3px; min-height: 38px; padding: 5px 8px; border-bottom: 1px solid var(--border-light); background: #fbfaf8; }
.rrt-toolbar button, .rrt-toolbar select { height: 27px; min-width: 28px; border: 0; border-radius: 5px; background: transparent; color: var(--text-regular); font: inherit; font-size: 12px; cursor: pointer; }
.rrt-toolbar button:hover:not(:disabled), .rrt-toolbar select:hover:not(:disabled) { background: var(--brand-soft); color: var(--brand-deep); }
.rrt-toolbar select { width: 78px; padding: 0 4px; }
.rrt-toolbar button:disabled, .rrt-toolbar select:disabled { cursor: not-allowed; opacity: .45; }
.rrt-divider { width: 1px; height: 18px; margin: 0 3px; background: var(--border); }
.rrt-color-wrap { position: relative; display: inline-flex; }
.rrt-color { position: relative; display: grid; width: 28px; place-items: center; font-weight: 700 !important; }
.rrt-color::after { position: absolute; right: 6px; bottom: 3px; left: 6px; height: 2px; background: var(--rrt-active-color); content: ''; }
.rrt-palette { position: absolute; z-index: 20; top: 31px; left: -2px; display: grid; grid-template-columns: repeat(10, 16px); gap: 3px; width: max-content; padding: 7px; border: 1px solid var(--border); border-radius: 8px; background: #fff; box-shadow: 0 10px 24px rgba(15, 23, 42, .18); }
.rrt-palette button { width: 16px; min-width: 16px; height: 16px; padding: 0; border: 1px solid rgba(15, 23, 42, .08); border-radius: 2px; }
.rrt-palette button:hover { outline: 2px solid var(--brand); outline-offset: 1px; }
.rrt-hint { margin-left: auto; color: var(--text-placeholder); font-size: 11px; }
.is-bold { font-weight: 800 !important; }
.is-italic { font-style: italic !important; }
.is-underline { text-decoration: underline; }
.rrt-content { min-height: 210px; max-height: 360px; overflow-y: auto; padding: 12px 14px; color: var(--text-primary); font-size: 13px; line-height: 1.7; outline: none; }
.rrt-content:empty::before { color: var(--text-placeholder); content: attr(data-placeholder); pointer-events: none; }
.rrt-content :deep(p) { margin: 0 0 8px; }
.rrt-content :deep(ol), .rrt-content :deep(ul) { margin: 4px 0; padding-left: 22px; }
.rrt-content :deep(img) { display: block; max-width: 100%; max-height: 240px; margin: 8px auto; object-fit: contain; }
.is-disabled { background: var(--el-disabled-bg-color); }
@media (max-width: 700px) { .rrt-toolbar { flex-wrap: wrap; } .rrt-hint { width: 100%; margin-left: 4px; } }
</style>
