<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/TopBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useDashboardStore } from '@/stores/dashboard.js'
import { useAuthStore } from '@/stores/auth.js'
import { tcApi } from '@/services/api.js'

const dashboard = useDashboardStore()
const auth = useAuthStore()
const router = useRouter()
const showResetConfirm = ref(false)
const signingOut = ref(false)

// ── Traffic control (control loop) ─────────────────────────
const tcMode = ref('PID')         // committed mode from the backend
const pid = ref(null)             // PidSettings
const hyst = ref(null)            // HysteresisSettings
const tcLoaded = ref(false)
const tcError = ref('')
const tcSaving = ref(false)
const tcStatus = ref('')

async function loadTrafficControl() {
  tcError.value = ''
  try {
    const [ctrl, pidSettings, hystSettings] = await Promise.all([
      tcApi.getController(),
      tcApi.getPid(),
      tcApi.getHysteresis(),
    ])
    tcMode.value = ctrl.mode
    pid.value = pidSettings
    hyst.value = hystSettings
    tcLoaded.value = true
  } catch (e) {
    tcError.value = `Could not reach traffic-control: ${e.message}`
  }
}

async function saveMode() {
  await runTc(() => tcApi.putController({ mode: tcMode.value }), 'Mode updated')
}
async function savePid() {
  await runTc(() => tcApi.putPid(pid.value), 'PID settings saved')
}
async function saveHysteresis() {
  await runTc(() => tcApi.putHysteresis(hyst.value), 'Hysteresis settings saved')
}

async function runTc(fn, okMsg) {
  if (tcSaving.value) return
  tcSaving.value = true
  tcStatus.value = ''
  tcError.value = ''
  try {
    await fn()
    tcStatus.value = okMsg
  } catch (e) {
    tcError.value = e.message
  } finally {
    tcSaving.value = false
  }
}

onMounted(loadTrafficControl)

const initials = computed(() => {
  const name = auth.user?.name || auth.user?.email || ''
  return name
    .split(/[\s@.]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() || '')
    .join('') || '?'
})

function resetLayout() {
  dashboard.reset()
  showResetConfirm.value = false
}
async function signOut() {
  if (signingOut.value) return
  signingOut.value = true
  try {
    await auth.logout()
  } finally {
    signingOut.value = false
    router.push('/login')
  }
}
</script>

<template>
  <div>
    <TopBar title="Settings" />

    <div class="page">
      <!-- User profile -->
      <section class="panel">
        <h3>User Profile</h3>
        <p class="muted">Your account information from OAuth provider (read-only)</p>

        <div class="profile">
          <div class="avatar-lg">{{ initials }}</div>
          <div class="info-grid">
            <div><span class="k">Name</span><span class="v">{{ auth.user?.name || '—' }}</span></div>
            <div><span class="k">Email</span><span class="v">{{ auth.user?.email || '—' }}</span></div>
            <div><span class="k">OAuth Provider</span><span class="v provider">Google</span></div>
            <div><span class="k">User ID</span><span class="v">{{ auth.user?.id || '—' }}</span></div>
          </div>
        </div>
      </section>

      <!-- Dashboard settings -->
      <section class="panel">
        <h3>Dashboard</h3>
        <p class="muted">Manage your dashboard layout and preferences</p>

        <div class="setting">
          <div>
            <div class="setting-title">Reset Dashboard Layout</div>
            <div class="setting-desc">
              Restore your dashboard panels to the default configuration.<br />
              This will remove all custom panels and positions.
            </div>
          </div>
          <button class="btn btn-danger" @click="showResetConfirm = true">Reset to Default</button>
        </div>
      </section>

      <!-- Traffic control -->
      <section class="panel">
        <h3>Traffic Control</h3>
        <p class="muted">Choose the control strategy that adjusts the PIP threshold across devices, and tune its parameters.</p>

        <p v-if="tcError" class="tc-error">{{ tcError }}</p>
        <p v-else-if="!tcLoaded" class="muted">Loading…</p>

        <template v-if="tcLoaded">
          <div class="setting">
            <div>
              <div class="setting-title">Active mode</div>
              <div class="setting-desc">Only one controller runs at a time. "None" disables broadcasting.</div>
            </div>
            <div class="row">
              <select v-model="tcMode">
                <option value="NONE">None</option>
                <option value="PID">PID</option>
                <option value="HYSTERESIS">Hysteresis</option>
              </select>
              <button class="btn btn-secondary" :disabled="tcSaving" @click="saveMode">Apply</button>
            </div>
          </div>

          <!-- PID params -->
          <div v-if="tcMode === 'PID' && pid" class="params">
            <div class="field"><label>Target lag</label><input type="number" v-model.number="pid.targetLag" /></div>
            <div class="field"><label>Kp</label><input type="number" step="any" v-model.number="pid.kp" /></div>
            <div class="field"><label>Ki</label><input type="number" step="any" v-model.number="pid.ki" /></div>
            <div class="field"><label>Kd</label><input type="number" step="any" v-model.number="pid.kd" /></div>
            <div class="field"><label>Integral max</label><input type="number" step="any" v-model.number="pid.integralMax" /></div>
            <div class="field"><label>Output min</label><input type="number" step="any" v-model.number="pid.outputMin" /></div>
            <div class="field"><label>Output max</label><input type="number" step="any" v-model.number="pid.outputMax" /></div>
            <div class="actions"><button class="btn btn-secondary" :disabled="tcSaving" @click="savePid">Save PID</button></div>
          </div>

          <!-- Hysteresis params -->
          <div v-if="tcMode === 'HYSTERESIS' && hyst" class="params">
            <div class="field"><label>Upper lag</label><input type="number" v-model.number="hyst.upperLag" /></div>
            <div class="field"><label>Lower lag</label><input type="number" v-model.number="hyst.lowerLag" /></div>
            <div class="field"><label>Step (%)</label><input type="number" step="any" v-model.number="hyst.step" /></div>
            <div class="field"><label>Gain</label><input type="number" step="any" v-model.number="hyst.gain" /></div>
            <div class="field"><label>Output min</label><input type="number" step="any" v-model.number="hyst.outputMin" /></div>
            <div class="field"><label>Output max</label><input type="number" step="any" v-model.number="hyst.outputMax" /></div>
            <div class="actions"><button class="btn btn-secondary" :disabled="tcSaving" @click="saveHysteresis">Save Hysteresis</button></div>
          </div>

          <p v-if="tcStatus" class="tc-status">{{ tcStatus }}</p>
        </template>
      </section>

      <!-- Appearance (future) -->
      <section class="panel">
        <h3>Appearance <span class="future">Future</span></h3>
        <p class="muted">Theme and visual customization options</p>

        <div class="setting">
          <div>
            <div class="setting-title disabled">Theme</div>
            <div class="setting-desc">Coming soon</div>
          </div>
          <select disabled>
            <option>Dark</option>
          </select>
        </div>
      </section>

      <!-- Sign out -->
      <section class="panel">
        <div class="setting">
          <div>
            <div class="setting-title">Sign Out</div>
            <div class="setting-desc">End your current session and return to the landing page.</div>
          </div>
          <button class="btn btn-secondary" :disabled="signingOut" @click="signOut">
            {{ signingOut ? 'Signing out…' : 'Sign Out' }}
          </button>
        </div>
      </section>
    </div>

    <ConfirmDialog
      :open="showResetConfirm"
      title="Reset dashboard?"
      body="All your panels and positions will be replaced with the default layout."
      confirm-text="Reset"
      danger
      @cancel="showResetConfirm = false"
      @confirm="resetLayout"
    />
  </div>
