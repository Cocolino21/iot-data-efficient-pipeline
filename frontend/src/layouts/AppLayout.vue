<script setup>
import Sidebar from '@/components/Sidebar.vue'
</script>

<template>
  <div class="layout">
    <Sidebar />
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout { min-height: 100vh; background: var(--bg-base); }
.main {
  margin-left: var(--sidebar-w);
  min-height: 100vh;
}
</style>
