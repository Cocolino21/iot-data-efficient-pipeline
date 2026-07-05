import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/overview',
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    children: [
      { path: 'overview',    name: 'overview',    component: () => import('@/pages/OverviewPage.vue') },
      { path: 'control',     name: 'control',     component: () => import('@/pages/ControlPage.vue') },
      { path: 'emqx',        name: 'emqx',        component: () => import('@/pages/EmqxPage.vue') },
      { path: 'calibration', name: 'calibration', component: () => import('@/pages/CalibrationPage.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
