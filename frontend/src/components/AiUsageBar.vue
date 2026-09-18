<template>
  <div v-if="mocked || hasUsage" class="usage-bar">
    <el-tag v-if="mocked" size="small" type="warning" effect="plain">本地模拟引擎，未消耗 token</el-tag>
    <template v-else>
      <span class="usage-title">本次消耗</span>
      <span class="usage-text">{{ summary }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 后端返回的 usage：{ inputTokens, outputTokens, cachedTokens, reasoningTokens, totalTokens, estimatedCost }
  usage: { type: Object, default: null },
  // 是否由本地模拟引擎产生（不消耗 token）
  mocked: { type: Boolean, default: false }
})

const hasUsage = computed(() => Boolean(props.usage && props.usage.totalTokens))

const count = (value) => Number(value || 0).toLocaleString('zh-CN')

const money = (value) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return null
  return amount >= 0.0001 ? amount.toFixed(4) : amount.toFixed(6)
}

const summary = computed(() => {
  const usage = props.usage || {}
  const parts = [
    `输入 ${count(usage.inputTokens)} tokens`,
    usage.cachedTokens ? `缓存命中 ${count(usage.cachedTokens)}` : null,
    `输出 ${count(usage.outputTokens)} tokens`,
    usage.reasoningTokens ? `思维链 ${count(usage.reasoningTokens)}` : null,
    `共 ${count(usage.totalTokens)} tokens`
  ].filter(Boolean)
  const cost = money(usage.estimatedCost)
  if (cost) parts.push(`预估 ${cost} 元`)
  return parts.join(' · ')
})
</script>

<style scoped>
.usage-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--brand-softer);
  font-size: 12px;
  line-height: 1.6;
}

.usage-title { color: var(--brand); font-weight: 600; }
.usage-text { color: var(--text-secondary); }
</style>
