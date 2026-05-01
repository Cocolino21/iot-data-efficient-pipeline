<script setup>
import { computed, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GaugeChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useDevicesStore } from '@/stores/devices.js'
import { api } from '@/services/api.js'
import { colorForType } from './useChartTheme.js'

use([CanvasRenderer, GaugeChart, TooltipComponent])

const props = defineProps({
  datastreamIds: { type: Array, required: true },
  min: { type: Number, default: null },
  max: { type: Number, default: null },
  unit: { type: String, default: '' },
})

const devices = useDevicesStore()
const sensors = computed(() => props.datastreamIds.map((id) => devices.allSensors.find((s) => s.id === id)).filter(Boolean))

// Reasonable defaults per sensor type so gauges look right without explicit min/max.
function rangeFor(type) {
  switch (type) {
    case 'temperature': return [0, 40]
    case 'humidity':    return [0, 100]
    case 'co2':         return [0, 2000]
    case 'light':       return [0, 1000]
    case 'energy':      return [0, 6]
    case 'pressure':    return [950, 1050]
    default:            return [0, 100]
  }
}

const liveValues = computed(() => devices.liveValues)
const values = ref({})

watch([sensors, liveValues], () => {
  for (const s of sensors.value) {
    const lv = devices.liveValues[s.id]
    values.value[s.id] = lv ? lv.value : api.latest(s.id, s.type).value
  }
}, { immediate: true, deep: true })

function buildOption(s) {
  const [defaultMin, defaultMax] = rangeFor(s.type)
  const lo = props.min != null ? props.min : defaultMin
  const hi = props.max != null ? props.max : defaultMax
  const v = values.value[s.id] ?? 0
  const color = colorForType(s.type) || '#818CF8'
  const symbol = s.symbol || ''
  const showPercentSign = symbol === '%RH' || symbol === '%'
  const labelText = sensors.value.length > 1 ? s.observed : (showPercentSign ? 'Relative Humidity' : symbol)
  return {
    series: [{
      type: 'gauge',
      startAngle: 220,
      endAngle: -40,
      min: lo,
      max: hi,
      progress: { show: true, width: 12, itemStyle: { color } },
      axisLine: { lineStyle: { width: 12, color: [[1, '#252536']] } },
      pointer: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      anchor: { show: false },
      detail: {
        offsetCenter: [0, '0%'],
        formatter: () => `{value|${formatVal(s.type, v)}${showPercentSign ? '%' : ''}}\n{label|${labelText}}`,
        rich: {
          value: { fontSize: sensors.value.length > 1 ? 22 : 30, fontWeight: 700, color: '#FFFFFF', fontFamily: 'Inter, sans-serif', lineHeight: 28 },
          label: { fontSize: 11, color: '#64748B', fontFamily: 'Inter, sans-serif', lineHeight: 22 },
        },
      },
      data: [{ value: v }],
    }],
  }
}

function formatVal(type, v) {
  const decimals = (type === 'co2' || type === 'light') ? 0 : 1
  return Number(v).toFixed(decimals)
}
</script>

<template>
  <div class="g-wrap" :class="{ multi: sensors.length > 1 }">
    <VChart
      v-for="s in sensors"
      :key="s.id"
      class="gauge"
      :option="buildOption(s)"
      :update-options="{ notMerge: true }"
      autoresize
    />
  </div>
</template>

<style scoped>
.g-wrap { width: 100%; height: 100%; display: flex; align-items: stretch; justify-content: stretch; }
.g-wrap.multi { display: grid; gap: 8px; }
/* layouts for 2-6 gauges */
.g-wrap.multi { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
.gauge { width: 100%; height: 100%; min-height: 180px; }
</style>
