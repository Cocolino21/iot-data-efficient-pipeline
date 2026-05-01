<script setup>
defineProps({
  title: { type: String, default: '' },
})
</script>

<template>
  <header class="topbar">
    <div class="title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="actions"><slot name="actions" /></div>
    <div class="avatar"><slot name="avatar"><span>MB</span></slot></div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  height: var(--topbar-h);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 24px;
  z-index: 5;
}
.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 0 0 auto;
}
.actions { flex: 1; display: flex; gap: 12px; align-items: center; justify-content: flex-end; padding-right: 16px; }
.avatar {
  width: 32px; height: 32px; border-radius: 50%;
  background: rgba(79, 70, 229, 0.3);
  color: var(--indigo-light);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  flex-shrink: 0;
}
</style>
