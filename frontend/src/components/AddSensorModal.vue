<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['close', 'save'])

const presets = [
  { type: 'temperature', name: 'Temperature Sensor', observed: 'Temperature',          unit: 'C',   symbol: 'C' },
  { type: 'humidity',    name: 'Humidity Sensor',    observed: 'Relative Humidity',    unit: '%RH', symbol: '%RH' },
  { type: 'co2',         name: 'CO2 Sensor',         observed: 'Carbon Dioxide',       unit: 'ppm', symbol: 'ppm' },
  { type: 'light',       name: 'Light Sensor',       observed: 'Luminous Intensity',   unit: 'lux', symbol: 'lux' },
  { type: 'energy',      name: 'Power Meter',        observed: 'Power Consumption',    unit: 'kWh', symbol: 'kWh' },
  { type: 'pressure',    name: 'Barometric Sensor',  observed: 'Atmospheric Pressure', unit: 'hPa', symbol: 'hPa' },
]

const choice = ref(presets[0].type)
const name = ref(presets[0].name)

watch(() => props.open, (v) => { if (v) { choice.value = presets[0].type; name.value = presets[0].name } })
watch(choice, (t) => { const p = presets.find((x) => x.type === t); if (p) name.value = p.name })

function save() {
  const p = presets.find((x) => x.type === choice.value)
  if (!p) return
  emit('save', { ...p, name: name.value.trim() || p.name })
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="backdrop" @click.self="emit('close')">
      <div class="modal">
        <header>
          <h3>Add Sensor</h3>
          <button class="x" @click="emit('close')">✕</button>
        </header>
        <div class="body">
          <label>
            <span>Sensor type</span>
            <select v-model="choice">
              <option v-for="p in presets" :key="p.type" :value="p.type">{{ p.observed }} ({{ p.symbol }})</option>
            </select>
          </label>
          <label><span>Sensor name</span><input v-model="name" /></label>
        </div>
        <footer>
          <button class="btn btn-ghost" @click="emit('close')">Cancel</button>
          <button class="btn btn-primary" @click="save">Add Sensor</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 460px; max-width: 92vw; background: var(--bg-panel); border: 1px solid var(--border); border-radius: 14px; box-shadow: var(--shadow-card); }
header { display: flex; align-items: center; justify-content: space-between; padding: 18px 22px; border-bottom: 1px solid var(--border); }
header h3 { margin: 0; font-size: 16px; color: var(--text-primary); }
.x { background: none; border: 0; color: var(--text-faint); cursor: pointer; font-size: 16px; }
.body { padding: 18px 22px; display: flex; flex-direction: column; gap: 16px; }
label { display: flex; flex-direction: column; gap: 6px; }
label > span { font-size: 11px; color: var(--text-faint); font-weight: 600; letter-spacing: 0.5px; text-transform: uppercase; }
footer { display: flex; gap: 10px; justify-content: flex-end; padding: 16px 22px; border-top: 1px solid var(--border); }
</style>
