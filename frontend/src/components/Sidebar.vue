<script setup>
import { RouterLink } from 'vue-router'

const items = [
  { to: '/dashboard', label: 'Dashboard', icon: 'home' },
  { to: '/explore',   label: 'Explore',   icon: 'compass' },
  { to: '/devices',   label: 'Devices',   icon: 'cpu' },
]
</script>

<template>
  <aside class="sidebar">
    <RouterLink to="/" class="logo">Horizon</RouterLink>
    <nav class="nav">
      <RouterLink
        v-for="i in items"
        :key="i.to"
        :to="i.to"
        class="nav-item"
        :class="{ active: $route.path.startsWith(i.to) }"
      >
        <span class="nav-icon" :data-icon="i.icon"></span>
        <span>{{ i.label }}</span>
      </RouterLink>
    </nav>
    <div class="bottom">
      <RouterLink to="/settings" class="nav-item" :class="{ active: $route.path === '/settings' }">
        <span class="nav-icon" data-icon="settings"></span>
        <span>Settings</span>
      </RouterLink>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  height: 100vh;
  background: var(--bg-surface);
  border-right: 1px solid var(--border);
  position: fixed;
  top: 0; left: 0;
  display: flex;
  flex-direction: column;
  z-index: 10;
}
.logo {
  display: block;
  padding: 20px 28px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-strong);
  border-bottom: 1px solid var(--border);
  letter-spacing: 0.3px;
}
.nav { padding: 12px 12px 0; flex: 1; display: flex; flex-direction: column; gap: 4px; }
.bottom { padding: 12px; border-top: 1px solid var(--border); }
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-faint);
  transition: color 0.12s, background 0.12s;
}
.nav-item:hover { color: var(--text-secondary); background: rgba(79, 70, 229, 0.06); }
.nav-item.active {
  color: var(--indigo-light);
  background: rgba(79, 70, 229, 0.15);
  font-weight: 500;
}
.nav-item.active::before {
  content: '';
  position: absolute; left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--indigo);
  border-radius: 2px;
}
.nav-icon {
  width: 16px; height: 16px;
  display: inline-block;
  background-color: currentColor;
  -webkit-mask-repeat: no-repeat; mask-repeat: no-repeat;
  -webkit-mask-position: center; mask-position: center;
  -webkit-mask-size: contain; mask-size: contain;
}
.nav-icon[data-icon="home"]    { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><path d='M3 11.5L12 4l9 7.5'/><path d='M5 10v10h14V10'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><path d='M3 11.5L12 4l9 7.5'/><path d='M5 10v10h14V10'/></svg>"); }
.nav-icon[data-icon="compass"] { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><circle cx='12' cy='12' r='9'/><polygon points='15,9 11,11 9,15 13,13'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><circle cx='12' cy='12' r='9'/><polygon points='15,9 11,11 9,15 13,13'/></svg>"); }
.nav-icon[data-icon="cpu"]     { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><rect x='6' y='6' width='12' height='12' rx='2'/><path d='M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><rect x='6' y='6' width='12' height='12' rx='2'/><path d='M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3'/></svg>"); }
.nav-icon[data-icon="settings"]{ -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z'/></svg>"); }
</style>
