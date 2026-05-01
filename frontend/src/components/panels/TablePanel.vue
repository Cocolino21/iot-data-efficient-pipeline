<script setup>
import { computed, ref, watch } from 'vue'
import { useDevicesStore } from '@/stores/devices.js'
import { useTimeStore } from '@/stores/time.js'
import { api } from '@/services/api.js'

const props = defineProps({ datastreamIds: { type: Array, required: true } })

const devices = useDevicesStore()
const time = useTimeStore()
const rows = ref([])

const sensors = computed(() => props.datastreamIds.map((id) => devices.allSensors.find((s) => s.id === id)).filter(Boolean))

async function load() {
  const out = []
  for (const s of sensors.value) {
    const obs = await api.observations(s.id, s.type, Date.now() - 60_000, Date.now(), 1)
    const last = obs[obs.length - 1]
    if (last) {
      out.push({
        ts: last.timestamp,
        sensor: s.observed,
        value: last.value,
        unit: s.symbol,
        quality: api.qualityFor(s.type, last.value),
      })
    }
  }
  rows.value = out.sort((a, b) => b.ts - a.ts)
}

watch(() => [props.datastreamIds.join(','), time.refreshTick, devices.list.length], load, { immediate: true })

watch(() => devices.liveValues, () => {
  load()
}, { deep: true })

function fmtTs(ts) {
  return new Date(ts).toISOString().replace('T', ' ').slice(0, 19)
}
function qColor(q) {
  return q === 'good' ? 'var(--green)' : q === 'moderate' ? 'var(--yellow)' : 'var(--red)'
}
function qLabel(q) {
  return q === 'good' ? 'Good' : q === 'moderate' ? 'Moderate' : 'Poor'
}
</script>

<template>
  <div class="tp">
    <div class="tp-head">
      <div class="th">Timestamp</div>
      <div class="th">Sensor</div>
      <div class="th">Value</div>
      <div class="th">Unit</div>
      <div class="th">Quality</div>
    </div>
    <div class="tp-rows">
      <div v-for="(r, i) in rows" :key="i" class="tr">
        <div class="td">{{ fmtTs(r.ts) }}</div>
        <div class="td">{{ r.sensor }}</div>
        <div class="td">{{ r.value }}</div>
        <div class="td">{{ r.unit }}</div>
        <div class="td"><span class="dot" :style="{ background: qColor(r.quality) }"></span><span :style="{ color: qColor(r.quality) }">{{ qLabel(r.quality) }}</span></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tp { height: 100%; display: flex; flex-direction: column; }
.tp-head, .tr {
  display: grid;
  grid-template-columns: 1.4fr 1fr 0.7fr 0.5fr 0.8fr;
  gap: 12px;
}
.tp-head {
  background: var(--bg-elevated);
  border-radius: 6px;
  padding: 9px 14px;
}
.th { color: var(--text-muted); font-size: 11px; font-weight: 600; }
.tp-rows { flex: 1; overflow: auto; padding: 0 4px; }
.tr {
  padding: 11px 14px;
  border-bottom: 1px solid var(--border);
}
.tr:last-child { border-bottom: none; }
.td { color: var(--text-secondary); font-size: 12px; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
</style>
