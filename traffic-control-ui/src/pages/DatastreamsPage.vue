<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import SettingsCard from '@/components/SettingsCard.vue'
import { api } from '@/services/api.js'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

// Two-series palette validated (dataviz six checks) against the dark panel
// surface #10202B: Go blue = actual/average, amber = expected baseline.
const C_ACTUAL = '#0092B8'
const C_EXPECTED = '#D97706'

const AXIS = {
  axisLine: { lineStyle: { color: '#1C2A35' } },
  axisLabel: { color: '#94A3B8', fontSize: 11 },
  splitLine: { lineStyle: { color: '#1C2A35' } },
}
const TOOLTIP = {
  backgroundColor: '#182430',
  borderColor: '#2B4150',
  textStyle: { color: '#E2E8F0', fontSize: 12 },
}
const LEGEND = { textStyle: { color: '#94A3B8', fontSize: 11 }, top: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 8 }

const DAY_MS = 86_400_000
const RANGES = {
  '24h': DAY_MS,
  '7d': 7 * DAY_MS,
  '30d': 30 * DAY_MS,
  '90d': 90 * DAY_MS,
  '1y': 365 * DAY_MS,
}

const list = ref({ items: [], total: 0, page: 0, size: 20 })
const selected = ref(null)
const baseline = ref([])
const actualByHour = ref({})
const aggregates = ref([])
const aggTier = ref('hourly')
const aggRange = ref('7d')
const aggLoaded = ref(false)
const aggLoading = ref(false)
const recon = ref(null) // { points: [{timestamp,value,reconstructed}], method, ... }
const rawMinutes = ref(15)
const rawLoaded = ref(false)
const rawLoading = ref(false)
const triggerMsg = ref('')

const query = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(list.value.total / list.value.size)))

async function loadList(page = 0) {
  list.value = await api.getDatastreams(
    Math.min(Math.max(page, 0), totalPages.value - 1), list.value.size, query.value)
}

let searchTimer = null
watch(query, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadList(0), 300)
})

onMounted(() => loadList())

async function select(row) {
  selected.value = row
  triggerMsg.value = ''
  aggregates.value = []
  aggLoaded.value = false
  recon.value = null
  rawLoaded.value = false
  baseline.value = await api.getBaseline(row.datastream_id)
  // Actual hourly averages over the last 24 h, keyed by hour-of-day, to
  // overlay against the baseline's expected hour-of-day profile.
  const now = Date.now()
  const rows = await api.getAggregates(row.datastream_id, 'hourly', now - DAY_MS, now)
  const byHour = {}
  for (const r of rows) byHour[new Date(r.bucket).getHours()] = r.avg_value
  actualByHour.value = byHour
}

// Explicit commit: aggregates only refetch on the Load button, never live as
// the tier/range selects change.
async function loadAggregates() {
  if (!selected.value) return
  aggLoading.value = true
  try {
    const now = Date.now()
    aggregates.value = await api.getAggregates(
      selected.value.datastream_id, aggTier.value, now - RANGES[aggRange.value], now)
    aggLoaded.value = true
  } finally {
    aggLoading.value = false
  }
}

// Explicit commit, same as the aggregates card: only refetch on Load.
async function loadRaw() {
  if (!selected.value) return
  rawLoading.value = true
  try {
    recon.value = await api.getReconstructed(selected.value.datastream_id, rawMinutes.value)
    rawLoaded.value = true
  } finally {
    rawLoading.value = false
  }
}

async function calibrateNow() {
  const res = await api.triggerCalibration(selected.value.datastream_id)
  triggerMsg.value = res.result
  loadList(list.value.page)
}

function statusClass(status) {
  if (status === 'collecting') return 'badge-warning'
  if (status === 'idle') return 'badge-online'
  return 'badge-offline'
}

const baselineOption = computed(() => {
  const hours = [...Array(24).keys()]
  const expected = hours.map((h) => baseline.value.find((b) => b.hour_of_day === h)?.expected_value ?? null)
  const actual = hours.map((h) => actualByHour.value[h] ?? null)
  return {
    grid: { left: 52, right: 16, top: 34, bottom: 28 },
    legend: LEGEND,
    tooltip: { trigger: 'axis', ...TOOLTIP },
    xAxis: { type: 'category', data: hours.map((h) => `${h}:00`), ...AXIS, splitLine: { show: false } },
    yAxis: { type: 'value', ...AXIS, splitNumber: 4 },
    series: [
      {
        name: 'Expected (baseline)',
        type: 'bar',
        data: expected,
        barWidth: '55%',
        itemStyle: { color: C_EXPECTED, borderRadius: [4, 4, 0, 0] },
      },
      {
        name: 'Actual (last 24 h)',
        type: 'line',
        data: actual,
        lineStyle: { width: 2, color: C_ACTUAL },
        itemStyle: { color: C_ACTUAL },
        symbolSize: 8,
      },
    ],
  }
})

