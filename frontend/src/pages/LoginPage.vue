<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function fakeSignIn() {
  router.push('/dashboard')
}
</script>

<template>
  <div class="page">
    <div class="bg"></div>
    <div class="card">
      <div class="logo">Horizon</div>
      <div class="tagline">IoT Smart Home Monitoring</div>
      <div class="divider"></div>
      <h2>Welcome back</h2>
      <p>Sign in to access your smart home dashboard</p>

      <button class="provider google" @click="fakeSignIn">
        <span class="icon-g">G</span>
        <span>Continue with Google</span>
      </button>

      <div class="or">
        <span></span><label>OR</label><span></span>
      </div>

      <button class="provider github" @click="fakeSignIn">
        <span class="icon-h">⌥</span>
        <span>Continue with GitHub</span>
      </button>

      <div class="terms">By signing in, you agree to our Terms of Service<br />and Privacy Policy.</div>
    </div>
    <RouterLink to="/" class="back">Back to home</RouterLink>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(135deg, var(--bg-base) 0%, var(--bg-panel-2) 100%);
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  padding: 40px;
  position: relative;
  overflow: hidden;
}
.bg {
  position: absolute; inset: 0;
  background-image:
    radial-gradient(circle at 14% 22%, rgba(79,70,229,0.08) 0, transparent 30%),
    radial-gradient(circle at 86% 78%, rgba(124,58,237,0.08) 0, transparent 35%);
  pointer-events: none;
}
.card {
  position: relative;
  width: 480px; max-width: 92vw;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 24px;
  padding: 50px 60px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
  text-align: center;
}
.logo { font-size: 28px; font-weight: 700; color: var(--text-strong); }
.tagline { color: var(--text-faint); font-size: 14px; margin-top: 4px; }
.divider { height: 1px; background: var(--border); margin: 26px 0; }
h2 { font-size: 22px; font-weight: 600; color: var(--text-primary); margin: 0 0 8px; }
p { color: var(--text-muted); font-size: 14px; margin: 0 0 32px; }

.provider {
  display: flex; align-items: center; justify-content: center;
  gap: 12px;
  width: 100%; height: 52px;
  border-radius: 12px;
  font-size: 15px; font-weight: 500;
  cursor: pointer;
  transition: filter 0.12s, background 0.12s;
}
.provider.google {
  background: white; color: var(--bg-panel-2);
  border: 0;
}
.provider.google:hover { filter: brightness(0.95); }
.provider.github {
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1px solid var(--border-strong);
}
.provider.github:hover { background: var(--bg-elevated); }
.icon-g {
  width: 24px; height: 24px; border-radius: 50%;
  background: #EA4335; color: white;
  display: inline-flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px;
}
.icon-h {
  width: 24px; height: 24px; border-radius: 50%;
  background: white; color: var(--bg-surface);
  display: inline-flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px;
}

.or { display: flex; align-items: center; gap: 14px; margin: 18px 0; color: var(--text-faint); font-size: 12px; font-weight: 500; }
.or span { flex: 1; height: 1px; background: var(--border); }

.terms { font-size: 11px; color: var(--text-faintest); margin-top: 32px; line-height: 1.6; }

.back {
  position: relative;
  margin-top: 32px;
  color: var(--indigo-light);
  font-size: 14px; font-weight: 500;
  text-decoration: underline;
}
</style>
