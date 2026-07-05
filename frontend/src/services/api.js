// Talks to the core-service Spring Boot backend over fetch with the JWT cookie
// (HttpOnly `auth`). FE never sees the token; it just sends credentials.
//
// Endpoints (see core-service):
//   GET    /api/auth/me
//   POST   /api/auth/logout
//   GET    /oauth2/authorization/google                      (browser redirect)
//   GET    /api/devices                                       -> Device[]
//   POST   /api/devices                                       -> Device
//   GET    /api/devices/:id                                   -> Device with sensors
//   DELETE /api/devices/:id
//   POST   /api/devices/:id/sensors                           -> Sensor
//   PATCH  /api/datastreams/:id  { is_active }
//   DELETE /api/sensors/:id
//   GET    /api/datastreams/:id/observations?from&to&maxPoints

import { quality } from './mockGenerator.js'

const API_BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8083').replace(/\/+$/, '')
// traffic-control runs as a separate service (control-loop settings live here).
const TC_API_BASE = (import.meta.env.VITE_TC_API_URL || 'http://localhost:8082').replace(/\/+$/, '')

async function request(path, { method = 'GET', body, query, base = API_BASE } = {}) {
  let url = `${base}${path}`
  if (query) {
    const qs = new URLSearchParams(
      Object.entries(query).filter(([, v]) => v !== undefined && v !== null),
    ).toString()
    if (qs) url += `?${qs}`
  }
  const res = await fetch(url, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status} ${res.statusText}`)
    err.status = res.status
    try { err.body = await res.json() } catch { /* noop */ }
    throw err
  }
  return res.json()
}

export const api = {
  // --- auth ---
  loginUrl() {
    return `${API_BASE}/oauth2/authorization/google`
  },
  async me() {
    try {
      return await request('/api/auth/me')
    } catch (e) {
      if (e.status === 401) return null
      throw e
    }
  },
  logout() {
    return request('/api/auth/logout', { method: 'POST' })
  },

  // --- devices ---
  listDevices() {
    return request('/api/devices')
  },
  getDevice(id) {
    return request(`/api/devices/${id}`)
  },
  createDevice(payload) {
    return request('/api/devices', { method: 'POST', body: payload })
  },
  deleteDevice(id) {
    return request(`/api/devices/${id}`, { method: 'DELETE' })
  },

  // --- sensors / datastreams ---
  addSensor(deviceId, payload) {
    return request(`/api/devices/${deviceId}/sensors`, { method: 'POST', body: payload })
  },
  toggleSensor(_deviceId, sensorId, is_active) {
    return request(`/api/datastreams/${sensorId}`, { method: 'PATCH', body: { is_active } })
  },
  deleteSensor(_deviceId, sensorId) {
    return request(`/api/sensors/${sensorId}`, { method: 'DELETE' })
  },

  // --- observations ---
  observations(datastreamId, _type, fromMs, toMs, maxPoints = 200) {
    return request(`/api/datastreams/${datastreamId}/observations`, {
      query: { from: Math.floor(fromMs), to: Math.floor(toMs), maxPoints },
    })
  },

  qualityFor: quality,

  // Live stream is not implemented on the backend yet. Polling + manual
  // refresh covers the FE today; return a no-op unsubscribe so the store
  // contract stays the same.
  subscribeLive() {
    return () => {}
  },
}

// traffic-control (control loop) settings. Separate origin from core-service.
function tcRequest(path, opts = {}) {
  return request(path, { ...opts, base: TC_API_BASE })
}

export const tcApi = {
  getController() {
    return tcRequest('/api/controller')
  },
  putController(body) {
    return tcRequest('/api/controller', { method: 'PUT', body })
  },
  getPid() {
    return tcRequest('/api/pid')
  },
  putPid(body) {
    return tcRequest('/api/pid', { method: 'PUT', body })
  },
  getHysteresis() {
    return tcRequest('/api/hysteresis')
  },
  putHysteresis(body) {
    return tcRequest('/api/hysteresis', { method: 'PUT', body })
  },
}
