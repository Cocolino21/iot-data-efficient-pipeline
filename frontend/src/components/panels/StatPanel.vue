<script setup>
import { computed, ref, watch } from 'vue'
import { useDevicesStore } from '@/stores/devices.js'
import { useTimeStore } from '@/stores/time.js'
import { api } from '@/services/api.js'
import { colorForType } from './useChartTheme.js'

const props = defineProps({
  datastreamIds: { type: Array, required: true },
})

const devices = useDevicesStore()
const time = useTimeStore()
const sensors = computed(() => props.datastreamIds.map((id) => devices.allSensors.find((s) => s.id === id)).filter(Boolean))

const current = ref({})  // sensorId -> current value
const previous = ref({}) // sensorId -> previous value

async function loadLatest() {
  for (const s of sensors.value) {
    try {
      const obs = await api.observations(s.id, s.type, Date.now() - 60 * 60_000, Date.now(), 1)
      const last = obs[obs.length - 1]
      const next = last ? last.value : 0
      previous.value[s.id] = current.value[s.id] ?? next
      current.value[s.id] = next
    } catch {
      current.value[s.id] = current.value[s.id] ?? 0
    }
  }
}

watch(() => [props.datastreamIds.join(','), time.refreshTick, devices.list.length], loadLatest, { immediate: true })

function format(s, v) {
  const decimals = (s.type === 'co2' || s.type === 'light') ? 0 : 1
  return Number(v ?? 0).toFixed(decimals)
}
function quality(s, v) { return api.qualityFor(s.type, v ?? 0) }
function color(s) {
  const v = current.value[s.id]
  const q = quality(s, v)
  if (q === 'good') return '#34D399'
  if (q === 'moderate') return '#FBBF24'
  if (q === 'poor') return '#EF4444'
  return colorForType(s.type)
}
function qLabel(s) {
  const q = quality(s, current.value[s.id])
  return q === 'good' ? 'Good' : q === 'moderate' ? 'Moderate' : 'Poor'
}
function trend(s) {
  const cur = current.value[s.id] ?? 0
  const prev = previous.value[s.id] ?? cur
  const diff = cur - prev
  if (Math.abs(diff) < 0.01) return 'flat'
  return diff > 0 ? 'up' : 'down'
}
function diffText(s) {
  const cur = current.value[s.id] ?? 0
  const prev = previous.value[s.id] ?? cur
  const d = cur - prev
  if (!Number.isFinite(d) || prev === 0) return ''
  const sign = d > 0 ? '+' : ''
  return `${sign}${d.toFixed(1)} from last update`
}
</script>

<template>
  <div class="stat-grid" :class="{ single: sensors.length === 1, multi: sensors.length > 1 }">
    <div v-for="s in sensors" :key="s.id" class="stat">
      <div v-if="sensors.length > 1" class="stat-name">{{ s.observed }}</div>
      <div class="stat-value" :style="{ color: color(s) }">{{ format(s, current[s.id]) }}</div>
      <div class="stat-unit" :style="{ color: color(s) }">{{ sensors.length > 1 ? s.symbol : qLabel(s) }}</div>
      <div v-if="sensors.length === 1" class="trend">
        <span class="arrow" :class="trend(s)" :style="{ borderBottomColor: trend(s) === 'up' ? color(s) : 'transparent', borderTopColor: trend(s) === 'down' ? color(s) : 'transparent' }"></span>
        <span class="diff">{{ diffText(s) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-grid { height: 100%; display: flex; align-items: center; justify-content: center; }
.stat-grid.multi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
.stat {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 4px;
  padding: 12px;
}
.stat-grid.multi .stat {
  background: var(--bg-elevated);
  border-radius: 10px;
}
.stat-name { font-size: 11px; color: var(--text-faint); margin-bottom: 6px; }
.stat-value { font-size: 56px; font-weight: 800; line-height: 1; font-family: var(--font-base); }
.stat-grid.multi .stat-value { font-size: 32px; }
.stat-unit { font-size: 14px; font-weight: 500; }
.stat-grid.multi .stat-unit { font-size: 11px; }
.trend { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.arrow {
  display: inline-block;
  width: 0; height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
}
.arrow.up { border-bottom: 8px solid; }
.arrow.down { border-top: 8px solid; }
.arrow.flat { display: none; }
.diff { color: var(--text-faint); font-size: 11px; }
</style>
