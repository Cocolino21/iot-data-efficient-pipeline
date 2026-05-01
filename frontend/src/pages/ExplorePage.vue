<script setup>
import { ref, computed, watch } from 'vue'
import TopBar from '@/components/TopBar.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import LinePanel from '@/components/panels/LinePanel.vue'
import BarPanel from '@/components/panels/BarPanel.vue'
import GaugePanel from '@/components/panels/GaugePanel.vue'
import StatPanel from '@/components/panels/StatPanel.vue'
import TablePanel from '@/components/panels/TablePanel.vue'
import { useDevicesStore } from '@/stores/devices.js'
import { useDashboardStore } from '@/stores/dashboard.js'
import { useTimeStore } from '@/stores/time.js'

const devices = useDevicesStore()
const dashboard = useDashboardStore()
const time = useTimeStore()

const deviceId = ref('')
const selectedSensors = ref([])
const committedSensors = ref([])
const chartType = ref('line')
const ranQuery = ref(false)

watch(() => devices.list.length, (n) => {
  if (n && !deviceId.value) {
    deviceId.value = devices.list[0].id
    const ids = devices.list[0].sensors.filter((s) => s.is_active).slice(0, 3).map((s) => s.id)
    selectedSensors.value = ids
    committedSensors.value = [...ids]
    if (ids.length) ranQuery.value = true
  }
}, { immediate: true })

// Reset selection when device changes; nothing is committed until Run Query.
watch(deviceId, () => {
  selectedSensors.value = []
})

const sensorsForDevice = computed(() => {
  const d = devices.list.find((x) => x.id === deviceId.value)
  return d ? d.sensors : []
})

function toggleSensor(id) {
  const i = selectedSensors.value.indexOf(id)
  if (i >= 0) selectedSensors.value.splice(i, 1)
  else selectedSensors.value.push(id)
}

function runQuery() {
  if (!selectedSensors.value.length) return
  committedSensors.value = [...selectedSensors.value]
  time.refresh()
  ranQuery.value = true
}

const dirty = computed(() => {
  if (selectedSensors.value.length !== committedSensors.value.length) return true
  for (const id of selectedSensors.value) {
    if (!committedSensors.value.includes(id)) return true
  }
  return false
})

const chartTypes = [
  { id: 'line', label: 'Line' },
  { id: 'bar', label: 'Bar' },
  { id: 'gauge', label: 'Gauge' },
  { id: 'stat', label: 'Stat' },
  { id: 'table', label: 'Table' },
]

const cmpMap = { line: LinePanel, bar: BarPanel, gauge: GaugePanel, stat: StatPanel, table: TablePanel }
const cmp = computed(() => cmpMap[chartType.value])

const chartTitle = computed(() => {
  const d = devices.list.find((x) => x.id === deviceId.value)
  return d ? `Sensor Comparison - ${d.name}` : 'Sensor Comparison'
})
const chartSubtitle = computed(() => {
  const sens = devices.allSensors.filter((s) => committedSensors.value.includes(s.id))
  return sens.map((s) => `${s.observed} (${s.symbol})`).join(', ') + ' | ' + time.label
})

const showSavedToast = ref(false)
function saveToDashboard() {
  if (!committedSensors.value.length) return
  const sens = devices.allSensors.find((s) => committedSensors.value.includes(s.id))
  dashboard.addPanel({
    type: chartType.value,
    title: chartTitle.value,
    subtitle: sens ? `${sens.observed} - ${sens.symbol}` : '',
    datastreamIds: [...committedSensors.value],
    w: 8, h: chartType.value === 'table' ? 9 : 8,
  })
  showSavedToast.value = true
  setTimeout(() => (showSavedToast.value = false), 2200)
}
</script>

