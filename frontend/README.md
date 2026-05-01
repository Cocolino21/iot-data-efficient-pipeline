# Horizon Frontend

Vue 3 + Vite frontend for the Horizon IoT smart home platform.

## Run

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — landing page is at `/`.

## Pages

| Route | Page |
|---|---|
| `/` | Landing |
| `/login` | Login (unauthenticated, no enforcement) |
| `/dashboard` | Customizable Grafana-like grid |
| `/explore` | Query builder + chart switcher |
| `/devices` | Device list / cards |
| `/devices/:id` | Device detail + sensor table |
| `/settings` | User profile + dashboard reset |

## Mock data → real backend

All data flows through `src/services/api.js`. The mock implementation generates
synthetic but coherent time-series via `src/services/mockGenerator.js` and pushes
live samples every 2s through `api.subscribeLive()`.

To swap to a real backend, replace each function body in `api.js` with `fetch()`
calls. The store layer (`src/stores/*.js`) and components consume only the API
surface, so no UI changes are needed.

Expected endpoints when wiring real data:

```
GET  /api/devices                              -> Device[]
POST /api/devices                              -> Device
GET  /api/devices/:id                          -> Device with sensors
POST /api/devices/:id/sensors                  -> Sensor
PATCH /api/datastreams/:id  { is_active }      -> Datastream
DELETE /api/sensors/:id
GET  /api/datastreams/:id/observations?from&to -> Observation[]
WS   /api/stream                               -> { device_id, datastream_id, type, timestamp, value }
```

## Tech

- Vue 3 + Vite + Pinia + vue-router
- ECharts (via vue-echarts) for line/bar/gauge
- grid-layout-plus for drag-and-drop panel grid
- No CSS framework — design tokens in `src/styles/tokens.css`

## Dashboard layout persistence

Panel positions, sizes, and configurations are saved to `localStorage` under
`horizon.dashboard.v1`. In a real backend, this would map to the `user.layout`
JSON column.
