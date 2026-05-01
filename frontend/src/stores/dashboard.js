import { defineStore } from 'pinia'

const STORAGE_KEY = 'horizon.dashboard.v1'

const DEFAULT_PANELS = [
  { i: 'p1', x: 0,  y: 0, w: 8, h: 8, type: 'line',  title: 'Living Room Temperature', subtitle: 'Temperature sensor - Celsius (C)', datastreamIds: ['sn-temp-1'] },
  { i: 'p2', x: 8,  y: 0, w: 4, h: 8, type: 'gauge', title: 'Humidity', subtitle: '%RH', datastreamIds: ['sn-hum-1'], min: 0, max: 100 },
  { i: 'p3', x: 0,  y: 8, w: 8, h: 8, type: 'bar',   title: 'Energy Usage', subtitle: 'Power meter - kWh per hour', datastreamIds: ['sn-pwr-1'] },
  { i: 'p4', x: 8,  y: 8, w: 4, h: 8, type: 'stat',  title: 'CO2 Level', subtitle: 'ppm', datastreamIds: ['sn-co2-1'] },
  { i: 'p5', x: 0,  y: 16, w: 8, h: 9, type: 'table', title: 'Recent Observations', subtitle: 'All sensors - Raw data log', datastreamIds: ['sn-temp-1', 'sn-hum-1', 'sn-co2-1', 'sn-lux-1', 'sn-pwr-1'] },
  { i: 'p6', x: 8,  y: 16, w: 4, h: 9, type: 'gauge', title: 'Pressure', subtitle: 'Barometric - hPa', datastreamIds: ['sn-prs-1'], min: 950, max: 1050 },
]

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    panels: [],
    editMode: false,
    initialized: false,
  }),
  actions: {
    init() {
      if (this.initialized) return
      this.initialized = true
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        try { this.panels = JSON.parse(raw) } catch { this.panels = JSON.parse(JSON.stringify(DEFAULT_PANELS)) }
      } else {
        this.panels = JSON.parse(JSON.stringify(DEFAULT_PANELS))
      }
    },
    persist() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.panels))
    },
    setEditMode(on) { this.editMode = !!on },
    addPanel(p) {
      const i = 'p' + Math.random().toString(36).slice(2, 8)
      const placed = this._findEmptySpot(p.w || 4, p.h || 7)
      this.panels.push({ i, x: placed.x, y: placed.y, w: p.w || 4, h: p.h || 7, ...p })
      this.persist()
    },
    updatePanel(id, patch) {
      const p = this.panels.find((x) => x.i === id)
      if (!p) return
      Object.assign(p, patch)
      this.persist()
    },
    removePanel(id) {
      this.panels = this.panels.filter((p) => p.i !== id)
      this.persist()
    },
    updateLayout(items) {
      // items from grid-layout-plus: [{ i, x, y, w, h }]
      for (const it of items) {
        const p = this.panels.find((x) => x.i === it.i)
        if (p) {
          p.x = it.x; p.y = it.y; p.w = it.w; p.h = it.h
        }
      }
      this.persist()
    },
    reset() {
      this.panels = JSON.parse(JSON.stringify(DEFAULT_PANELS))
      this.persist()
    },
    _findEmptySpot(w, h) {
      const cols = 12
      let y = 0
      // simple: stack at the bottom
      for (const p of this.panels) {
        const bottom = p.y + p.h
        if (bottom > y) y = bottom
      }
      return { x: 0, y }
    },
  },
})
