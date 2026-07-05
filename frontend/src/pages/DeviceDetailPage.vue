<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import TopBar from '@/components/TopBar.vue'
import Sparkline from '@/components/Sparkline.vue'
import AddSensorModal from '@/components/AddSensorModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useDevicesStore } from '@/stores/devices.js'
import { api } from '@/services/api.js'
import { colorForType } from '@/components/panels/useChartTheme.js'

const route = useRoute()
const router = useRouter()
const devices = useDevicesStore()

const device = computed(() => devices.byId(route.params.id))
const showAddSensor = ref(false)
const pendingDelete = ref(null)
const sparklines = ref({}) // sensorId -> [points]

watch(device, async (d) => {
  if (!d) return
  for (const s of d.sensors) {
    if (!s.is_active) continue
    sparklines.value[s.id] = await api.observations(s.id, s.type, Date.now() - 30 * 60_000, Date.now(), 16)
  }
}, { immediate: true })

const activeCount = computed(() => device.value?.sensors.filter((s) => s.is_active).length || 0)

function relTime(ts) {
  const diff = Date.now() - ts
  if (diff < 5000) return 'Just now'
  if (diff < 60_000) return `${Math.floor(diff / 1000)} sec ago`
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} min ago`
  return `${Math.floor(diff / 3_600_000)} hours ago`
}

function lastValue(s) {
  if (!s.is_active) return '---'
  const lv = devices.liveValues[s.id]
  if (lv) return lv.value
  const series = sparklines.value[s.id]
  if (series && series.length) return series[series.length - 1].value
  return '---'
}

function lastSeen(s) {
  if (!s.is_active) return 'Inactive'
  const lv = devices.liveValues[s.id]
  if (lv) return relTime(lv.timestamp)
  const series = sparklines.value[s.id]
  if (series && series.length) return relTime(series[series.length - 1].timestamp)
  return '---'
}

async function toggle(s) {
  await devices.toggleSensor(device.value.id, s.id, !s.is_active)
}

async function saveSensor(payload) {
  await devices.addSensor(device.value.id, payload)
}

async function deleteSensor() {
  if (!pendingDelete.value) return
  await devices.deleteSensor(device.value.id, pendingDelete.value)
  pendingDelete.value = null
}

function exploreSensor(s) {
  router.push({ name: 'explore' })
}
</script>

<template>
  <div v-if="device">
    <TopBar>
      <template #title>
        <RouterLink to="/devices" class="crumb">Devices</RouterLink>
        <span class="sep">/</span>
        <span class="here">{{ device.name }}</span>
      </template>
    </TopBar>

    <div class="page">
      <!-- Device header -->
      <div class="dev-head panel">
        <div class="row">
          <h1>{{ device.name }} <span class="edit">✎ edit</span></h1>
        </div>
        <span class="badge" :class="device.status === 'online' ? 'badge-online' : 'badge-offline'">
          <span class="status-dot" :style="{ background: device.status === 'online' ? 'var(--green)' : 'var(--red)' }"></span>
          {{ device.status === 'online' ? 'Online' : 'Offline' }}
        </span>

        <div class="meta-row">
          <div><span class="k">Last seen:</span><span class="v">{{ device.last_seen_at ? relTime(device.last_seen_at) : 'never' }}</span></div>
          <div><span class="k">Description:</span><span class="v">{{ device.description }}</span></div>
          <div><span class="k">Location:</span><span class="v">{{ device.latitude != null ? device.latitude.toFixed(4) : '—' }}, {{ device.longitude != null ? device.longitude.toFixed(4) : '—' }}</span></div>
        </div>
      </div>

      <!-- Sensors header -->
      <div class="sensors-head">
        <div>
          <h2>Sensors &amp; Datastreams</h2>
          <p>{{ device.sensors.length }} sensors configured, {{ activeCount }} active</p>
        </div>
        <button class="btn btn-primary" @click="showAddSensor = true">+ Add Sensor</button>
      </div>

      <!-- Sensors table -->
      <div class="table panel">
        <div class="th">
          <div>Sensor Name</div>
          <div>Observed Property</div>
          <div>Unit</div>
          <div>Active</div>
          <div>Last Value</div>
          <div>Last Updated</div>
          <div>Sparkline</div>
          <div>Actions</div>
        </div>
        <div
          v-for="s in device.sensors"
          :key="s.id"
          class="tr"
          :class="{ inactive: !s.is_active }"
        >
          <div class="name">{{ s.name }}</div>
          <div>{{ s.observed }}</div>
          <div>{{ s.unit }}{{ s.type === 'temperature' ? ' (Celsius)' : '' }}</div>
          <div>
            <button class="toggle" :class="{ on: s.is_active }" @click="toggle(s)">
              <span></span>
            </button>
          </div>
          <div :class="{ warn: s.type === 'co2' && lastValue(s) > 800 && s.is_active }">{{ lastValue(s) }}</div>
          <div class="muted">{{ lastSeen(s) }}</div>
          <div>
            <Sparkline
              v-if="s.is_active && sparklines[s.id]"
              :points="sparklines[s.id]"
              :color="colorForType(s.type)"
            />
            <span v-else class="muted">N/A</span>
          </div>
          <div class="acts">
            <button class="link" @click="exploreSensor(s)">Explore</button>
            <button class="ico danger" @click="pendingDelete = s.id">✕</button>
          </div>
        </div>
      </div>
    </div>

    <AddSensorModal :open="showAddSensor" @close="showAddSensor = false" @save="saveSensor" />
    <ConfirmDialog
      :open="!!pendingDelete"
      title="Delete sensor?"
      body="This will remove the sensor and all of its observations."
      confirm-text="Delete"
      danger
      @cancel="pendingDelete = null"
      @confirm="deleteSensor"
    />
  </div>
  <div v-else class="page">
    <p class="muted">Device not found.</p>
  </div>
</template>

<style scoped>
.crumb { color: var(--text-faint); font-weight: 400; font-size: 13px; }
.crumb:hover { color: var(--text-secondary); }
.sep { color: var(--text-faintest); margin: 0 8px; font-size: 13px; }
.here { color: var(--text-primary); font-size: 13px; font-weight: 600; }

.dev-head { padding: 22px 30px; position: relative; }
.dev-head h1 { margin: 0; font-size: 22px; color: var(--text-strong); display: inline-block; }
.dev-head .edit { color: var(--text-faintest); font-size: 12px; margin-left: 14px; cursor: pointer; }
.dev-head .badge { margin-top: 8px; }
.meta-row { margin-top: 18px; display: flex; gap: 36px; flex-wrap: wrap; }
.meta-row .k { color: var(--text-faint); font-size: 12px; margin-right: 8px; }
.meta-row .v { color: var(--text-secondary); font-size: 12px; }

.sensors-head { display: flex; justify-content: space-between; align-items: flex-end; margin: 28px 0 12px; }
.sensors-head h2 { margin: 0; font-size: 16px; color: var(--text-primary); font-weight: 600; }
.sensors-head p { margin: 4px 0 0; color: var(--text-faint); font-size: 12px; }

.table { padding: 0; overflow: hidden; }
.th, .tr {
  display: grid;
  grid-template-columns: 1.4fr 1.4fr 1fr 0.7fr 0.8fr 1fr 1.4fr 1fr;
  gap: 12px;
  padding: 12px 30px;
  align-items: center;
}
.th {
  background: var(--bg-elevated);
  color: var(--text-muted);
  font-size: 11px; font-weight: 600;
}
.tr {
  border-top: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 13px;
  min-height: 48px;
}
.tr.inactive { color: var(--text-faintest); }
.tr.inactive .name { color: var(--text-faint); }
.name { color: var(--text-primary); font-weight: 500; }
.muted { color: var(--text-faint); }
.warn { color: var(--yellow); font-weight: 500; }

.toggle {
  width: 32px; height: 18px;
  background: var(--border-strong);
  border: 0;
  border-radius: 9px;
  position: relative;
  cursor: pointer;
  transition: background 0.15s;
}
.toggle span {
  position: absolute; top: 2px; left: 2px;
  width: 14px; height: 14px;
  background: var(--text-faint);
  border-radius: 50%;
  transition: transform 0.15s, background 0.15s;
}
.toggle.on { background: var(--green); }
.toggle.on span { transform: translateX(14px); background: white; }

.acts { display: flex; gap: 12px; align-items: center; }
.link { background: none; border: 0; color: var(--indigo-light); font-size: 11px; cursor: pointer; }
.link:hover { text-decoration: underline; }
.ico { background: none; border: 0; cursor: pointer; font-size: 14px; color: var(--text-faint); width: 24px; height: 24px; border-radius: 4px; }
.ico.danger { color: var(--red); }
.ico:hover { background: var(--bg-elevated); }
</style>
