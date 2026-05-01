<script setup>
import { ref, computed } from 'vue'
import { useTimeStore, TIME_PRESETS } from '@/stores/time.js'

const time = useTimeStore()
const open = ref(false)
const label = computed(() => time.label)

function pick(id) {
  time.setPreset(id)
  open.value = false
}
</script>

<template>
  <div class="trp" @click.stop>
    <button class="trigger" @click="open = !open">
      <span class="ic">⏱</span>
      <span class="lbl">{{ label }}</span>
      <span class="caret">▾</span>
    </button>
    <div v-if="open" class="menu">
      <div v-for="g in TIME_PRESETS" :key="g.group" class="group">
        <div class="group-label">{{ g.group }}</div>
        <button
          v-for="p in g.items"
          :key="p.id"
          class="opt"
          :class="{ active: time.presetId === p.id }"
          @click="pick(p.id)"
        >{{ p.label }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.trp { position: relative; }
.trigger {
  display: inline-flex; align-items: center; gap: 8px;
  height: 32px; padding: 0 14px;
  background: var(--bg-panel);
  color: var(--text-muted);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  font-size: 12px; font-weight: 400;
  cursor: pointer;
}
.trigger:hover { color: var(--text-secondary); }
.ic { color: var(--text-faint); font-size: 11px; }
.caret { color: var(--text-faint); font-size: 10px; margin-left: 6px; }
.menu {
  position: absolute; top: calc(100% + 6px); right: 0;
  width: 240px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 8px;
  z-index: 50;
  box-shadow: var(--shadow-card);
  max-height: 70vh; overflow: auto;
}
.group { padding: 4px 0; }
.group-label {
  font-size: 10px; font-weight: 600; letter-spacing: 1px;
  color: var(--text-faint); padding: 6px 8px;
  text-transform: uppercase;
}
.opt {
  display: block; width: 100%; text-align: left;
  background: transparent; border: 0;
  color: var(--text-secondary);
  padding: 7px 10px; border-radius: 6px;
  font-size: 12px; cursor: pointer;
}
.opt:hover { background: var(--bg-elevated); }
.opt.active { background: rgba(79,70,229,0.18); color: var(--indigo-light); }
</style>