// Measured points solid blue (line broken across gaps); reconstructed points
// dashed amber, anchored to the bracketing measured points so segments join.
const rawOption = computed(() => {
  const pts = recon.value?.points ?? []
  const measured = pts.map((p) => [p.timestamp, p.reconstructed ? null : p.value])
  const reconstructed = []
  let inRun = false
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i]
    if (p.reconstructed) {
      if (!inRun && i > 0) reconstructed.push([pts[i - 1].timestamp, pts[i - 1].value])
      reconstructed.push([p.timestamp, p.value])
      inRun = true
    } else if (inRun) {
      reconstructed.push([p.timestamp, p.value]) // closing anchor
      reconstructed.push([p.timestamp + 1, null]) // break before next run
      inRun = false
    }
  }
  return {
    grid: { left: 52, right: 16, top: 34, bottom: 28 },
    legend: { ...LEGEND, data: ['Measured', 'Reconstructed'] },
    tooltip: {
      trigger: 'axis',
      ...TOOLTIP,
      valueFormatter: (v) => (v != null ? Number(v).toFixed(2) : '—'),
    },
    xAxis: { type: 'time', ...AXIS, splitLine: { show: false } },
    yAxis: { type: 'value', ...AXIS, splitNumber: 4, scale: true },
    series: [
      {
        name: 'Measured',
        type: 'line',
        data: measured,
        lineStyle: { width: 2, color: C_ACTUAL },
        itemStyle: { color: C_ACTUAL },
        showSymbol: true,
        symbolSize: 5,
        connectNulls: false,
      },
      {
        name: 'Reconstructed',
        type: 'line',
        data: reconstructed,
        lineStyle: { width: 2, color: C_EXPECTED, type: 'dashed' },
        itemStyle: { color: C_EXPECTED },
        showSymbol: false,
        connectNulls: false,
      },
    ],
  }
})

const aggOption = computed(() => {
  const rows = aggregates.value
  const ts = (r) => +new Date(r.bucket)
  return {
    grid: { left: 52, right: 16, top: 34, bottom: 28 },
    legend: { ...LEGEND, data: ['Average', 'Min–max range'] },
    tooltip: {
      trigger: 'axis',
      ...TOOLTIP,
      formatter(params) {
        const i = params[0]?.dataIndex
        const r = rows[i]
        if (!r) return ''
        const fmt = (v) => (v != null ? Number(v).toFixed(2) : '—')
        return `${new Date(ts(r)).toLocaleString()}<br/>` +
          `avg ${fmt(r.avg_value)} · min ${fmt(r.min_value)} · max ${fmt(r.max_value)}<br/>` +
          `${r.sample_count} samples`
      },
    },
    xAxis: { type: 'time', ...AXIS, splitLine: { show: false } },
    yAxis: { type: 'value', ...AXIS, splitNumber: 4, scale: true },
    series: [
      // Invisible floor + stacked delta = the min–max envelope of the bucket,
      // drawn in the average's own hue at low alpha (same entity, not a new category).
      {
        name: 'min-floor',
        type: 'line',
        stack: 'band',
        data: rows.map((r) => [ts(r), r.min_value]),
        lineStyle: { opacity: 0 },
        symbol: 'none',
        silent: true,
        tooltip: { show: false },
      },
      {
        name: 'Min–max range',
        type: 'line',
        stack: 'band',
        data: rows.map((r) => [ts(r), (r.max_value ?? 0) - (r.min_value ?? 0)]),
        lineStyle: { opacity: 0 },
        symbol: 'none',
        areaStyle: { color: C_ACTUAL + '26' },
        silent: true,
      },
      {
        name: 'Average',
        type: 'line',
        data: rows.map((r) => [ts(r), r.avg_value]),
        lineStyle: { width: 2, color: C_ACTUAL },
        itemStyle: { color: C_ACTUAL },
        showSymbol: false,
      },
    ],
  }
})
</script>