</template>

<style scoped>
.page { max-width: 720px; padding: 24px 28px; display: flex; flex-direction: column; gap: 22px; }
.panel { padding: 26px 30px; }
.panel h3 { margin: 0 0 6px; font-size: 16px; color: var(--text-primary); font-weight: 600; }
.muted { color: var(--text-faint); font-size: 12px; margin: 0 0 22px; }

.profile { display: flex; gap: 24px; align-items: flex-start; padding-top: 6px; }
.avatar-lg {
  width: 64px; height: 64px;
  border-radius: 50%;
  background: rgba(79,70,229,0.2);
  color: var(--indigo-light);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 600;
  flex-shrink: 0;
}
.info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 36px; flex: 1; }
.info-grid > div { display: flex; flex-direction: column; gap: 4px; }
.info-grid .k { color: var(--text-faint); font-size: 12px; font-weight: 500; }
.info-grid .v { color: var(--text-primary); font-size: 14px; }
.info-grid .v.provider {
  background: var(--bg-elevated);
  border-radius: 6px;
  padding: 4px 10px;
  width: max-content;
  color: var(--text-secondary);
  font-size: 11px;
}

.setting {
  display: flex; align-items: center; justify-content: space-between;
  gap: 20px;
  padding-top: 10px;
}
.setting-title { color: var(--text-primary); font-size: 14px; font-weight: 500; }
.setting-title.disabled { color: var(--text-faintest); }
.setting-desc { color: var(--text-faint); font-size: 12px; margin-top: 6px; line-height: 1.6; }

.future {
  display: inline-block;
  background: rgba(124,58,237,0.2);
  color: var(--violet-light);
  font-size: 10px; font-weight: 500;
  padding: 2px 10px; border-radius: 10px;
  margin-left: 8px; vertical-align: middle;
}

.row { display: flex; align-items: center; gap: 10px; }
.params {
  display: grid; grid-template-columns: 1fr 1fr; gap: 14px 24px;
  margin-top: 18px; padding-top: 18px;
  border-top: 1px solid var(--border, rgba(255,255,255,0.08));
}
.params .field { display: flex; flex-direction: column; gap: 6px; }
.params .field label { color: var(--text-faint); font-size: 12px; font-weight: 500; }
.params .field input {
  background: var(--bg-elevated); color: var(--text-primary);
  border: 1px solid var(--border, rgba(255,255,255,0.1));
  border-radius: 6px; padding: 7px 10px; font-size: 13px;
}
.params .actions { grid-column: 1 / -1; display: flex; justify-content: flex-end; }
.tc-error { color: var(--danger, #f87171); font-size: 12px; margin: 4px 0 0; }
.tc-status { color: var(--text-secondary); font-size: 12px; margin: 14px 0 0; }
</style>
