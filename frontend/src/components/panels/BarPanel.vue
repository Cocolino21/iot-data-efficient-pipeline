<script setup>
import { computed, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useDevicesStore } from '@/stores/devices.js'
import { useTimeStore } from '@/stores/time.js'
import { api } from '@/services/api.js'
import { baseGrid, axisStyle, tooltipStyle, colorForType } from './useChartTheme.js'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const props = defineProps({ datastreamIds: { type: Array, required: true } })

const devices = useDevicesStore()
const time = useTimeStore()
const seriesData = ref([])

const sensors = computed(() => props.datastreamIds.map((id) => devices.allSensors.find((s) => s.id === id)).filter(Boolean))

async function load() {
  const out = []
  const { from, to } = time.range
  for (const s of sensors.value) {
    const points = await api.observations(s.id, s.type, from, to, 30)
    out.push({ sensor: s, points })
  }
  seriesData.value = out
}

watch(() => [props.datastreamIds.join(','), time.refreshTick, devices.list.length], load, { immediate: true })

const option = computed(() => {
  const multi = seriesData.value.length > 1
  return {
    grid: baseGrid,
    tooltip: { ...tooltipStyle, trigger: 'axis' },
    legend: multi ? {
      top: 0, right: 8,
      textStyle: { color: '#CBD5E1', fontSize: 11, fontFamily: 'Inter, sans-serif' },
      itemWidth: 10, itemHeight: 10,
      icon: 'roundRect',
    } : undefined,
    xAxis: { type: 'time', ...axisStyle, splitLine: { show: false } },
    yAxis: { type: 'value', ...axisStyle, splitNumber: 4 },
    series: seriesData.value.map((g) => ({
      type: 'bar',
      name: `${g.sensor.observed} (${g.sensor.symbol})`,
      barCategoryGap: '30%',
      data: g.points.map((p) => [p.timestamp, p.value]),
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: colorForType(g.sensor.type),
        opacity: 0.85,
      },
    })),
  }
})
</script>

<template>
  <VChart class="chart" :option="option" :update-options="{ notMerge: true }" autoresize />
</template>

<style scoped>
.chart { width: 100%; height: 100%; }
</style>