<template>
  <div>
    <TopBar title="Explore" />

    <div class="page">
      <!-- Query bar -->
      <div class="query">
        <label>
          <span>Device</span>
          <select v-model="deviceId">
            <option v-for="d in devices.list" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </label>
        <label class="ds-field">
          <span>Datastreams</span>
          <div class="chips">
            <button
              v-for="s in sensorsForDevice"
              :key="s.id"
              class="chip"
              :class="{ on: selectedSensors.includes(s.id) }"
              @click="toggleSensor(s.id)"
            >{{ s.observed }} <em>({{ s.symbol }})</em></button>
          </div>
        </label>
        <label class="time-field">
          <span>Time Range</span>
          <TimeRangePicker />
        </label>
        <button class="btn btn-primary run" :class="{ pulse: dirty }" @click="runQuery">Run Query</button>
      </div>

      <!-- Type switcher -->
      <div class="types">
        <button
          v-for="t in chartTypes"
          :key="t.id"
          class="tswitch"
          :class="{ on: chartType === t.id }"
          @click="chartType = t.id"
          :title="t.label"
        >{{ t.label }}</button>
      </div>

      <!-- Chart area -->
      <div class="chart-card">
        <div class="chart-head">
          <div>
            <div class="ct">{{ chartTitle }}</div>
            <div class="cs">{{ chartSubtitle }}</div>
          </div>
        </div>
        <div class="chart-body">
          <component
            v-if="ranQuery && committedSensors.length"
            :is="cmp"
            :datastream-ids="committedSensors"
            :zoomable="true"
          />
          <div v-else class="empty-chart">
            Select a device + datastreams, then click Run Query.
          </div>
        </div>
      </div>

      <!-- Action bar -->
      <div class="action-bar">
        <button class="btn btn-primary" :disabled="!selectedSensors.length" @click="saveToDashboard">
          Save to Dashboard
        </button>
      </div>

      <transition name="toast">
        <div v-if="showSavedToast" class="toast">Saved to dashboard ✓</div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.query {
  background: var(--bg-panel);
  border-radius: 12px;
  padding: 14px 18px;
  display: grid;
  grid-template-columns: 220px 1fr 200px auto;
  gap: 18px;
  align-items: end;
}
label { display: flex; flex-direction: column; gap: 6px; }
label > span { font-size: 10px; color: var(--text-faint); font-weight: 500; letter-spacing: 0.5px; text-transform: uppercase; }
select {
  height: 32px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  padding: 0 12px;
  color: var(--text-secondary);
  font-size: 12px;
}
.chips { display: flex; flex-wrap: wrap; gap: 6px; min-height: 32px; align-items: center; }
.chip {
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  padding: 5px 10px;
  font-size: 11px;
  cursor: pointer;
}
.chip em { color: var(--text-faint); font-style: normal; }
.chip.on { background: rgba(79,70,229,0.2); color: var(--indigo-light); border-color: var(--indigo); }
.run { height: 36px; }
.run.pulse { box-shadow: 0 0 0 0 rgba(79,70,229,0.6); animation: pulse 1.6s infinite; }
@keyframes pulse {
  0%   { box-shadow: 0 0 0 0 rgba(129,140,248,0.6); }
  70%  { box-shadow: 0 0 0 10px rgba(129,140,248,0); }
  100% { box-shadow: 0 0 0 0 rgba(129,140,248,0); }
}

.types {
  display: flex; gap: 6px;
  margin-top: 14px;
}
.tswitch {
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-faint);
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
}
.tswitch:hover { color: var(--text-secondary); background: var(--bg-elevated); }
.tswitch.on { background: rgba(79,70,229,0.2); color: var(--indigo-light); border-color: rgba(79,70,229,0.4); }

.chart-card {
  background: var(--bg-panel);
  border-radius: 12px;
  margin-top: 14px;
  padding: 24px 28px 18px;
  height: 580px;
  display: flex; flex-direction: column;
  box-shadow: var(--shadow-panel);
}
.chart-head { display: flex; justify-content: space-between; }
.ct { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.cs { font-size: 12px; color: var(--text-faint); margin-top: 4px; }
.chart-body { flex: 1; min-height: 0; margin-top: 16px; }
.empty-chart {
  height: 100%; display: flex; align-items: center; justify-content: center;
  color: var(--text-faint); font-size: 13px;
}

.action-bar {
  background: var(--bg-panel);
  border-radius: 12px;
  margin-top: 14px;
  padding: 12px 18px;
  display: flex; justify-content: flex-end;
}

button[disabled] { opacity: 0.5; cursor: not-allowed; }

.toast {
  position: fixed;
  bottom: 32px; right: 32px;
  padding: 12px 22px;
  background: var(--bg-elevated);
  border: 1px solid var(--green);
  color: var(--green);
  border-radius: 10px;
  font-size: 13px;
  box-shadow: var(--shadow-card);
}
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }
.toast-enter-active, .toast-leave-active { transition: 0.2s; }
</style>