<template>
  <div class="page">
    <h2 class="page-title">Datastreams</h2>
    <p class="page-desc">Inspect a stream's baseline profile and its continuous-aggregate tiers</p>

    <div class="layout">
      <SettingsCard title="Streams" class="list-card">
        <template #actions>
          <button class="btn btn-ghost" @click="loadList(list.page)">↻</button>
        </template>

        <input v-model="query" class="search" type="search" placeholder="Search stream / device…" />

        <div class="stream-list">
          <button
            v-for="row in list.items"
            :key="row.datastream_id"
            class="stream-item"
            :class="{ selected: selected?.datastream_id === row.datastream_id }"
            @click="select(row)"
          >
            <span class="mono">{{ row.datastream_id }}</span>
            <span class="badge" :class="statusClass(row.status)">{{ row.status }}</span>
          </button>
          <p v-if="!list.items.length" class="empty">No datastreams yet</p>
        </div>

        <div class="pager" v-if="list.total > list.size">
          <button class="btn btn-ghost" :disabled="list.page === 0" @click="loadList(list.page - 1)">‹</button>
          <span class="pager-info">{{ list.page + 1 }} / {{ totalPages }}</span>
          <button class="btn btn-ghost" :disabled="list.page >= totalPages - 1" @click="loadList(list.page + 1)">›</button>
        </div>
      </SettingsCard>

      <div class="detail" v-if="selected">
        <SettingsCard :title="`Calibration — ${selected.datastream_id}`">
          <template #actions>
            <button class="btn btn-primary" :disabled="selected.status === 'collecting'" @click="calibrateNow">
              Calibrate now
            </button>
          </template>
          <div class="status-row">
            <div class="status-item">
              <span class="field-label">Device</span>
              <span class="mono">{{ selected.thing_id ?? '—' }}</span>
            </div>
            <div class="status-item">
              <span class="field-label">Status</span>
              <span class="badge" :class="statusClass(selected.status)">{{ selected.status }}</span>
            </div>
            <div class="status-item">
              <span class="field-label">Drift</span>
              <span>{{ selected.drift_score != null ? selected.drift_score.toFixed(2) : '—' }}</span>
            </div>
            <div class="status-item">
              <span class="field-label">Last collected</span>
              <span>{{ selected.last_collected_at ? new Date(selected.last_collected_at).toLocaleString() : 'never' }}</span>
            </div>
          </div>
          <p v-if="triggerMsg" class="trigger-msg">{{ triggerMsg }}</p>
        </SettingsCard>

        <SettingsCard title="Baseline vs actual" subtitle="Expected hour-of-day profile against the last 24 h">
          <VChart v-if="baseline.length" class="chart" :option="baselineOption" :update-options="{ notMerge: true }" autoresize />
          <p v-else class="empty">No baseline yet — run a calibration to build one</p>
        </SettingsCard>

        <SettingsCard title="Raw data" subtitle="Measured points (PIP-filtered) with gaps reconstructed">
          <template #actions>
            <select v-model.number="rawMinutes" class="select">
              <option :value="5">Last 5 min</option>
              <option :value="15">Last 15 min</option>
              <option :value="60">Last 60 min</option>
            </select>
            <button class="btn btn-primary" :disabled="rawLoading" @click="loadRaw">
              {{ rawLoading ? 'Loading…' : 'Load' }}
            </button>
          </template>
          <template v-if="recon?.points?.length">
            <VChart class="chart" :option="rawOption" :update-options="{ notMerge: true }" autoresize />
            <p class="recon-method">Reconstruction: {{ recon.method }}</p>
          </template>
          <p v-else-if="rawLoaded" class="empty">No observations in this window</p>
          <p v-else class="empty">Pick an interval, then Load</p>
        </SettingsCard>

        <SettingsCard title="Aggregates" subtitle="Continuous-aggregate tiers" class="wide">
          <template #actions>
            <select v-model="aggTier" class="select">
              <option value="hourly">Hourly</option>
              <option value="daily">Daily</option>
              <option value="weekly">Weekly</option>
              <option value="monthly">Monthly</option>
            </select>
            <select v-model="aggRange" class="select">
              <option v-for="(_, k) in RANGES" :key="k" :value="k">Last {{ k }}</option>
            </select>
            <button class="btn btn-primary" :disabled="aggLoading" @click="loadAggregates">
              {{ aggLoading ? 'Loading…' : 'Load' }}
            </button>
          </template>
          <VChart v-if="aggregates.length" class="chart chart-tall" :option="aggOption" :update-options="{ notMerge: true }" autoresize />
          <p v-else-if="aggLoaded" class="empty">No aggregate rows in this range</p>
          <p v-else class="empty">Pick a tier and range, then Load</p>
        </SettingsCard>
      </div>

      <div v-else class="detail placeholder panel">
        <p class="empty">Select a datastream to inspect its baseline and aggregates</p>
      </div>
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
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  align-items: start;
}
.detail {
  display: grid;
  gap: 20px;
}
.placeholder {
  padding: 60px 20px;
}
.stream-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 60vh;
  overflow-y: auto;
}
.stream-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
  font-size: 12px;
}
.stream-item:hover { background: var(--bg-elevated); }
.stream-item.selected {
  background: rgba(100, 183, 204, 0.11);
  color: var(--indigo-light);
}
.mono {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
}
.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}
.status-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}
.field-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.trigger-msg {
  margin: 0;
  font-size: 12px;
  color: var(--green);
}
.recon-method {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--text-faint);
}
.chart { width: 100%; height: 260px; }
.chart-tall { height: 320px; }
.select {
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  font-family: inherit;
}
.search {
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  font-family: inherit;
  width: 100%;
}
.search::placeholder { color: var(--text-faint); }
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.pager-info {
  font-size: 12px;
  color: var(--text-muted);
}
.empty {
  text-align: center;
  padding: 32px;
  color: var(--text-faint);
  font-size: 13px;
  margin: 0;
}
</style>
