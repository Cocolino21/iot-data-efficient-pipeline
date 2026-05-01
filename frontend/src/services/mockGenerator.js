// Synthetic time-series generators per observed property type.
// Produces realistic-looking but deterministic-ish data so the UI feels alive.

const seedFor = (id) => {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return h
}

function rng(seed) {
  let s = seed >>> 0 || 1
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 0xffffffff
  }
}

const PROFILES = {
  temperature: { base: 22, amp: 3, noise: 0.4, period: 86400_000, decimals: 1 },
  humidity:    { base: 60, amp: 8, noise: 1.2, period: 43200_000, decimals: 1 },
  co2:         { base: 700, amp: 200, noise: 25, period: 21600_000, decimals: 0 },
  light:       { base: 400, amp: 350, noise: 30, period: 86400_000, decimals: 0 },
  energy:      { base: 2.0, amp: 1.4, noise: 0.25, period: 21600_000, decimals: 2 },
  pressure:    { base: 1013, amp: 6, noise: 0.6, period: 86400_000, decimals: 1 },
}

export function profileFor(type) {
  return PROFILES[type] || { base: 50, amp: 10, noise: 1, period: 86400_000, decimals: 2 }
}

export function valueAt(datastreamId, type, ts) {
  const p = profileFor(type)
  const r = rng(seedFor(datastreamId) + Math.floor(ts / 60_000))
  const phase = (ts % p.period) / p.period
  const wave = Math.sin(phase * Math.PI * 2)
  const v = p.base + wave * p.amp + (r() - 0.5) * 2 * p.noise
  return Number(v.toFixed(p.decimals))
}

// Generate a series of points for a given range with an appropriate step.
export function generateSeries(datastreamId, type, fromMs, toMs, maxPoints = 200) {
  const span = toMs - fromMs
  const step = Math.max(Math.floor(span / maxPoints), 1000)
  const out = []
  for (let t = fromMs; t <= toMs; t += step) {
    out.push({ timestamp: t, value: valueAt(datastreamId, type, t) })
  }
  return out
}

// Latest value for a sensor (uses "now").
export function latestValue(datastreamId, type) {
  return valueAt(datastreamId, type, Date.now())
}

// Quality classifier per type — used for the green/yellow/red dot.
export function quality(type, value) {
  switch (type) {
    case 'co2':
      if (value < 800) return 'good'
      if (value < 1200) return 'moderate'
      return 'poor'
    case 'temperature':
      if (value >= 18 && value <= 26) return 'good'
      if (value >= 15 && value <= 30) return 'moderate'
      return 'poor'
    case 'humidity':
      if (value >= 40 && value <= 70) return 'good'
      if (value >= 30 && value <= 80) return 'moderate'
      return 'poor'
    default:
      return 'good'
  }
}
