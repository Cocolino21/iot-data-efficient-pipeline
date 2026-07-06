<script setup>
import { ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useTrafficControlStore } from '@/stores/trafficControl.js'
import SettingsCard from '@/components/SettingsCard.vue'
import GrafanaEmbed from '@/components/GrafanaEmbed.vue'

const store = useTrafficControlStore()
const { emqxSettings, emqxState, loading } = storeToRefs(store)

const form = ref({})
const saved = ref(false)

watch(emqxSettings, (v) => {
  if (v) form.value = { ...v }
}, { immediate: true })

async function save() {
  await store.saveEmqxSettings(form.value)
  saved.value = true
  setTimeout(() => saved.value = false, 2000)
}

function toggleEnabled() {
  form.value.enabled = !form.value.enabled
  save()
}
</script>

<template>
  <div class="page">
    <h2 class="page-title">EMQX Auto-Tuning</h2>
    <p class="page-desc">Automatically boost Kafka bridge params when message drops are detected</p>

    <div class="state-banner" v-if="emqxState">
      <div class="state-item">
        <span class="state-label">State</span>
        <span class="badge" :class="emqxState.state === 'BOOSTED' ? 'badge-warning' : 'badge-online'">
          {{ emqxState.state }}
        </span>
      </div>
      <div class="state-item">
        <span class="state-label">Last Drop Rate</span>
        <span class="state-value">{{ emqxState.lastDropRate?.toFixed(2) ?? '—' }}/s</span>
      </div>
      <div class="state-item">
        <span class="state-label">Auto-Tuning</span>
        <button class="switch" :class="{ on: form.enabled }" role="switch"
                :aria-checked="!!form.enabled" @click="toggleEnabled">
          <span class="switch-knob" />
        </button>
        <span class="state-value">{{ form.enabled ? 'On' : 'Off' }}</span>
      </div>
    </div>

    <div class="grid">
      <SettingsCard title="Thresholds">
        <div class="field-grid">
          <label class="field">
            <span class="field-label">Drop Rate Threshold</span>
            <input type="number" step="0.1" v-model.number="form.dropRateThreshold" />
          </label>
          <label class="field">
            <span class="field-label">Cooldown Polls</span>
            <input type="number" v-model.number="form.cooldownPolls" />
          </label>
        </div>
      </SettingsCard>

      <SettingsCard title="Default Values" subtitle="Normal operation">
        <div class="field-grid">
          <label class="field">
            <span class="field-label">Max Linger Time</span>
            <input type="text" v-model="form.defaultMaxLingerTime" />
          </label>
          <label class="field">
            <span class="field-label">Max Linger Bytes</span>
            <input type="text" v-model="form.defaultMaxLingerBytes" />
          </label>
          <label class="field">
            <span class="field-label">Max Batch Bytes</span>
            <input type="text" v-model="form.defaultMaxBatchBytes" />
          </label>
          <label class="field">
            <span class="field-label">Max Inflight</span>
            <input type="number" v-model.number="form.defaultMaxInflight" />
          </label>
        </div>
      </SettingsCard>

      <SettingsCard title="Upper Limits" subtitle="Applied when drops detected">
        <div class="field-grid">
          <label class="field">
            <span class="field-label">Max Linger Time</span>
            <input type="text" v-model="form.upperMaxLingerTime" />
          </label>
          <label class="field">
            <span class="field-label">Max Linger Bytes</span>
            <input type="text" v-model="form.upperMaxLingerBytes" />
          </label>
          <label class="field">
            <span class="field-label">Max Batch Bytes</span>
            <input type="text" v-model="form.upperMaxBatchBytes" />
          </label>
          <label class="field">
            <span class="field-label">Max Inflight</span>
            <input type="number" v-model.number="form.upperMaxInflight" />
          </label>
        </div>
      </SettingsCard>

      <SettingsCard title="EMQX → Kafka Bridge (live)">
        <GrafanaEmbed
          src="/d-solo/traffic-control/traffic-control-dashboard?orgId=1&panelId=6&kiosk"
          height="280px"
        />
      </SettingsCard>
    </div>

    <div class="actions">
      <button class="btn btn-primary" :disabled="loading" @click="save">
        {{ loading ? 'Saving…' : 'Save All' }}
      </button>
      <span v-if="saved" class="saved-msg">✓ Saved</span>
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
.state-banner {
  display: flex;
  gap: 32px;
  padding: 14px 20px;
  background: var(--bg-panel);
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-panel);
}
.state-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.state-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.state-value {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 600;
}
.switch {
  position: relative;
  width: 40px;
  height: 22px;
  border-radius: 11px;
  border: 1px solid var(--border-strong);
  background: var(--bg-elevated);
  cursor: pointer;
  padding: 0;
  transition: background 0.15s, border-color 0.15s;
}
.switch-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: transform 0.15s, background 0.15s;
}
.switch.on {
  background: var(--green);
  border-color: var(--green);
}
.switch.on .switch-knob {
  transform: translateX(18px);
  background: white;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
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
}
.saved-msg {
  font-size: 12px;
  color: var(--green);
  font-weight: 500;
}
</style>
