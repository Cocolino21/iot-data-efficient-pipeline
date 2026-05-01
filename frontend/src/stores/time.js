import { defineStore } from 'pinia'

export const TIME_PRESETS = [
  { group: 'Recent', items: [
    { id: '5m',  label: 'Last 5 minutes',  ms: 5 * 60_000 },
    { id: '10m', label: 'Last 10 minutes', ms: 10 * 60_000 },
    { id: '1h',  label: 'Last 1 hour',     ms: 3600_000 },
    { id: '12h', label: 'Last 12 hours',   ms: 12 * 3600_000 },
  ] },
  { group: 'Days', items: [
    { id: '1d', label: 'Last 1 day',  ms: 86400_000 },
    { id: '3d', label: 'Last 3 days', ms: 3 * 86400_000 },
    { id: '5d', label: 'Last 5 days', ms: 5 * 86400_000 },
  ] },
  { group: 'Weeks', items: [
    { id: '1w', label: 'Last 1 week',  ms: 7 * 86400_000 },
    { id: '2w', label: 'Last 2 weeks', ms: 14 * 86400_000 },
    { id: '3w', label: 'Last 3 weeks', ms: 21 * 86400_000 },
  ] },
  { group: 'Months', items: [
    { id: '1mo', label: 'Last 1 month',  ms: 30 * 86400_000 },
    { id: '3mo', label: 'Last 3 months', ms: 90 * 86400_000 },
    { id: '6mo', label: 'Last 6 months', ms: 180 * 86400_000 },
  ] },
  { group: 'Years', items: [
    { id: '1y', label: 'Last 1 year',  ms: 365 * 86400_000 },
    { id: '3y', label: 'Last 3 years', ms: 3 * 365 * 86400_000 },
    { id: '5y', label: 'Last 5 years', ms: 5 * 365 * 86400_000 },
  ] },
]

export const useTimeStore = defineStore('time', {
  state: () => ({
    presetId: '12h',
    label: 'Last 12 hours',
    fromMs: null,
    toMs: null,
    refreshTick: 0,
  }),
  getters: {
    range() {
      const to = this.toMs ?? Date.now()
      const ms = this._presetMs() ?? 12 * 3600_000
      const from = this.fromMs ?? to - ms
      return { from, to }
    },
  },
  actions: {
    setPreset(id) {
      for (const g of TIME_PRESETS) {
        const p = g.items.find((x) => x.id === id)
        if (p) {
          this.presetId = id
          this.label = p.label
          this.fromMs = null
          this.toMs = null
          this.refreshTick++
          return
        }
      }
    },
    setCustom(from, to) {
      this.presetId = 'custom'
      this.label = 'Custom range'
      this.fromMs = from
      this.toMs = to
      this.refreshTick++
    },
    refresh() { this.refreshTick++ },
    _presetMs() {
      for (const g of TIME_PRESETS) {
        const p = g.items.find((x) => x.id === this.presetId)
        if (p) return p.ms
      }
      return null
    },
  },
})
