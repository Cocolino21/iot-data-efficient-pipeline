import { defineStore } from 'pinia'
import { useDevicesStore } from './devices.js'
import { useAuthStore } from './auth.js'
import { api } from '@/services/api.js'
import { chatComplete } from '@/services/llm.js'

const STORAGE_KEY = 'horizon.chat.v1'

function loadFromStorage() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: loadFromStorage(), // [{role: 'user'|'assistant', content, ts}]
    sending: false,
    error: null,
  }),
  actions: {
    async send(text) {
      const trimmed = text.trim()
      if (!trimmed || this.sending) return
      this.error = null
      this.messages.push({ role: 'user', content: trimmed, ts: Date.now() })
      this.persist()
      this.sending = true
      try {
        const ctx = await buildContext()
        const reply = await chatComplete({
          systemPrompt: systemPromptFor(ctx),
          messages: this.messages,
        })
        this.messages.push({
          role: 'assistant',
          content: reply || '(empty response)',
          ts: Date.now(),
        })
      } catch (e) {
        this.error = e.message
        this.messages.push({
          role: 'assistant',
          content: `Error talking to Gemini: ${e.message}`,
          ts: Date.now(),
        })
      } finally {
        this.sending = false
        this.persist()
      }
    },
    clear() {
      this.messages = []
      this.persist()
    },
    persist() {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.messages))
      } catch {
        // localStorage full / disabled — non-fatal
      }
    },
  },
})

async function buildContext() {
  const devices = useDevicesStore()
  const auth = useAuthStore()
  if (!devices.list.length) await devices.refresh()

  const summary = []
  for (const d of devices.list) {
    const sensors = []
    for (const s of d.sensors) {
      let latest = null
      if (s.is_active) {
        try {
          const obs = await api.observations(
            s.id,
            s.type,
            Date.now() - 60 * 60_000,
            Date.now(),
            1,
          )
          const last = obs[obs.length - 1]
          if (last) latest = { ts: new Date(last.timestamp).toISOString(), value: last.value }
        } catch {
          // ignore; assistant just won't have this sensor's latest
        }
      }
      sensors.push({
        name: s.name,
        type: s.type,
        observed: s.observed,
        unit: s.unit,
        active: s.is_active,
        latest,
      })
    }
    summary.push({
      device: d.name,
      status: d.status,
      description: d.description,
      sensors,
    })
  }
  return { user: auth.user?.name, devices: summary }
}

function systemPromptFor(ctx) {
  return `You are the Horizon IoT assistant, embedded in a Vue dashboard that monitors a user's smart-home sensors.
Be concise — 2-4 sentences unless the user asks for detail. Use the JSON context below to answer questions about the user's devices, sensor activity, and the latest readings. If a question goes beyond what's in the context (forecasts, raw history beyond "latest"), say so plainly rather than guessing.

User: ${ctx.user || 'unknown'}.

Devices (JSON):
${JSON.stringify(ctx.devices, null, 2)}
`
}
