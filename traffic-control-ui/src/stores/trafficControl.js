import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api.js'

export const useTrafficControlStore = defineStore('trafficControl', () => {
  const controller = ref(null)
  const pid = ref(null)
  const hysteresis = ref(null)
  const emqxSettings = ref(null)
  const emqxState = ref(null)
  const calibrationSettings = ref(null)
  const calibrationState = ref({ items: [], total: 0, page: 0, size: 20 })
  const loading = ref(false)
  const error = ref(null)

  let pollTimer = null
  let connected = false

  async function fetchAll() {
    try {
      const [c, p, h, es, est, cs, cst] = await Promise.all([
        api.getController(),
        api.getPid(),
        api.getHysteresis(),
        api.getEmqxSettings(),
        api.getEmqxState(),
        api.getCalibrationSettings(),
        api.getCalibrationState(),
      ])
      controller.value = c
      pid.value = p
      hysteresis.value = h
      emqxSettings.value = es
      emqxState.value = est
      calibrationSettings.value = cs
      calibrationState.value = cst
      error.value = null
      connected = true
    } catch (e) {
      error.value = e.message
      connected = false
    }
  }

  async function fetchStatus() {
    try {
      const [c, est] = await Promise.all([
        api.getController(),
        api.getEmqxState(),
      ])
      controller.value = c
      emqxState.value = est
      if (!connected) {
        connected = true
        fetchAll()
      }
      error.value = null
    } catch (e) {
      error.value = e.message
      connected = false
    }
  }

  function startPolling(intervalMs = 5000) {
    fetchAll()
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = setInterval(fetchStatus, intervalMs)
  }

  function stopPolling() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }

  async function saveController(data) {
    loading.value = true
    try {
      controller.value = await api.updateController(data)
    } finally { loading.value = false }
  }

  async function savePid(data) {
    loading.value = true
    try {
      pid.value = await api.updatePid(data)
    } finally { loading.value = false }
  }

  async function saveHysteresis(data) {
    loading.value = true
    try {
      hysteresis.value = await api.updateHysteresis(data)
    } finally { loading.value = false }
  }

  async function saveEmqxSettings(data) {
    loading.value = true
    try {
      emqxSettings.value = await api.updateEmqxSettings(data)
    } finally { loading.value = false }
  }

  async function saveCalibrationSettings(data) {
    loading.value = true
    try {
      calibrationSettings.value = await api.updateCalibrationSettings(data)
    } finally { loading.value = false }
  }

  const calibrationQuery = ref('')

  async function refreshCalibrationState(page = calibrationState.value.page, size = calibrationState.value.size) {
    try {
      calibrationState.value = await api.getCalibrationState(page, size, calibrationQuery.value)
    } catch {
      // silently ignore — status bar already shows the error
    }
  }

  return {
    controller, pid, hysteresis,
    emqxSettings, emqxState, calibrationSettings, calibrationState, calibrationQuery,
    loading, error,
    fetchAll, fetchStatus, startPolling, stopPolling,
    saveController, savePid, saveHysteresis, saveEmqxSettings,
    saveCalibrationSettings, refreshCalibrationState,
  }
})
