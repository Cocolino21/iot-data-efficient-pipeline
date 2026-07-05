<script setup>
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const items = [
  { to: '/overview',    label: 'Overview',     icon: 'chart' },
  { to: '/control',     label: 'Control Loop', icon: 'sliders' },
  { to: '/emqx',        label: 'EMQX Tuning',  icon: 'zap' },
  { to: '/calibration', label: 'Calibration',  icon: 'target' },
]
</script>

<template>
  <aside class="sidebar">
    <div class="logo">Traffic Control</div>
    <nav class="nav">
      <RouterLink
        v-for="i in items"
        :key="i.to"
        :to="i.to"
        class="nav-item"
        :class="{ active: route.path.startsWith(i.to) }"
      >
        <span class="nav-icon" :data-icon="i.icon"></span>
        <span>{{ i.label }}</span>
      </RouterLink>
    </nav>
    <div class="bottom">
      <span class="version">v0.1.0</span>
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
  font-size: 16px;
  font-weight: 700;
  color: var(--text-strong);
  border-bottom: 1px solid var(--border);
  letter-spacing: 0.3px;
}
.nav { padding: 12px 12px 0; flex: 1; display: flex; flex-direction: column; gap: 4px; }
.bottom {
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}
.version { font-size: 11px; color: var(--text-faintest); }
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
.nav-icon[data-icon="chart"]   { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><path d='M3 3v18h18'/><path d='M7 16l4-8 4 4 5-9'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><path d='M3 3v18h18'/><path d='M7 16l4-8 4 4 5-9'/></svg>"); }
.nav-icon[data-icon="sliders"] { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><line x1='4' y1='21' x2='4' y2='14'/><line x1='4' y1='10' x2='4' y2='3'/><line x1='12' y1='21' x2='12' y2='12'/><line x1='12' y1='8' x2='12' y2='3'/><line x1='20' y1='21' x2='20' y2='16'/><line x1='20' y1='12' x2='20' y2='3'/><line x1='1' y1='14' x2='7' y2='14'/><line x1='9' y1='8' x2='15' y2='8'/><line x1='17' y1='16' x2='23' y2='16'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><line x1='4' y1='21' x2='4' y2='14'/><line x1='4' y1='10' x2='4' y2='3'/><line x1='12' y1='21' x2='12' y2='12'/><line x1='12' y1='8' x2='12' y2='3'/><line x1='20' y1='21' x2='20' y2='16'/><line x1='20' y1='12' x2='20' y2='3'/><line x1='1' y1='14' x2='7' y2='14'/><line x1='9' y1='8' x2='15' y2='8'/><line x1='17' y1='16' x2='23' y2='16'/></svg>"); }
.nav-icon[data-icon="zap"]     { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><polygon points='13 2 3 14 12 14 11 22 21 10 12 10 13 2'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><polygon points='13 2 3 14 12 14 11 22 21 10 12 10 13 2'/></svg>"); }
.nav-icon[data-icon="target"]  { -webkit-mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><circle cx='12' cy='12' r='10'/><circle cx='12' cy='12' r='6'/><circle cx='12' cy='12' r='2'/></svg>"); mask-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2'><circle cx='12' cy='12' r='10'/><circle cx='12' cy='12' r='6'/><circle cx='12' cy='12' r='2'/></svg>"); }
</style>
