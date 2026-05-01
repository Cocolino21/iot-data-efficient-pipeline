<script setup>
defineProps({
  title: String,
  subtitle: String,
  quality: { type: String, default: 'good' },
  editMode: Boolean,
})
const emit = defineEmits(['edit', 'remove'])

const colorByQuality = { good: 'var(--green)', moderate: 'var(--yellow)', poor: 'var(--red)' }
</script>

<template>
  <div class="bp">
    <div class="bp-head">
      <div class="bp-titles">
        <div class="bp-title">{{ title }}</div>
        <div class="bp-sub">{{ subtitle }}</div>
      </div>
      <div class="bp-meta">
        <span class="dot" :style="{ background: colorByQuality[quality] || colorByQuality.good }"></span>
        <span v-if="!editMode" class="q-text" :style="{ color: colorByQuality[quality] || colorByQuality.good }">
          {{ quality === 'good' ? 'Good' : quality === 'moderate' ? 'Moderate' : 'Poor' }}
        </span>
        <template v-if="editMode">
          <button class="ico" title="Edit" @click.stop="emit('edit')">✎</button>
          <button class="ico danger" title="Remove" @click.stop="emit('remove')">✕</button>
        </template>
      </div>
    </div>
    <div class="bp-body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.bp {
  height: 100%;
  background: var(--bg-panel);
  border-radius: 12px;
  display: flex; flex-direction: column;
  padding: 18px 22px 14px;
  overflow: hidden;
  box-shadow: var(--shadow-panel);
}
.bp-head { display: flex; align-items: flex-start; justify-content: space-between; }
.bp-titles { min-width: 0; }
.bp-title { font-size: 14px; font-weight: 600; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bp-sub { font-size: 11px; color: var(--text-faint); margin-top: 4px; }
.bp-meta { display: flex; align-items: center; gap: 6px; }
.dot { width: 8px; height: 8px; border-radius: 50%; }
.q-text { font-size: 10px; }
.ico {
  width: 26px; height: 26px;
  background: var(--bg-elevated);
  border: 0; border-radius: 6px;
  color: var(--text-muted);
  cursor: pointer;
}
.ico:hover { color: var(--text-secondary); }
.ico.danger { color: var(--red); }
.bp-body { flex: 1; min-height: 0; margin-top: 10px; position: relative; }
</style>
