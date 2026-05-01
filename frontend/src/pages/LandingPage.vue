<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="landing">
    <header class="nav">
      <div class="logo">Horizon</div>
      <nav class="links">
        <a href="#features">Features</a>
        <a href="#about">About</a>
        <a href="#contact">Contact</a>
      </nav>
      <div class="cta">
        <RouterLink to="/login" class="btn-outline">Sign In</RouterLink>
        <RouterLink to="/dashboard" class="btn-grad">Get Started</RouterLink>
      </div>
    </header>

    <section class="hero">
      <div class="hero-bg"></div>
      <div class="badge">IoT SMART HOME PLATFORM</div>
      <h1>Monitor your smart home,<br /><span class="accent">beautifully.</span></h1>
      <p class="lede">
        Real-time sensor monitoring with customizable Grafana-like dashboards.<br />
        Explore, visualize, and control your IoT devices from one place.
      </p>
      <div class="hero-cta">
        <RouterLink to="/dashboard" class="btn-grad lg">Get Started</RouterLink>
        <a href="#features" class="btn-outline lg">Learn More</a>
      </div>
    </section>

    <section class="preview">
      <div class="preview-card">
        <div class="preview-bar">
          <div class="preview-time">Last 12 hours</div>
          <div class="preview-actions">
            <span class="pa-pill">Refresh</span>
            <span class="pa-pill outline">Add Panel</span>
          </div>
        </div>
        <div class="preview-grid">
          <div class="preview-side">
            <div class="ps-item active">Dashboard</div>
            <div class="ps-item">Explore</div>
            <div class="ps-item">Devices</div>
          </div>
          <div class="preview-cells">
            <div class="pc tall">
              <div class="pc-title">Living Room Temperature</div>
              <div class="pc-sub">Temperature sensor - Celsius</div>
              <svg class="pc-chart" viewBox="0 0 460 120" preserveAspectRatio="none">
                <polyline fill="none" stroke="#818CF8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"
                  points="20,90 70,80 120,85 170,60 220,65 270,40 320,55 370,30 420,50 450,25" />
                <polyline fill="none" stroke="#4F46E5" stroke-width="2" stroke-linecap="round" stroke-dasharray="5 4" opacity="0.5"
                  points="20,100 70,90 120,95 170,75 220,80 270,55 320,70 370,45 420,65 450,40" />
              </svg>
            </div>
            <div class="pc">
              <div class="pc-title">Current Humidity</div>
              <div class="pc-sub">Humidity sensor - %RH</div>
              <div class="pc-gauge">
                <svg viewBox="0 0 200 120">
                  <path d="M 30 110 A 70 70 0 1 1 170 110" stroke="#252536" stroke-width="12" fill="none" stroke-linecap="round" />
                  <path d="M 30 110 A 70 70 0 1 1 130 50" stroke="#818CF8" stroke-width="12" fill="none" stroke-linecap="round" />
                  <text x="100" y="92" text-anchor="middle" fill="#fff" font-size="28" font-weight="700">62%</text>
                  <text x="100" y="108" text-anchor="middle" fill="#64748B" font-size="10">Relative Humidity</text>
                </svg>
              </div>
            </div>
            <div class="pc">
              <div class="pc-title">Energy Usage (kWh)</div>
              <div class="pc-sub">Power meter - Hourly</div>
              <div class="pc-bars">
                <span style="height: 56%"></span><span style="height: 76%"></span><span style="height: 44%"></span>
                <span class="hi" style="height: 86%"></span><span style="height: 64%"></span><span style="height: 50%"></span>
                <span style="height: 70%"></span><span style="height: 38%"></span><span style="height: 60%"></span>
              </div>
            </div>
            <div class="pc stat">
              <div class="pc-title">Air Quality</div>
              <div class="pc-sub">AQI Index</div>
              <div class="big" style="color: #34D399">42</div>
              <div class="lbl" style="color: #34D399">Good</div>
            </div>
            <div class="pc stat">
              <div class="pc-title">CO2 Level</div>
              <div class="pc-sub">ppm</div>
              <div class="big" style="color: #FBBF24">847</div>
              <div class="lbl" style="color: #FBBF24">Moderate</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section id="features" class="features">
      <div class="features-head">
        <div class="kicker">FEATURES</div>
        <h2>Everything you need to monitor your home</h2>
        <p>Powerful tools for real-time monitoring, visualization, and control.</p>
      </div>
      <div class="feature-cards">
        <div class="fc">
          <div class="fc-bar" style="background:#4F46E5"></div>
          <div class="fc-icon" style="background: rgba(79,70,229,0.15); color:#818CF8">●</div>
          <div class="fc-title">Real-Time Monitoring</div>
          <div class="fc-desc">Sensor data streamed via MQTT with frequent updates on your customizable dashboard.</div>
        </div>
        <div class="fc">
          <div class="fc-bar" style="background:#7C3AED"></div>
          <div class="fc-icon" style="background: rgba(124,58,237,0.15); color:#A78BFA">■</div>
          <div class="fc-title">Custom Dashboards</div>
          <div class="fc-desc">Grafana-like drag-and-drop panels with 5 chart types: line, bar, gauge, single stat, and table.</div>
        </div>
        <div class="fc">
          <div class="fc-bar" style="background:#06B6D4"></div>
          <div class="fc-icon" style="background: rgba(6,182,212,0.15); color:#22D3EE">▲</div>
          <div class="fc-title">Multi-Device Support</div>
          <div class="fc-desc">Manage multiple IoT devices and sensors from a single unified control panel.</div>
        </div>
        <div class="fc">
          <div class="fc-bar" style="background:#F59E0B"></div>
          <div class="fc-icon" style="background: rgba(245,158,11,0.15); color:#FBBF24">★</div>
          <div class="fc-title">Historical Exploration</div>
          <div class="fc-desc">Browse up to 5 years of data with automatic downsampling and flexible time ranges.</div>
        </div>
      </div>
    </section>

    <section class="stats">
      <div class="stat"><div class="num">5</div><div class="lab">Chart Types</div></div>
      <div class="div"></div>
      <div class="stat"><div class="num">15+</div><div class="lab">Time Range Presets</div></div>
      <div class="div"></div>
      <div class="stat"><div class="num">Real-Time</div><div class="lab">MQTT Data Streaming</div></div>
      <div class="div"></div>
      <div class="stat"><div class="num">5 Years</div><div class="lab">Historical Data Retention</div></div>
    </section>

    <section class="cta-section" id="about">
      <div class="cta-card">
        <h2>Ready to monitor your smart home?</h2>
        <p>Get started in seconds. No complex setup required.</p>
        <RouterLink to="/dashboard" class="btn-grad lg">Start Monitoring</RouterLink>
      </div>
    </section>

    <footer class="footer" id="contact">
      <div class="foot-grid">
        <div class="foot-brand">
          <div class="logo">Horizon</div>
          <div class="muted">IoT Smart Home Monitoring Platform</div>
        </div>
        <div>
          <div class="kicker small">PRODUCT</div>
          <a>Dashboard</a><a>Explore</a><a>Devices</a>
        </div>
        <div>
          <div class="kicker small">RESOURCES</div>
          <a>Documentation</a><a>API Reference</a>
        </div>
        <div>
          <div class="kicker small">COMPANY</div>
          <a>About</a><a>Contact</a>
        </div>
      </div>
      <div class="foot-bottom">
        <span>2026 Horizon. All rights reserved.</span>
        <span>Privacy Policy | Terms of Service</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.landing { background: var(--bg-base); color: var(--text-primary); }

