<script setup>
import { useTrafficControlStore } from '@/stores/trafficControl.js'
import { storeToRefs } from 'pinia'

const store = useTrafficControlStore()
const { controller, emqxState, error } = storeToRefs(store)

const MODE_LABEL = { PID: 'PID', HYSTERESIS: 'HYSTERESIS', NONE: 'OFF' }
</script>

<template>
  <header class="status-bar">
    <div class="status-group">
      <span class="label">Controller</span>
      <span v-if="controller" class="badge" :class="controller.mode !== 'NONE' ? 'badge-online' : 'badge-offline'">
        {{ MODE_LABEL[controller.mode] ?? controller.mode }}
      </span>
      <span v-else class="badge badge-offline">—</span>
    </div>

    <div class="status-group">
      <span class="label">EMQX Tuning</span>
      <span v-if="emqxState" class="badge" :class="emqxState.state === 'BOOSTED' ? 'badge-warning' : (emqxState.enabled ? 'badge-online' : 'badge-offline')">
        {{ emqxState.enabled ? emqxState.state : 'OFF' }}
      </span>
      <span v-else class="badge badge-offline">—</span>
    </div>

    <div v-if="emqxState && emqxState.enabled" class="status-group">
      <span class="label">Drop Rate</span>
      <span class="value">{{ emqxState.lastDropRate?.toFixed(2) ?? '—' }}/s</span>
    </div>

    <div v-if="error" class="status-group">
      <span class="badge badge-offline">⚠ {{ error }}</span>
    </div>
  </header>
</template>

<style scoped>
.status-bar {
  position: sticky;
  top: 0;
  height: var(--topbar-h);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 24px;
  z-index: 5;
}
.status-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.value {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}
</style>
