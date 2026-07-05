<script setup>
import GrafanaEmbed from '@/components/GrafanaEmbed.vue'
</script>

<template>
  <div class="page">
    <h2 class="page-title">System Overview</h2>
    <p class="page-desc">Live pipeline monitoring — EMQX → Kafka → TimescaleDB</p>

    <GrafanaEmbed
      src="/d/traffic-control/traffic-control-dashboard?orgId=1&kiosk"
      height="850px"
    />
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-strong);
}
.page-desc {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--text-muted);
}
</style>
