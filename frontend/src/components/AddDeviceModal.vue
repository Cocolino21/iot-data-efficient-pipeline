<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['close', 'save'])

const name = ref('')
const description = ref('')
const latitude = ref('')
const longitude = ref('')

watch(() => props.open, (v) => {
  if (v) { name.value = ''; description.value = ''; latitude.value = ''; longitude.value = '' }
})

function save() {
  if (!name.value.trim()) return
  emit('save', {
    name: name.value.trim(),
    description: description.value.trim(),
    latitude: parseFloat(latitude.value) || 0,
    longitude: parseFloat(longitude.value) || 0,
  })
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="backdrop" @click.self="emit('close')">
      <div class="modal">
        <header>
          <h3>Add Device</h3>
          <button class="x" @click="emit('close')">✕</button>
        </header>
        <div class="body">
          <label><span>Name</span><input v-model="name" placeholder="e.g. Living Room Hub" /></label>
          <label><span>Description</span><textarea v-model="description" rows="2" placeholder="Where is it / what does it do" /></label>
          <div class="row">
            <label><span>Latitude</span><input v-model="latitude" placeholder="44.4268" /></label>
            <label><span>Longitude</span><input v-model="longitude" placeholder="26.1025" /></label>
          </div>
        </div>
        <footer>
          <button class="btn btn-ghost" @click="emit('close')">Cancel</button>
          <button class="btn btn-primary" :disabled="!name.trim()" @click="save">Add Device</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 480px; max-width: 92vw; background: var(--bg-panel); border: 1px solid var(--border); border-radius: 14px; box-shadow: var(--shadow-card); }
header { display: flex; align-items: center; justify-content: space-between; padding: 18px 22px; border-bottom: 1px solid var(--border); }
header h3 { margin: 0; font-size: 16px; color: var(--text-primary); }
.x { background: none; border: 0; color: var(--text-faint); cursor: pointer; font-size: 16px; }
.body { padding: 18px 22px; display: flex; flex-direction: column; gap: 16px; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
label { display: flex; flex-direction: column; gap: 6px; }
label > span { font-size: 11px; color: var(--text-faint); font-weight: 600; letter-spacing: 0.5px; text-transform: uppercase; }
footer { display: flex; gap: 10px; justify-content: flex-end; padding: 16px 22px; border-top: 1px solid var(--border); }
button[disabled] { opacity: 0.5; cursor: not-allowed; }
</style>
