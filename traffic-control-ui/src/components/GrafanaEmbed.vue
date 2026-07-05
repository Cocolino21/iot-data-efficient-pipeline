<script setup>
import { computed } from 'vue'

const props = defineProps({
  src: { type: String, required: true },
  height: { type: String, default: '350px' },
  // Grafana auto-refresh interval (e.g. '5s', '10s'); null disables it.
  refresh: { type: String, default: null },
  // Relative time window that slides with the clock.
  from: { type: String, default: 'now-15m' },
  to: { type: String, default: 'now' },
})

const grafanaBase = import.meta.env.VITE_GRAFANA_URL || '/grafana'

const iframeSrc = computed(() => {
  if (props.src.startsWith('http')) return props.src
  let url = `${grafanaBase}${props.src}`
  // Only opt into live behaviour when a refresh interval is requested,
  // so existing embeds keep the dashboard's own time range untouched.
  if (props.refresh) {
    const sep = props.src.includes('?') ? '&' : '?'
    url += `${sep}from=${props.from}&to=${props.to}&refresh=${props.refresh}`
  }
  return url
})
</script>

<template>
  <div class="grafana-embed" :style="{ height }">
    <iframe
      :src="iframeSrc"
      frameborder="0"
      allowfullscreen
    ></iframe>
  </div>
</template>

<style scoped>
.grafana-embed {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-panel);
  box-shadow: var(--shadow-panel);
}
.grafana-embed iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
