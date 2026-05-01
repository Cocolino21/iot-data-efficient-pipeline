// Mock API layer. Every page goes through this file so swapping to a real
// backend is a one-file change: replace each function body with fetch() calls.
//
// Real backend would expose:
//   GET  /api/devices                              -> Device[]
//   POST /api/devices                              -> Device
//   GET  /api/devices/:id                          -> Device with sensors
//   POST /api/devices/:id/sensors                  -> Sensor
//   PATCH /api/datastreams/:id  { is_active }      -> Datastream
//   DELETE /api/sensors/:id
//   GET  /api/datastreams/:id/observations?from&to -> Observation[]
// And a websocket /api/stream that pushes Observations as they arrive.

import { generateSeries, latestValue, quality } from './mockGenerator.js'

const SEED_DEVICES = [
  {
    id: 'dev-living-room',
    name: 'Living Room Hub',
    description: 'Main hub in the living room, central monitoring point',
    latitude: 44.4268,
    longitude: 26.1025,
    status: 'online',
    last_seen_at: () => Date.now() - 1500,
    sensors: [
      { id: 'sn-temp-1', name: 'Temperature Sensor', type: 'temperature', observed: 'Temperature', unit: 'C', symbol: 'C', is_active: true },
      { id: 'sn-hum-1',  name: 'Humidity Sensor',    type: 'humidity',    observed: 'Relative Humidity', unit: '%RH', symbol: '%RH', is_active: true },
      { id: 'sn-co2-1',  name: 'CO2 Sensor',         type: 'co2',         observed: 'Carbon Dioxide', unit: 'ppm', symbol: 'ppm', is_active: true },
      { id: 'sn-lux-1',  name: 'Light Sensor',       type: 'light',       observed: 'Luminous Intensity', unit: 'lux', symbol: 'lux', is_active: true },
      { id: 'sn-pwr-1',  name: 'Power Meter',        type: 'energy',      observed: 'Power Consumption', unit: 'kWh', symbol: 'kWh', is_active: true },
      { id: 'sn-prs-1',  name: 'Barometric Sensor',  type: 'pressure',    observed: 'Atmospheric Pressure', unit: 'hPa', symbol: 'hPa', is_active: false },
    ],
  },
  {
    id: 'dev-bedroom',
    name: 'Bedroom Station',
    description: 'Secondary station in the master bedroom',
    latitude: 44.4268,
    longitude: 26.1025,
    status: 'online',
    last_seen_at: () => Date.now() - 120_000,
    sensors: [
      { id: 'sn-temp-2', name: 'Temperature Sensor', type: 'temperature', observed: 'Temperature', unit: 'C', symbol: 'C', is_active: true },
      { id: 'sn-hum-2',  name: 'Humidity Sensor',    type: 'humidity',    observed: 'Relative Humidity', unit: '%RH', symbol: '%RH', is_active: true },
      { id: 'sn-lux-2',  name: 'Light Sensor',       type: 'light',       observed: 'Luminous Intensity', unit: 'lux', symbol: 'lux', is_active: true },
    ],
  },
  {
    id: 'dev-garden',
    name: 'Garden Monitor',
    description: 'Outdoor monitoring node',
    latitude: 44.4270,
    longitude: 26.1030,
    status: 'offline',
    last_seen_at: () => Date.now() - 3 * 3600_000,
    sensors: [
      { id: 'sn-temp-3', name: 'Temperature Sensor', type: 'temperature', observed: 'Temperature', unit: 'C', symbol: 'C', is_active: false },
      { id: 'sn-hum-3',  name: 'Humidity Sensor',    type: 'humidity',    observed: 'Relative Humidity', unit: '%RH', symbol: '%RH', is_active: false },
      { id: 'sn-lux-3',  name: 'Light Sensor',       type: 'light',       observed: 'Luminous Intensity', unit: 'lux', symbol: 'lux', is_active: false },
      { id: 'sn-prs-3',  name: 'Pressure Sensor',    type: 'pressure',    observed: 'Atmospheric Pressure', unit: 'hPa', symbol: 'hPa', is_active: false },
    ],
  },
  {
    id: 'dev-kitchen',
    name: 'Kitchen Sensors',
    description: 'Kitchen monitoring node',
    latitude: 44.4268,
    longitude: 26.1025,
    status: 'online',
    last_seen_at: () => Date.now() - 800,
    sensors: [
      { id: 'sn-temp-4', name: 'Temperature Sensor', type: 'temperature', observed: 'Temperature', unit: 'C', symbol: 'C', is_active: true },
      { id: 'sn-hum-4',  name: 'Humidity Sensor',    type: 'humidity',    observed: 'Relative Humidity', unit: '%RH', symbol: '%RH', is_active: true },
      { id: 'sn-co2-4',  name: 'CO2 Sensor',         type: 'co2',         observed: 'Carbon Dioxide', unit: 'ppm', symbol: 'ppm', is_active: true },
      { id: 'sn-pwr-4',  name: 'Power Meter',        type: 'energy',      observed: 'Power Consumption', unit: 'kWh', symbol: 'kWh', is_active: true },
    ],
  },
]

