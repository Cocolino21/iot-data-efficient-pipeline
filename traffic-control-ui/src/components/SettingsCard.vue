<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>

<template>
  <div class="card panel">
    <div class="card-header">
      <h3 class="card-title">{{ title }}</h3>
      <span v-if="subtitle" class="card-subtitle">{{ subtitle }}</span>
      <div class="card-actions"><slot name="actions" /></div>
    </div>
    <div class="card-body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.card {
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-strong);
}
.card-subtitle {
  font-size: 12px;
  color: var(--text-faint);
}
.card-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.card-body {
  display: grid;
  gap: 14px;
}
</style>
