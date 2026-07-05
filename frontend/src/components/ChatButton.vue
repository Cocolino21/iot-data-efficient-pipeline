<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '@/stores/chat.js'

const route = useRoute()
const chat = useChatStore()

const open = ref(false)
const draft = ref('')
const msgsEl = ref(null)

const variant = computed(() => (route.path === '/' ? 'gold' : 'brand'))

const hasMessages = computed(() => chat.messages.length > 0)

async function scrollToBottom() {
  await nextTick()
  if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight
}

watch(() => chat.messages.length, scrollToBottom)
watch(open, (v) => { if (v) scrollToBottom() })

async function send() {
  const t = draft.value
  if (!t.trim() || chat.sending) return
  draft.value = ''
  await chat.send(t)
}
function clear() {
  chat.clear()
}
function fmt(text) {
  return text
}
</script>

<template>
  <div class="cb-root">
    <transition name="cb-pop">
      <div v-if="open" class="cb-panel">
        <header>
          <span>Horizon Assistant</span>
          <div class="hdr-acts">
            <button v-if="hasMessages" class="hdr-btn" @click="clear" title="Clear conversation">Clear</button>
            <button class="x" @click="open = false">✕</button>
          </div>
        </header>
        <div ref="msgsEl" class="msgs">
          <div v-if="!hasMessages" class="msg bot">
            <div class="bubble">
              Hi! I'm the Horizon assistant. Ask me about your sensors, latest readings, or how the dashboard works.
            </div>
          </div>
          <div v-for="(m, i) in chat.messages" :key="i" class="msg" :class="m.role === 'user' ? 'me' : 'bot'">
            <div class="bubble">{{ fmt(m.content) }}</div>
          </div>
          <div v-if="chat.sending" class="msg bot">
            <div class="bubble typing"><span></span><span></span><span></span></div>
          </div>
        </div>
        <div class="input">
          <input
            v-model="draft"
            @keyup.enter="send"
            :disabled="chat.sending"
            placeholder="Ask about your devices..."
          />
          <button class="send" @click="send" :disabled="chat.sending || !draft.trim()">Send</button>
        </div>
      </div>
    </transition>

    <button class="cb-btn" :class="variant" @click="open = !open">
      <span class="ic">💬</span>
      <span>Chat with us!</span>
    </button>
  </div>
</template>

<style scoped>
.cb-root {
  position: fixed;
  right: 24px; bottom: 24px;
  z-index: 90;
  display: flex; flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.cb-btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px;
  border: 0; border-radius: 999px;
  font-size: 13px; font-weight: 600;
  color: white;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
  transition: filter 0.12s, transform 0.12s;
}
.cb-btn:hover { filter: brightness(1.1); transform: translateY(-1px); }
.cb-btn.brand { background: var(--grad-brand); }
.cb-btn.gold {
  background: linear-gradient(90deg, #84cc16 0%, #facc15 100%);
  color: #1a1a2e;
}
.ic { font-size: 14px; }

.cb-panel {
  width: 360px;
  height: 480px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  display: flex; flex-direction: column;
  overflow: hidden;
}
header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  font-size: 13px; font-weight: 600;
  border-bottom: 1px solid var(--border);
}
.hdr-acts { display: flex; align-items: center; gap: 8px; }
.hdr-btn {
  background: none; border: 0;
  color: var(--text-faint);
  font-size: 11px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.hdr-btn:hover { background: var(--bg-surface); color: var(--text-secondary); }
.x {
  background: none; border: 0;
  color: var(--text-faint);
  font-size: 14px; cursor: pointer;
}
.msgs {
  flex: 1; overflow: auto;
  padding: 14px;
  display: flex; flex-direction: column;
  gap: 10px;
}
.msg { display: flex; }
.msg.me { justify-content: flex-end; }
.bubble {
  max-width: 82%;
  padding: 9px 13px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.45;
  word-wrap: break-word;
  white-space: pre-wrap;
}
.msg.bot .bubble {
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border-bottom-left-radius: 4px;
}
.msg.me .bubble {
  background: var(--indigo);
  color: white;
  border-bottom-right-radius: 4px;
}
.bubble.typing { display: inline-flex; gap: 4px; padding: 12px 14px; }
.bubble.typing span {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--text-faint);
  animation: bounce 1.2s infinite;
}
.bubble.typing span:nth-child(2) { animation-delay: 0.15s; }
.bubble.typing span:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}
.input {
  display: flex; gap: 8px;
  padding: 10px;
  border-top: 1px solid var(--border);
  background: var(--bg-surface);
}
.input input {
  flex: 1;
  background: var(--bg-elevated);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  padding: 8px 12px;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}
.input input:focus { border-color: var(--indigo-light); }
.input input:disabled { opacity: 0.6; cursor: not-allowed; }
.send {
  background: var(--grad-brand);
  border: 0; border-radius: 8px;
  color: white;
  padding: 0 14px;
  font-size: 12px; font-weight: 500;
  cursor: pointer;
}
.send:disabled { opacity: 0.5; cursor: not-allowed; }

.cb-pop-enter-from, .cb-pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}
.cb-pop-enter-active, .cb-pop-leave-active { transition: opacity 0.15s, transform 0.15s; }
</style>
