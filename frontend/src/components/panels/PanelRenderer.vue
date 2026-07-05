<script setup>
import { computed } from 'vue'
import BasePanel from './BasePanel.vue'
import LinePanel from './LinePanel.vue'
import BarPanel from './BarPanel.vue'
import GaugePanel from './GaugePanel.vue'
import StatPanel from './StatPanel.vue'
import TablePanel from './TablePanel.vue'
import { useDevicesStore } from '@/stores/devices.js'
import { api } from '@/services/api.js'

const props = defineProps({
  panel: { type: Object, required: true },
  editMode: Boolean,
})
const emit = defineEmits(['edit', 'remove'])

const devices = useDevicesStore()
const firstSensor = computed(() => devices.allSensors.find((s) => s.id === props.panel.datastreamIds[0]))

const quality = computed(() => {
  const s = firstSensor.value
  if (!s) return 'good'
  const lv = devices.liveValues[s.id]
  if (!lv) return 'good'
  return api.qualityFor(s.type, lv.value)
})

const componentMap = {
  line: LinePanel,
  bar: BarPanel,
  gauge: GaugePanel,
  stat: StatPanel,
  table: TablePanel,
}
const cmp = computed(() => componentMap[props.panel.type] || LinePanel)
</script>

<template>
  <BasePanel
    :title="panel.title"
    :subtitle="panel.subtitle"
    :quality="quality"
    :edit-mode="editMode"
    @edit="emit('edit')"
    @remove="emit('remove')"
  >
    <component
      :is="cmp"
      :datastream-ids="panel.datastreamIds"
      :min="panel.min"
      :max="panel.max"
    />
  </BasePanel>
</template>
