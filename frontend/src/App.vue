<script setup>
import { onMounted } from 'vue'
import { useDevicesStore } from '@/stores/devices.js'
import { useDashboardStore } from '@/stores/dashboard.js'
import ChatButton from '@/components/ChatButton.vue'

const devices = useDevicesStore()
const dashboard = useDashboardStore()

onMounted(() => {
  devices.init()
  dashboard.init()
})
</script>

<template>
  <RouterView />
  <ChatButton />
</template>
