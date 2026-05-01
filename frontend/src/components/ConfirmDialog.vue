<script setup>
const props = defineProps({
  open: Boolean,
  title: { type: String, default: 'Are you sure?' },
  body: { type: String, default: '' },
  confirmText: { type: String, default: 'Confirm' },
  danger: Boolean,
})
const emit = defineEmits(['confirm', 'cancel'])
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="backdrop" @click.self="emit('cancel')">
      <div class="dialog">
        <h3>{{ title }}</h3>
        <p v-if="body">{{ body }}</p>
        <div class="actions">
          <button class="btn btn-ghost" @click="emit('cancel')">Cancel</button>
          <button :class="['btn', danger ? 'btn-danger' : 'btn-primary']" @click="emit('confirm')">{{ confirmText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  z-index: 100;
}
.dialog {
  width: 420px; max-width: 90vw;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 24px;
  box-shadow: var(--shadow-card);
}
h3 { margin: 0 0 8px; font-size: 16px; color: var(--text-primary); }
p { margin: 0 0 18px; color: var(--text-muted); font-size: 13px; line-height: 1.5; }
.actions { display: flex; gap: 10px; justify-content: flex-end; }
</style>
