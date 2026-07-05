import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth.js'

const routes = [
  { path: '/', name: 'landing', component: () => import('@/pages/LandingPage.vue') },
  { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue') },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue') },
      { path: 'explore', name: 'explore', component: () => import('@/pages/ExplorePage.vue') },
      { path: 'devices', name: 'devices', component: () => import('@/pages/DevicesPage.vue') },
      { path: 'devices/:id', name: 'device-detail', component: () => import('@/pages/DeviceDetailPage.vue') },
      { path: 'settings', name: 'settings', component: () => import('@/pages/SettingsPage.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.loaded) await auth.load()
  const needsAuth = to.matched.some((r) => r.meta.requiresAuth)
  if (needsAuth && !auth.isAuthenticated) {
    return { name: 'login' }
  }
})

export default router
