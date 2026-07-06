<script setup>
import { ref, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useTrafficControlStore } from '@/stores/trafficControl.js'
import SettingsCard from '@/components/SettingsCard.vue'
import GrafanaEmbed from '@/components/GrafanaEmbed.vue'

const store = useTrafficControlStore()
const { controller, pid, hysteresis, loading } = storeToRefs(store)

const MODES = [
  { key: 'PID',        label: 'PID' },
  { key: 'HYSTERESIS', label: 'Hysteresis' },
  { key: 'NONE',       label: 'Off' },
]

// Which mode is currently driving the loop (from the backend).
const activeMode = computed(() => controller.value?.mode ?? 'NONE')

// Which mode's parameters the user is looking at — independent of what's active.
const viewedMode = ref(null)

const pidForm = ref({})
const hystForm = ref({})
const deadZone = ref(0)
const pollSeconds = ref(10)

const saved = ref(false)
const pollSaved = ref(false)

// Seed the viewed tab from the active mode on first load.
watch(controller, (v) => {
  if (v) {
    if (viewedMode.value === null) viewedMode.value = v.mode ?? 'NONE'
    deadZone.value = v.deadZone ?? 0
    pollSeconds.value = (v.pollIntervalMs ?? 10000) / 1000
  }
}, { immediate: true })

watch(pid,        (v) => { if (v) pidForm.value = { ...v } }, { immediate: true })
watch(hysteresis, (v) => { if (v) hystForm.value = { ...v } }, { immediate: true })

const isViewingActive = computed(() => viewedMode.value === activeMode.value)

function flashSaved() {
  saved.value = true
  setTimeout(() => saved.value = false, 2000)
}

async function saveParams() {
  if (viewedMode.value === 'PID') {
    await store.savePid(pidForm.value)
  } else if (viewedMode.value === 'HYSTERESIS') {
    await store.saveHysteresis(hystForm.value)
  }
  // The dead-zone gate is controller-level; persist it alongside.
  await store.saveController({ deadZone: deadZone.value })
  flashSaved()
}

async function makeActive() {
  await store.saveController({ mode: viewedMode.value, deadZone: deadZone.value })
}

async function savePollInterval() {
  await store.saveController({ pollIntervalMs: Math.round(pollSeconds.value * 1000) })
  pollSaved.value = true
  setTimeout(() => pollSaved.value = false, 2000)
}
</script>

