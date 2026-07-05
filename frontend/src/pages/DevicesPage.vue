<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/TopBar.vue'
import AddDeviceModal from '@/components/AddDeviceModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useDevicesStore } from '@/stores/devices.js'

const devices = useDevicesStore()
const router = useRouter()

const showAdd = ref(false)
const pendingDelete = ref(null)

function relTime(ts) {
  const diff = Date.now() - ts
  if (diff < 5000) return 'Just now'
  if (diff < 60_000) return `${Math.floor(diff / 1000)} sec ago`
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} min ago`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} hours ago`
  return `${Math.floor(diff / 86_400_000)} days ago`
}

function activeCount(d) { return d.sensors.filter((s) => s.is_active).length }

function openDevice(d) {
  router.push({ name: 'device-detail', params: { id: d.id } })
}

async function saveDevice(payload) {
  await devices.createDevice(payload)
}
async function deleteDevice() {
  if (!pendingDelete.value) return
  await devices.deleteDevice(pendingDelete.value)
  pendingDelete.value = null
}
</script>

<template>
  <div>
    <TopBar title="Devices">
      <template #actions>
        <button class="btn btn-primary" @click="showAdd = true">+ Add Device</button>
      </template>
    </TopBar>

    <div class="page">
      <div class="grid">
        <div
          v-for="d in devices.list"
          :key="d.id"
          class="card"
          @click="openDevice(d)"
        >
          <div class="bar"></div>
          <div class="head">
            <div class="name">{{ d.name }}</div>
          </div>
          <div class="row"><span class="k">Last seen:</span><span class="v">{{ d.last_seen_at ? relTime(d.last_seen_at) : 'never' }}</span></div>
          <div class="row"><span class="k">Location:</span><span class="v">{{ d.latitude != null ? d.latitude.toFixed(4) : '—' }}, {{ d.longitude != null ? d.longitude.toFixed(4) : '—' }}</span></div>
          <div class="row"><span class="k">Sensors:</span><span class="v">{{ activeCount(d) }} active / {{ d.sensors.length }} total</span></div>

          <div class="footer">
            <span class="hint">Click to view details →</span>
            <div class="actions" @click.stop>
              <button class="ico" title="Edit" @click="openDevice(d)">✎</button>
              <button class="ico danger" title="Delete" @click="pendingDelete = d.id">✕</button>
            </div>
          </div>
        </div>

        <div class="card empty" @click="showAdd = true">
          <div class="plus">+</div>
          <div class="lbl">Add a new device</div>
        </div>
      </div>
    </div>

    <AddDeviceModal :open="showAdd" @close="showAdd = false" @save="saveDevice" />
    <ConfirmDialog
      :open="!!pendingDelete"
      title="Delete device?"
      body="This will remove the device and all of its sensors and historical data."
      confirm-text="Delete"
      danger
      @cancel="pendingDelete = null"
      @confirm="deleteDevice"
    />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(370px, 1fr));
  gap: 24px;
}
.card {
  position: relative;
  background: var(--bg-panel);
  border-radius: 16px;
  padding: 26px 28px 18px;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.15s;
  box-shadow: var(--shadow-panel);
  overflow: hidden;
  min-height: 220px;
  display: flex; flex-direction: column;
}
.card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.35); }
.bar { position: absolute; top: 0; left: 0; right: 0; height: 4px; background: var(--green); }
.card.offline .bar { background: var(--red); }

.head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.name { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.row { display: flex; gap: 12px; margin-top: 12px; font-size: 12px; }
.row:first-of-type { margin-top: 22px; }
.k { color: var(--text-faint); }
.v { color: var(--text-secondary); }

.footer { margin-top: auto; padding-top: 16px; display: flex; justify-content: space-between; align-items: center; }
.hint { color: var(--indigo-light); font-size: 11px; }
.actions { display: flex; gap: 6px; }
.ico {
  width: 28px; height: 28px;
  background: var(--bg-elevated);
  color: var(--text-faint);
  border: 0; border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.ico:hover { background: var(--border); color: var(--text-secondary); }
.ico.danger { color: var(--red); }
.ico.danger:hover { background: rgba(239,68,68,0.15); }

.card.empty {
  border: 1px dashed var(--border);
  background: var(--bg-panel);
  display: flex; align-items: center; justify-content: center;
  flex-direction: column;
  color: var(--text-faintest);
}
.card.empty .plus { font-size: 32px; color: var(--border-strong); }
.card.empty .lbl { font-size: 14px; color: var(--text-faintest); margin-top: 6px; }
.card.empty:hover { border-color: var(--text-faint); color: var(--text-faint); }
</style>
