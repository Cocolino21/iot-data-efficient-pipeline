<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/TopBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useDashboardStore } from '@/stores/dashboard.js'

const dashboard = useDashboardStore()
const router = useRouter()
const showResetConfirm = ref(false)

function resetLayout() {
  dashboard.reset()
  showResetConfirm.value = false
}
function signOut() {
  router.push('/')
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
          <div class="avatar-lg">MB</div>
          <div class="info-grid">
            <div><span class="k">Name</span><span class="v">Mihnea Bostina</span></div>
            <div><span class="k">Email</span><span class="v">mihnea@example.com</span></div>
            <div><span class="k">OAuth Provider</span><span class="v provider">Google</span></div>
            <div><span class="k">Member since</span><span class="v">March 2026</span></div>
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
          <button class="btn btn-secondary" @click="signOut">Sign Out</button>
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
</style>