<template>
  <div class="page">
    <h2 class="page-title">Control Loop</h2>
    <p class="page-desc">Adaptive PIP threshold adjustment based on Kafka consumer lag</p>

    <div class="grid">
      <SettingsCard title="Active Controller">
        <p class="mode-help">
          Exactly one controller drives the loop. Switching is instant — no restart needed.
        </p>

        <div class="segmented" role="tablist">
          <button
            v-for="m in MODES"
            :key="m.key"
            class="segment"
            :class="{ viewing: viewedMode === m.key }"
            role="tab"
            :aria-selected="viewedMode === m.key"
            @click="viewedMode = m.key"
          >
            <span>{{ m.label }}</span>
            <span v-if="activeMode === m.key" class="active-badge">● Active</span>
          </button>
        </div>

        <p class="view-note" :class="{ muted: !isViewingActive }">
          <template v-if="isViewingActive">
            Viewing the active controller.
          </template>
          <template v-else>
            Viewing <strong>{{ MODES.find(m => m.key === viewedMode)?.label }}</strong> —
            not active. Save updates its parameters; click <em>Make Active</em> to run it.
          </template>
        </p>

        <div class="poll-row">
          <label class="field-label" for="poll">Poll interval</label>
          <input id="poll" type="number" min="1" step="1" v-model.number="pollSeconds" />
          <span class="poll-unit">s</span>
          <button class="btn btn-primary" :disabled="loading" @click="savePollInterval">Save</button>
          <span v-if="pollSaved" class="saved-msg">✓ Saved</span>
        </div>
        <p class="poll-hint">How often the live loop reads lag and (re)broadcasts. Applies instantly.</p>
      </SettingsCard>

      <SettingsCard title="PIP adjustments (live)">
        <GrafanaEmbed
          src="/d-solo/traffic-control/traffic-control-dashboard?orgId=1&panelId=11&kiosk"
          height="280px"
          refresh="5s"
          from="now-15m"
        />
      </SettingsCard>
    </div>

    <div class="charts-grid params-card">
      <SettingsCard title="Consumer Lag Trend (live)">
        <GrafanaEmbed
          src="/d-solo/traffic-control/traffic-control-dashboard?orgId=1&panelId=10&kiosk"
          height="280px"
          refresh="1s"
          from="now-15m"
        />
      </SettingsCard>

      <SettingsCard title="EMQX Messages Received (live)"
                    subtitle="Falls when shedding, recovers when relaxed">
        <GrafanaEmbed
          src="/d-solo/traffic-control/traffic-control-dashboard?orgId=1&panelId=4&kiosk"
          height="280px"
          refresh="5s"
          from="now-15m"
        />
      </SettingsCard>
    </div>

    <!-- PID parameters -->
    <SettingsCard v-if="viewedMode === 'PID'" title="PID Parameters" class="params-card">
      <template #actions>
        <span v-if="isViewingActive" class="running-tag">● Running</span>
        <button v-else class="btn btn-primary" :disabled="loading" @click="makeActive">
          Make Active
        </button>
      </template>

      <div class="field-grid">
        <label class="field">
          <span class="field-label">Target Lag</span>
          <input type="number" v-model.number="pidForm.targetLag" />
        </label>
        <label class="field">
          <span class="field-label">Kp (proportional)</span>
          <input type="number" step="0.0001" v-model.number="pidForm.kp" />
        </label>
        <label class="field">
          <span class="field-label">Ki (integral)</span>
          <input type="number" step="0.0001" v-model.number="pidForm.ki" />
        </label>
        <label class="field">
          <span class="field-label">Kd (derivative)</span>
          <input type="number" step="0.0001" v-model.number="pidForm.kd" />
        </label>
        <label class="field">
          <span class="field-label">Integral Max</span>
          <input type="number" v-model.number="pidForm.integralMax" />
        </label>
        <label class="field">
          <span class="field-label">Output Min (%)</span>
          <input type="number" v-model.number="pidForm.outputMin" />
        </label>
        <label class="field">
          <span class="field-label">Output Max (%)</span>
          <input type="number" v-model.number="pidForm.outputMax" />
        </label>
        <label class="field">
          <span class="field-label">Dead Zone (%)</span>
          <input type="number" step="0.1" v-model.number="deadZone" />
        </label>
      </div>

      <div class="actions">
        <button class="btn btn-primary" :disabled="loading" @click="saveParams">
          {{ loading ? 'Saving…' : 'Save' }}
        </button>
        <span v-if="saved" class="saved-msg">✓ Saved</span>
      </div>
    </SettingsCard>

    <!-- Hysteresis parameters -->
    <SettingsCard v-else-if="viewedMode === 'HYSTERESIS'" title="Hysteresis Parameters" class="params-card">
      <template #actions>
        <span v-if="isViewingActive" class="running-tag">● Running</span>
        <button v-else class="btn btn-primary" :disabled="loading" @click="makeActive">
          Make Active
        </button>
      </template>

      <div class="field-grid">
        <label class="field">
          <span class="field-label">Upper Lag</span>
          <input type="number" v-model.number="hystForm.upperLag" />
        </label>
        <label class="field">
          <span class="field-label">Lower Lag</span>
          <input type="number" v-model.number="hystForm.lowerLag" />
        </label>
        <label class="field">
          <span class="field-label">Shed Step (%)</span>
          <input type="number" step="0.1" v-model.number="hystForm.step" />
        </label>
        <label class="field">
          <span class="field-label">Gain (% / 100% overshoot)</span>
          <input type="number" step="0.1" v-model.number="hystForm.gain" />
        </label>
        <label class="field">
          <span class="field-label">Relax Step (%)</span>
          <input type="number" step="0.1" v-model.number="hystForm.relaxStep" />
        </label>
        <label class="field">
          <span class="field-label">Output Min (%)</span>
          <input type="number" v-model.number="hystForm.outputMin" />
        </label>
        <label class="field">
          <span class="field-label">Output Max (%)</span>
          <input type="number" v-model.number="hystForm.outputMax" />
        </label>
        <label class="field">
          <span class="field-label">Dead Zone (%)</span>
          <input type="number" step="0.1" v-model.number="deadZone" />
        </label>
      </div>

      <div class="actions">
        <button class="btn btn-primary" :disabled="loading" @click="saveParams">
          {{ loading ? 'Saving…' : 'Save' }}
        </button>
        <span v-if="saved" class="saved-msg">✓ Saved</span>
      </div>
    </SettingsCard>

    <!-- Off -->
    <SettingsCard v-else title="Loop Disabled" class="params-card">
      <template #actions>
        <span v-if="isViewingActive" class="running-tag off">● Off</span>
        <button v-else class="btn btn-primary" :disabled="loading" @click="makeActive">
          Turn Off
        </button>
      </template>
      <p class="off-note">
        No controller is driving PIP adjustments. The poller still reads lag but broadcasts nothing.
      </p>
    </SettingsCard>
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
.params-card { margin-top: 20px; }
/* Consumer Lag is the dominant chart; EMQX messages sits narrower beside it. */
.charts-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

/* Mode selector */
.mode-help {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--text-muted);
}
.segmented {
  display: inline-flex;
  padding: 4px;
  gap: 4px;
  background: var(--bg-base, rgba(0, 0, 0, 0.2));
  border: 1px solid var(--border);
  border-radius: 10px;
}
.segment {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--text-faint);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.12s, background 0.12s, border-color 0.12s;
}
.segment:hover { color: var(--text-secondary); }
.segment.viewing {
  background: var(--bg-surface);
  border-color: var(--indigo);
  color: var(--text-strong);
}
.active-badge {
  font-size: 10px;
  font-weight: 600;
  color: var(--green);
  letter-spacing: 0.3px;
}
.view-note {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}
.view-note.muted { color: var(--text-muted); }

.poll-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
}
.poll-row input {
  width: 80px;
}
.poll-row .field-label { margin: 0; }
.poll-unit { font-size: 13px; color: var(--text-muted); margin-left: -4px; }
.poll-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.running-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--green);
}
.running-tag.off { color: var(--text-muted); }

.off-note {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
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
  margin-top: 16px;
}
.saved-msg {
  font-size: 12px;
  color: var(--green);
  font-weight: 500;
}
</style>
