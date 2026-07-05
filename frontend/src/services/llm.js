// Browser-direct Gemini client. NOT safe to deploy publicly — the key lives
// in the bundle. Fine for the localhost demo. Add a backend proxy later if
// the FE is ever served from somewhere reachable.
//
// Docs: https://ai.google.dev/api/generate-content

const API_KEY = import.meta.env.VITE_LLM_API_KEY
const MODEL = import.meta.env.VITE_LLM_MODEL || 'gemini-2.5-flash'

const ENDPOINT = (model) =>
  `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`

export async function chatComplete({ systemPrompt, messages }) {
  if (!API_KEY) {
    throw new Error('VITE_LLM_API_KEY is not set; put it in frontend/.env.local')
  }

  const body = {
    systemInstruction: { parts: [{ text: systemPrompt }] },
    contents: messages.map((m) => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }],
    })),
    generationConfig: {
      temperature: 0.5,
      maxOutputTokens: 1024,
    },
  }

  const res = await fetch(`${ENDPOINT(MODEL)}?key=${API_KEY}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`Gemini ${res.status}: ${text.slice(0, 400)}`)
  }

  const data = await res.json()
  const parts = data.candidates?.[0]?.content?.parts || []
  return parts.map((p) => p.text || '').join('').trim()
}
