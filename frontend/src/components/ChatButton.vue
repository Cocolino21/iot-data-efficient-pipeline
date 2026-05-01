<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const open = ref(false)
const messages = ref([
  { from: 'bot', text: 'Hi! I\'m the Horizon assistant. Ask me about your sensors or how the dashboard works.' },
])
const draft = ref('')

const variant = computed(() => (route.path === '/' ? 'gold' : 'brand'))

function send() {
  const t = draft.value.trim()
  if (!t) return
  messages.value.push({ from: 'me', text: t })
  draft.value = ''
  setTimeout(() => {
    messages.value.push({ from: 'bot', text: 'Got it — this is a mock chat. Real LLM hookup coming later.' })
  }, 600)
}
</script>

<template>
  <div class="cb-root">
    <transition name="cb-pop">
      <div v-if="open" class="cb-panel">
        <header>
          <span>Horizon Assistant</span>
          <button class="x" @click="open = false">✕</button>
        </header>
        <div class="msgs">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.from">
            <div class="bubble">{{ m.text }}</div>
          </div>
        </div>
        <div class="input">
          <input v-model="draft" @keyup.enter="send" placeholder="Type a message..." />
          <button class="send" @click="send">Send</button>
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
  width: 340px;
  height: 440px;
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
  max-width: 78%;
  padding: 9px 13px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.4;
  word-wrap: break-word;
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
.send {
  background: var(--grad-brand);
  border: 0; border-radius: 8px;
  color: white;
  padding: 0 14px;
  font-size: 12px; font-weight: 500;
  cursor: pointer;
}

.cb-pop-enter-from, .cb-pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.96);
}
.cb-pop-enter-active, .cb-pop-leave-active { transition: opacity 0.15s, transform 0.15s; }
</style>
