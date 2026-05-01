<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useDevicesStore } from '@/stores/devices.js'
import { useTimeStore } from '@/stores/time.js'
import { api } from '@/services/api.js'
import { SERIES_COLORS, baseGrid, axisStyle, tooltipStyle, colorForType } from './useChartTheme.js'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const props = defineProps({
  datastreamIds: { type: Array, required: true },
  zoomable: { type: Boolean, default: false },
})

const devices = useDevicesStore()
const time = useTimeStore()
const seriesData = ref([])

const sensors = computed(() => {
  return props.datastreamIds.map((id) => devices.allSensors.find((s) => s.id === id)).filter(Boolean)
})

async function load() {
  const { from, to } = time.range
  const out = []
  for (const s of sensors.value) {
    const points = await api.observations(s.id, s.type, from, to, 200)
    out.push({ sensor: s, points })
  }
  seriesData.value = out
}

watch(() => [props.datastreamIds.join(','), time.refreshTick, devices.list.length], load, { immediate: true })

const live = computed(() => devices.liveValues)
watch(live, () => {
  for (const grp of seriesData.value) {
    const lv = live.value[grp.sensor.id]
    if (lv && grp.points.length) {
      const last = grp.points[grp.points.length - 1]
      if (lv.timestamp - last.timestamp > 30_000) {
        grp.points.push({ timestamp: lv.timestamp, value: lv.value })
        if (grp.points.length > 240) grp.points.shift()
      }
    }
  }
}, { deep: true })

const option = computed(() => {
  const series = seriesData.value.map((g, i) => ({
    name: g.sensor.name,
    type: 'line',
    showSymbol: false,
    smooth: true,
    lineStyle: { width: 2, color: colorForType(g.sensor.type) || SERIES_COLORS[i] },
    itemStyle: { color: colorForType(g.sensor.type) || SERIES_COLORS[i] },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: (colorForType(g.sensor.type) || SERIES_COLORS[i]) + '40' },
          { offset: 1, color: (colorForType(g.sensor.type) || SERIES_COLORS[i]) + '00' },
        ],
      },
    },
    data: g.points.map((p) => [p.timestamp, p.value]),
  }))

  return {
    grid: baseGrid,
    tooltip: tooltipStyle,
    xAxis: { type: 'time', ...axisStyle, splitLine: { show: false } },
    yAxis: { type: 'value', ...axisStyle, splitNumber: 4 },
    series,
    ...(props.zoomable ? { dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 0, backgroundColor: '#1E1E2E', fillerColor: 'rgba(79,70,229,0.2)', borderColor: '#252536', textStyle: { color: '#475569', fontSize: 9 } }] } : {}),
  }
})
</script>

<template>
  <VChart class="chart" :option="option" :update-options="{ notMerge: true }" autoresize />
</template>

<style scoped>
.chart { width: 100%; height: 100%; }
</style>