.nav {
  position: sticky; top: 0; z-index: 5;
  display: flex; align-items: center; gap: 24px;
  padding: 18px 80px;
  background: rgba(15, 15, 26, 0.95);
  backdrop-filter: blur(8px);
}
.nav .logo { font-size: 22px; font-weight: 700; color: var(--text-strong); letter-spacing: 0.3px; }
.nav .links { display: flex; gap: 32px; flex: 1; justify-content: center; }
.nav .links a { color: var(--text-muted); font-size: 14px; font-weight: 500; }
.nav .links a:hover { color: var(--indigo-light); }
.nav .cta { display: flex; gap: 12px; }

.btn-outline {
  display: inline-flex; align-items: center;
  padding: 10px 22px;
  border: 1.5px solid var(--indigo);
  border-radius: 8px;
  color: var(--indigo);
  font-size: 14px; font-weight: 500;
  transition: background 0.12s;
}
.btn-outline:hover { background: rgba(79,70,229,0.1); }
.btn-outline.lg { padding: 14px 32px; font-size: 16px; border-color: #475569; color: var(--text-secondary); }
.btn-outline.lg:hover { background: rgba(255,255,255,0.04); border-color: var(--text-faint); }

.btn-grad {
  display: inline-flex; align-items: center;
  padding: 10px 24px;
  background: var(--grad-brand);
  border-radius: 8px;
  color: white;
  font-size: 14px; font-weight: 600;
  transition: filter 0.12s;
}
.btn-grad:hover { filter: brightness(1.1); }
.btn-grad.lg { padding: 14px 36px; font-size: 16px; border-radius: 10px; }

.hero {
  position: relative;
  padding: 96px 80px 80px;
  text-align: center;
  background: var(--grad-hero);
  overflow: hidden;
}
.hero-bg {
  position: absolute; inset: 0;
  background-image:
    radial-gradient(circle at 12% 20%, rgba(79,70,229,0.08) 0, transparent 30%),
    radial-gradient(circle at 88% 70%, rgba(124,58,237,0.08) 0, transparent 35%);
}
.badge {
  position: relative;
  display: inline-block;
  padding: 8px 28px;
  background: rgba(79,70,229,0.15);
  color: var(--indigo-light);
  font-size: 12px; font-weight: 600;
  letter-spacing: 1px;
  border-radius: 20px;
  margin-bottom: 24px;
}
.hero h1 {
  position: relative;
  font-size: 52px; font-weight: 800;
  line-height: 1.15; letter-spacing: -1px;
  color: white;
  margin: 0 0 24px;
}
.hero h1 .accent { color: var(--indigo-light); }
.lede { position: relative; font-size: 18px; color: var(--text-muted); max-width: 720px; margin: 0 auto 36px; line-height: 1.5; }
.hero-cta { position: relative; display: flex; gap: 16px; justify-content: center; }

.preview { padding: 0 80px 96px; margin-top: -120px; }
.preview-card {
  max-width: 1200px; margin: 0 auto;
  background: var(--bg-panel);
  border-radius: 16px;
  padding: 0;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
  overflow: hidden;
}
.preview-bar {
  height: 48px;
  background: var(--bg-elevated);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 22px;
  font-size: 13px; color: var(--text-muted);
}
.preview-actions { display: flex; gap: 8px; }
.pa-pill { background: var(--indigo); color: white; font-size: 11px; font-weight: 500; padding: 5px 14px; border-radius: 6px; }
.pa-pill.outline { background: transparent; color: var(--text-muted); border: 1px solid #475569; }
.preview-grid { display: grid; grid-template-columns: 200px 1fr; min-height: 420px; }
.preview-side { background: var(--bg-surface); padding: 16px; }
.ps-item { padding: 10px 14px; border-radius: 8px; color: var(--text-faint); font-size: 13px; margin-bottom: 4px; }
.ps-item.active { background: rgba(79,70,229,0.15); color: var(--indigo-light); font-weight: 500; }

.preview-cells {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 14px; padding: 22px;
}
.pc {
  background: var(--bg-panel-2);
  border-radius: 12px;
  padding: 16px;
  display: flex; flex-direction: column;
}
.pc.tall { grid-row: span 1; grid-column: 1 / 2; }
.pc.stat { align-items: center; justify-content: center; }
.pc-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.pc-sub { font-size: 11px; color: var(--text-faint); margin-top: 4px; }
.pc-chart { width: 100%; height: 120px; margin-top: auto; }
.pc-gauge { flex: 1; display: flex; align-items: center; justify-content: center; }
.pc-gauge svg { width: 100%; height: 100%; }
.pc-bars { display: flex; gap: 6px; align-items: flex-end; height: 90px; margin-top: auto; }
.pc-bars span { flex: 1; background: rgba(79,70,229,0.7); border-radius: 4px 4px 0 0; }
.pc-bars .hi { background: var(--indigo-light); }
.big { font-size: 48px; font-weight: 800; }
.lbl { font-size: 13px; font-weight: 500; }

.features { padding: 96px 80px; background: var(--bg-base); }
.features-head { text-align: center; margin-bottom: 52px; }
.kicker { color: var(--indigo-light); font-size: 14px; font-weight: 600; letter-spacing: 2px; margin-bottom: 8px; }
.kicker.small { font-size: 12px; letter-spacing: 1px; color: var(--text-muted); }
.features-head h2 { font-size: 36px; font-weight: 700; color: var(--text-strong); margin: 0 0 12px; }
.features-head p { color: var(--text-muted); font-size: 16px; }
.feature-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; max-width: 1280px; margin: 0 auto; }
.fc {
  position: relative;
  background: var(--bg-panel);
  border-radius: 16px;
  padding: 36px 28px 28px;
  overflow: hidden;
  box-shadow: var(--shadow-panel);
}
.fc-bar { position: absolute; top: 0; left: 0; right: 0; height: 4px; }
.fc-icon { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 22px; margin-bottom: 24px; }
.fc-title { font-size: 18px; font-weight: 600; color: var(--text-primary); margin-bottom: 10px; }
.fc-desc { font-size: 13px; color: var(--text-muted); line-height: 1.55; }

.stats {
  background: var(--bg-panel-2);
  display: flex; align-items: center; justify-content: space-around;
  padding: 50px 80px;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}
.stats .stat { text-align: center; }
.stats .num { font-size: 40px; font-weight: 700; color: var(--text-strong); }
.stats .lab { color: var(--text-muted); font-size: 14px; margin-top: 4px; }
.stats .div { width: 1px; height: 80px; background: var(--border); }

.cta-section { padding: 80px 80px; background: var(--bg-base); }
.cta-card {
  max-width: 1120px; margin: 0 auto;
  text-align: center;
  background: linear-gradient(180deg, rgba(79,70,229,0.08), transparent), var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 24px;
  padding: 60px 40px;
}
.cta-card h2 { font-size: 32px; font-weight: 700; color: var(--text-strong); margin: 0 0 12px; }
.cta-card p { color: var(--text-muted); font-size: 16px; margin: 0 0 28px; }

.footer { background: var(--bg-deeper); padding: 50px 80px 30px; }
.foot-grid { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 40px; margin-bottom: 50px; }
.foot-grid a { display: block; color: var(--text-faint); font-size: 13px; margin-top: 8px; cursor: pointer; }
.foot-grid a:hover { color: var(--text-secondary); }
.foot-brand .logo { font-size: 20px; font-weight: 700; color: var(--text-strong); margin-bottom: 8px; }
.foot-brand .muted { color: var(--text-faint); font-size: 13px; line-height: 1.5; }
.foot-bottom {
  display: flex; justify-content: space-between;
  padding-top: 24px;
  border-top: 1px solid var(--bg-panel);
  color: var(--text-faintest);
  font-size: 12px;
}

@media (max-width: 1024px) {
  .nav, .hero, .preview, .features, .stats, .cta-section, .footer { padding-left: 32px; padding-right: 32px; }
  .feature-cards { grid-template-columns: repeat(2, 1fr); }
  .stats { flex-wrap: wrap; gap: 32px; }
  .stats .div { display: none; }
  .preview-grid { grid-template-columns: 1fr; }
  .preview-side { display: flex; gap: 8px; }
}
</style>
