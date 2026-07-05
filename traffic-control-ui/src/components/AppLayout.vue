<script setup>
import Sidebar from '@/components/Sidebar.vue'
import StatusBar from '@/components/StatusBar.vue'
</script>

<template>
  <div class="layout">
    <Sidebar />
    <div class="main">
      <StatusBar />
      <RouterView />
    </div>
  </div>
</template>

<style scoped>
.layout { min-height: 100vh; background: var(--bg-base); }
.main {
  margin-left: var(--sidebar-w);
  min-height: 100vh;
}
</style>
