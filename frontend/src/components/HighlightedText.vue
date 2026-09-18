<template>
  <template v-for="(part, index) in parts" :key="index">
    <span :class="{ 'resume-highlight': part.highlighted }">{{ part.text }}</span>
  </template>
</template>

<script setup>
import { computed } from 'vue'
import { splitHighlightedText } from '@/utils/resumeHighlight'

const props = defineProps({
  text: { type: String, default: '' },
  terms: { type: Array, default: () => [] }
})

const parts = computed(() => splitHighlightedText(props.text, props.terms))
</script>

<style scoped>
.resume-highlight {
  color: var(--sheet-accent);
  font-weight: 700;
}
</style>
