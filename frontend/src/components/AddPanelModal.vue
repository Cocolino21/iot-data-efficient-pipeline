<script setup>
import { ref, computed, watch } from 'vue'
import { useDevicesStore } from '@/stores/devices.js'

const props = defineProps({
  open: Boolean,
  initial: { type: Object, default: null },
})
const emit = defineEmits(['close', 'save'])

const devices = useDevicesStore()
const deviceId = ref('')
const datastreamIds = ref([])
const type = ref('line')
const title = ref('')

watch(() => props.open, (v) => {
  if (!v) return
  if (props.initial) {
    type.value = props.initial.type
    title.value = props.initial.title
    datastreamIds.value = [...(props.initial.datastreamIds || [])]
    const first = devices.allSensors.find((s) => s.id === datastreamIds.value[0])
    deviceId.value = first?.device_id || devices.list[0]?.id || ''
  } else {
    deviceId.value = devices.list[0]?.id || ''
    datastreamIds.value = []
    type.value = 'line'
    title.value = ''
  }
})

const sensorsForDevice = computed(() => {
  const d = devices.list.find((x) => x.id === deviceId.value)
  return d ? d.sensors : []
})

function toggleDs(id) {
  const i = datastreamIds.value.indexOf(id)
  if (i >= 0) datastreamIds.value.splice(i, 1)
  else datastreamIds.value.push(id)
  if (!title.value) {
    const s = devices.allSensors.find((x) => x.id === id)
    if (s) title.value = `${s.observed} - ${devices.list.find((d) => d.id === deviceId.value)?.name || ''}`
  }
}

function save() {
  if (!datastreamIds.value.length) return
  const sensor = devices.allSensors.find((s) => s.id === datastreamIds.value[0])
  const subtitle = sensor ? `${sensor.observed} - ${sensor.symbol}` : ''
  emit('save', {
    type: type.value,
    title: title.value || sensor?.observed || 'Panel',
    subtitle,
    datastreamIds: [...datastreamIds.value],
    w: type.value === 'stat' || type.value === 'gauge' ? 4 : 6,
    h: type.value === 'table' ? 9 : 7,
  })
  emit('close')
}

const chartTypes = [
  { id: 'line',  label: 'Line' },
  { id: 'bar',   label: 'Bar' },
  { id: 'gauge', label: 'Gauge' },
  { id: 'stat',  label: 'Single Stat' },
  { id: 'table', label: 'Table' },
]
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="backdrop" @click.self="emit('close')">
      <div class="modal">
        <header>
          <h3>{{ initial ? 'Edit Panel' : 'Add Panel' }}</h3>
          <button class="x" @click="emit('close')">✕</button>
        </header>
        <div class="body">
          <label>
            <span>Device</span>
            <select v-model="deviceId">
              <option v-for="d in devices.list" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
          </label>

          <label>
            <span>Datastreams</span>
            <div class="chips">
              <button
                v-for="s in sensorsForDevice"
                :key="s.id"
                class="chip"
                :class="{ on: datastreamIds.includes(s.id) }"
                @click="toggleDs(s.id)"
              >
                {{ s.observed }} <span class="unit">({{ s.symbol }})</span>
              </button>
            </div>
          </label>

          <label>
            <span>Chart type</span>
            <div class="chips">
              <button
                v-for="t in chartTypes"
                :key="t.id"
                class="chip"
                :class="{ on: type === t.id }"
                @click="type = t.id"
              >{{ t.label }}</button>
            </div>
          </label>

          <label>
            <span>Title</span>
            <input v-model="title" placeholder="Panel title" />
          </label>
        </div>
        <footer>
          <button class="btn btn-ghost" @click="emit('close')">Cancel</button>
          <button class="btn btn-primary" :disabled="!datastreamIds.length" @click="save">
            {{ initial ? 'Save' : 'Add Panel' }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 560px; max-width: 92vw; background: var(--bg-panel); border: 1px solid var(--border); border-radius: 14px; box-shadow: var(--shadow-card); display: flex; flex-direction: column; max-height: 90vh; }
header { display: flex; align-items: center; justify-content: space-between; padding: 18px 22px; border-bottom: 1px solid var(--border); }
header h3 { margin: 0; font-size: 16px; color: var(--text-primary); }
.x { background: none; border: 0; color: var(--text-faint); cursor: pointer; font-size: 16px; }
.body { padding: 18px 22px; display: flex; flex-direction: column; gap: 18px; overflow: auto; }
label { display: flex; flex-direction: column; gap: 8px; }
label > span { font-size: 11px; color: var(--text-faint); font-weight: 600; letter-spacing: 0.5px; text-transform: uppercase; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}
.chip:hover { border-color: var(--text-faint); }
.chip.on { background: rgba(79, 70, 229, 0.2); color: var(--indigo-light); border-color: var(--indigo); }
.chip .unit { color: var(--text-faint); font-size: 11px; }
footer { display: flex; gap: 10px; justify-content: flex-end; padding: 16px 22px; border-top: 1px solid var(--border); }
button[disabled] { opacity: 0.5; cursor: not-allowed; }
</style>
