<script setup>
import { useTrafficControlStore } from '@/stores/trafficControl.js'

const store = useTrafficControlStore()
store.startPolling()
</script>

<template>
  <RouterView />
</template>