// Frozen, in-memory mutable copy.
function deepClone(o) { return JSON.parse(JSON.stringify(o)) }
const DEVICES = SEED_DEVICES.map((d) => ({ ...d, last_seen_at: d.last_seen_at() }))

const delay = (ms = 80) => new Promise((r) => setTimeout(r, ms))

export const api = {
  async listDevices() {
    await delay()
    return DEVICES.map((d) => ({ ...deepClone(d), last_seen_at: d.status === 'online' ? Date.now() - Math.floor(Math.random() * 5000) : d.last_seen_at }))
  },

  async getDevice(id) {
    await delay()
    const d = DEVICES.find((x) => x.id === id)
    return d ? { ...deepClone(d), last_seen_at: d.status === 'online' ? Date.now() - Math.floor(Math.random() * 5000) : d.last_seen_at } : null
  },

  async createDevice(payload) {
    await delay()
    const d = {
      id: 'dev-' + Math.random().toString(36).slice(2, 8),
      name: payload.name || 'New Device',
      description: payload.description || '',
      latitude: Number(payload.latitude) || 0,
      longitude: Number(payload.longitude) || 0,
      status: 'online',
      last_seen_at: Date.now(),
      sensors: [],
    }
    DEVICES.push(d)
    return deepClone(d)
  },

  async deleteDevice(id) {
    await delay()
    const i = DEVICES.findIndex((d) => d.id === id)
    if (i >= 0) DEVICES.splice(i, 1)
  },

  async addSensor(deviceId, payload) {
    await delay()
    const d = DEVICES.find((x) => x.id === deviceId)
    if (!d) return null
    const sensor = {
      id: 'sn-' + Math.random().toString(36).slice(2, 8),
      name: payload.name,
      type: payload.type,
      observed: payload.observed,
      unit: payload.unit,
      symbol: payload.symbol || payload.unit,
      is_active: true,
    }
    d.sensors.push(sensor)
    return deepClone(sensor)
  },

  async toggleSensor(deviceId, sensorId, is_active) {
    await delay()
    const d = DEVICES.find((x) => x.id === deviceId)
    if (!d) return
    const s = d.sensors.find((x) => x.id === sensorId)
    if (s) s.is_active = is_active
  },

  async deleteSensor(deviceId, sensorId) {
    await delay()
    const d = DEVICES.find((x) => x.id === deviceId)
    if (!d) return
    d.sensors = d.sensors.filter((s) => s.id !== sensorId)
  },

  async observations(datastreamId, type, fromMs, toMs, maxPoints = 200) {
    await delay()
    return generateSeries(datastreamId, type, fromMs, toMs, maxPoints)
  },

  latest(datastreamId, type) {
    return { timestamp: Date.now(), value: latestValue(datastreamId, type) }
  },

  qualityFor: quality,

  // Real backend would replace this with a websocket subscription.
  // The shape here is identical to what the real stream would push.
  subscribeLive(callback, intervalMs = 2000) {
    const id = setInterval(() => {
      const samples = []
      for (const d of DEVICES) {
        if (d.status !== 'online') continue
        for (const s of d.sensors) {
          if (!s.is_active) continue
          samples.push({
            device_id: d.id,
            datastream_id: s.id,
            type: s.type,
            timestamp: Date.now(),
            value: latestValue(s.id, s.type),
          })
        }
      }
      callback(samples)
    }, intervalMs)
    return () => clearInterval(id)
  },
}
