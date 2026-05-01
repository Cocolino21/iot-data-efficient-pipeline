import { defineStore } from 'pinia'
import { api } from '@/services/api.js'

export const useDevicesStore = defineStore('devices', {
  state: () => ({
    list: [],
    loading: false,
    initialized: false,
    liveValues: {}, // datastream_id -> { value, timestamp }
    _unsub: null,
  }),
  getters: {
    byId: (s) => (id) => s.list.find((d) => d.id === id),
    allSensors: (s) => s.list.flatMap((d) => d.sensors.map((sn) => ({ ...sn, device_id: d.id, device_name: d.name }))),
    activeSensors() { return this.allSensors.filter((s) => s.is_active) },
  },
  actions: {
    async init() {
      if (this.initialized) return
      this.initialized = true
      await this.refresh()
      this._unsub = api.subscribeLive((samples) => {
        for (const s of samples) {
          this.liveValues[s.datastream_id] = { value: s.value, timestamp: s.timestamp }
        }
      }, 2000)
    },
    async refresh() {
      this.loading = true
      this.list = await api.listDevices()
      this.loading = false
    },
    async createDevice(payload) {
      const d = await api.createDevice(payload)
      this.list.push(d)
      return d
    },
    async deleteDevice(id) {
      await api.deleteDevice(id)
      this.list = this.list.filter((d) => d.id !== id)
    },
    async addSensor(deviceId, payload) {
      const sn = await api.addSensor(deviceId, payload)
      const d = this.list.find((x) => x.id === deviceId)
      if (d && sn) d.sensors.push(sn)
      return sn
    },
    async toggleSensor(deviceId, sensorId, is_active) {
      await api.toggleSensor(deviceId, sensorId, is_active)
      const d = this.list.find((x) => x.id === deviceId)
      if (!d) return
      const s = d.sensors.find((x) => x.id === sensorId)
      if (s) s.is_active = is_active
    },
    async deleteSensor(deviceId, sensorId) {
      await api.deleteSensor(deviceId, sensorId)
      const d = this.list.find((x) => x.id === deviceId)
      if (d) d.sensors = d.sensors.filter((s) => s.id !== sensorId)
    },
  },
})
