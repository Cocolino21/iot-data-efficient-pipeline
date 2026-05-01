<script setup>
import { ref, computed } from 'vue'
import { GridLayout } from 'grid-layout-plus'

import TopBar from '@/components/TopBar.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import PanelRenderer from '@/components/panels/PanelRenderer.vue'
import AddPanelModal from '@/components/AddPanelModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useDashboardStore } from '@/stores/dashboard.js'
import { useTimeStore } from '@/stores/time.js'

const dashboard = useDashboardStore()
const time = useTimeStore()

const showAdd = ref(false)
const editingPanel = ref(null)
const pendingRemoveId = ref(null)

const layout = computed({
  get: () => dashboard.panels,
  set: (v) => { /* grid-layout-plus mutates in place */ }
})

function openAdd() { editingPanel.value = null; showAdd.value = true }
function openEdit(p) { editingPanel.value = p; showAdd.value = true }

function savePanel(payload) {
  if (editingPanel.value) {
    dashboard.updatePanel(editingPanel.value.i, payload)
  } else {
    dashboard.addPanel(payload)
  }
}

function requestRemove(id) { pendingRemoveId.value = id }
function confirmRemove() {
  if (pendingRemoveId.value) dashboard.removePanel(pendingRemoveId.value)
  pendingRemoveId.value = null
}

function onLayoutUpdated() {
  dashboard.persist()
}

function refresh() { time.refresh() }
</script>

<template>
  <div>
    <TopBar title="Dashboard">
      <template #actions>
        <TimeRangePicker />
        <button class="btn btn-secondary" title="Refresh" @click="refresh">↻</button>
        <button class="btn btn-primary" @click="openAdd">+ Add Panel</button>
        <label class="edit-toggle btn btn-secondary">
          <span>Edit Mode</span>
          <input type="checkbox" :checked="dashboard.editMode" @change="dashboard.setEditMode($event.target.checked)" />
          <span class="switch" :class="{ on: dashboard.editMode }"></span>
        </label>
      </template>
    </TopBar>

    <div class="grid-wrap">
      <GridLayout
        v-model:layout="dashboard.panels"
        :col-num="12"
        :row-height="36"
        :is-draggable="dashboard.editMode"
        :is-resizable="dashboard.editMode"
        :margin="[16, 16]"
        :use-css-transforms="true"
        @layout-updated="onLayoutUpdated"
      >
        <template #item="{ item }">
          <PanelRenderer
            :panel="item"
            :edit-mode="dashboard.editMode"
            @edit="openEdit(item)"
            @remove="requestRemove(item.i)"
          />
        </template>
      </GridLayout>

      <div v-if="!dashboard.panels.length" class="empty">
        <div>No panels yet</div>
        <button class="btn btn-primary" @click="openAdd">+ Add your first panel</button>
      </div>
    </div>

    <AddPanelModal
      :open="showAdd"
      :initial="editingPanel"
      @close="showAdd = false"
      @save="savePanel"
    />
    <ConfirmDialog
      :open="!!pendingRemoveId"
      title="Remove panel?"
      body="This will remove the panel from your dashboard."
      confirm-text="Remove"
      danger
      @cancel="pendingRemoveId = null"
      @confirm="confirmRemove"
    />
  </div>
</template>

<style scoped>
.grid-wrap { padding: 16px; min-height: calc(100vh - var(--topbar-h)); }
.empty {
  padding: 80px 0; text-align: center; color: var(--text-faint);
  display: flex; flex-direction: column; align-items: center; gap: 16px;
}
.edit-toggle {
  display: inline-flex; align-items: center; gap: 10px;
  cursor: pointer; user-select: none;
}
.edit-toggle input { display: none; }
.switch {
  width: 32px; height: 18px;
  background: var(--border-strong);
  border-radius: 9px;
  position: relative;
  transition: background 0.15s;
}
.switch::after {
  content: '';
  position: absolute; top: 2px; left: 2px;
  width: 14px; height: 14px;
  background: var(--text-faint);
  border-radius: 50%;
  transition: transform 0.15s, background 0.15s;
}
.switch.on { background: var(--indigo); }
.switch.on::after { transform: translateX(14px); background: white; }
</style>

<style>
/* grid-layout-plus item styling — global so it applies to children */
.vgl-item--placeholder { background: rgba(79, 70, 229, 0.2) !important; border-radius: 12px; }
.vgl-item--resizing, .vgl-item--dragging { opacity: 0.85; }
.vgl-item > .vgl-item__resizer {
  border-color: var(--text-faint) !important;
  opacity: 0.5;
}
.vgl-item > .vgl-item__resizer::after { border-color: var(--text-faint) !important; }
</style>
