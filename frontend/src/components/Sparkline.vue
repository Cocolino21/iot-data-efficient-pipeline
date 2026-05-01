<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] }, // [{ timestamp, value }] OR raw numbers
  color: { type: String, default: '#818CF8' },
  width: { type: Number, default: 140 },
  height: { type: Number, default: 28 },
})

const path = computed(() => {
  const vals = props.points.map((p) => (typeof p === 'number' ? p : p.value))
  if (vals.length < 2) return ''
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const span = max - min || 1
  const stepX = props.width / (vals.length - 1)
  return vals
    .map((v, i) => `${i === 0 ? 'M' : 'L'}${(i * stepX).toFixed(1)},${(props.height - ((v - min) / span) * props.height).toFixed(1)}`)
    .join(' ')
})
</script>

<template>
  <svg :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`">
    <path :d="path" :stroke="color" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>
