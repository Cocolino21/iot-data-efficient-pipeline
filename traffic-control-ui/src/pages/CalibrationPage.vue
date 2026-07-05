<script setup>
import { ref, watch, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useTrafficControlStore } from '@/stores/trafficControl.js'
import SettingsCard from '@/components/SettingsCard.vue'

const store = useTrafficControlStore()
const { calibrationSettings, calibrationState, loading } = storeToRefs(store)

const form = ref({})
const saved = ref(false)

watch(calibrationSettings, (v) => {
  if (v) form.value = { ...v }
}, { immediate: true })

onMounted(() => {
  store.refreshCalibrationState()
})

async function save() {
  await store.saveCalibrationSettings(form.value)
  saved.value = true
  setTimeout(() => saved.value = false, 2000)
}

function statusClass(status) {
  if (status === 'collecting') return 'badge-warning'
  if (status === 'idle') return 'badge-online'
  return 'badge-offline'
}
</script>

<template>
  <div class="page">
    <h2 class="page-title">Calibration</h2>
    <p class="page-desc">Baseline drift detection and raw-mode collection orchestration</p>

    <div class="grid">
      <SettingsCard title="Calibration Parameters">
        <div class="field-grid">
          <label class="field">
            <span class="field-label">Max Concurrent</span>
            <input type="number" v-model.number="form.maxConcurrent" />
          </label>
          <label class="field">
            <span class="field-label">Collection TTL (s)</span>
            <input type="number" v-model.number="form.collectionTtlSeconds" />
          </label>
          <label class="field">
            <span class="field-label">Baseline Days</span>
            <input type="number" v-model.number="form.baselineDays" />
          </label>
          <label class="field">
            <span class="field-label">Poll Interval (ms)</span>
            <input type="number" v-model.number="form.pollIntervalMs" />
          </label>
        </div>

        <div class="actions">
          <button class="btn btn-primary" :disabled="loading" @click="save">
            {{ loading ? 'Saving…' : 'Save' }}
          </button>
          <span v-if="saved" class="saved-msg">✓ Saved</span>
        </div>
      </SettingsCard>

      <SettingsCard title="Calibration State">
        <template #actions>
          <button class="btn btn-ghost" @click="store.refreshCalibrationState()">↻ Refresh</button>
        </template>

        <div class="table-wrap">
          <table v-if="calibrationState.length">
            <thead>
              <tr>
                <th>Datastream</th>
                <th>Thing</th>
                <th>Status</th>
                <th>Drift</th>
                <th>Needs Cal.</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in calibrationState" :key="row.datastream_id">
                <td class="mono">{{ row.datastream_id }}</td>
                <td class="mono">{{ row.thing_id ?? '—' }}</td>
                <td><span class="badge" :class="statusClass(row.status)">{{ row.status }}</span></td>
                <td>{{ row.drift_score != null ? row.drift_score.toFixed(2) : '—' }}</td>
                <td>{{ row.needs_calibration ? '✓' : '—' }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else class="empty">No calibration entries yet</p>
        </div>
      </SettingsCard>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-strong);
}
.page-desc {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--text-muted);
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.saved-msg {
  font-size: 12px;
  color: var(--green);
  font-weight: 500;
}
.table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th {
  text-align: left;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  border-bottom: 1px solid var(--border);
}
td {
  padding: 8px 10px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-muted);
}
.mono {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
}
.empty {
  text-align: center;
  padding: 32px;
  color: var(--text-faint);
}
</style>
