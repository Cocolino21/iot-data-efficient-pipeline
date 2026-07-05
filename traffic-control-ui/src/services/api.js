const API_BASE = import.meta.env.VITE_TRAFFIC_CONTROL_URL || ''

async function request(path, { method = 'GET', body } = {}) {
  const url = `${API_BASE}${path}`
  let res
  try {
    res = await fetch(url, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new Error('Backend unavailable')
  }
  if (res.status === 204) return null
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status} ${res.statusText}`)
    err.status = res.status
    throw err
  }
  return res.json()
}

export const api = {
  // Controller mode
  getController()      { return request('/api/controller') },
  updateController(data) { return request('/api/controller', { method: 'PUT', body: data }) },

  // PID
  getPid()            { return request('/api/pid') },
  updatePid(data)     { return request('/api/pid', { method: 'PUT', body: data }) },

  // Hysteresis
  getHysteresis()     { return request('/api/hysteresis') },
  updateHysteresis(data) { return request('/api/hysteresis', { method: 'PUT', body: data }) },

  // EMQX
  getEmqxSettings()   { return request('/api/emqx/settings') },
  updateEmqxSettings(data) { return request('/api/emqx/settings', { method: 'PUT', body: data }) },
  getEmqxState()      { return request('/api/emqx/state') },

  // Calibration
  getCalibrationSettings() { return request('/api/calibration/settings') },
  updateCalibrationSettings(data) { return request('/api/calibration/settings', { method: 'PUT', body: data }) },
  getCalibrationState() { return request('/api/calibration/state') },

  // Test
  publishPip(pct)     { return request(`/api/test/pip?pct=${pct}`, { method: 'POST' }) },
}
