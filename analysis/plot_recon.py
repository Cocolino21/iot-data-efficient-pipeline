# Overlay multiple runs (one CSV per threshold). Paste into a Colab cell.
# Upload as many CSVs as you like; each is drawn on both panels with its point count.

import glob
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

# --- pick up the files ---------------------------------------------------
try:
    from google.colab import files
    uploaded = files.upload()          # select all your csvs at once
    paths = list(uploaded.keys())
except Exception:
    paths = sorted(glob.glob("*.csv")) # local fallback: every csv in the folder

def load(p):
    d = pd.read_csv(p)
    d.columns = [c.strip() for c in d.columns]
    if "t" in d.columns:                       # epoch-seconds export
        d["dt"] = pd.to_datetime(d["t"], unit="s", utc=True)
    else:                                      # raw datetime export
        d["dt"] = pd.to_datetime(d["timestamp"], utc=True)
    return d[["dt", "value"]].sort_values("dt")

runs = {p: load(p) for p in paths}

# --- trim every run to the window they all share -------------------------
lo = max(r.dt.min() for r in runs.values())
hi = min(r.dt.max() for r in runs.values())
runs = {p: r[(r.dt >= lo) & (r.dt <= hi)] for p, r in runs.items()}

for p, r in runs.items():
    print(f"{p}: {len(r)} points")

# --- plot: per-second (top) + hourly average (bottom) --------------------
colors = ["#636EFA", "#EF553B", "#00CC96", "#AB63FA", "#FFA15A",
          "#19D3F3", "#FF6692", "#B6E880", "#FF97FF", "#FECB52"]

fig = make_subplots(rows=2, cols=1, shared_xaxes=True, vertical_spacing=0.08,
                    row_heights=[0.6, 0.4],
                    subplot_titles=("per-second", "hourly average"))

# densest run first so it sits at the back / top of the legend
for i, (p, r) in enumerate(sorted(runs.items(), key=lambda kv: -len(kv[1]))):
    c = colors[i % len(colors)]
    label = p.replace(".csv", "")
    fig.add_trace(go.Scatter(x=r.dt, y=r.value, mode="lines",
                  name=f"{label} ({len(r)} pts)", legendgroup=label,
                  line=dict(color=c, width=1)), row=1, col=1)
    h = r.set_index("dt")["value"].resample("1h").mean()
    fig.add_trace(go.Scatter(x=h.index, y=h.values, mode="lines+markers",
                  name=label, legendgroup=label, showlegend=False,
                  line=dict(color=c, width=2)), row=2, col=1)

fig.update_layout(height=820, hovermode="x unified")
fig.update_yaxes(title_text="active power (W)", row=1, col=1)
fig.update_yaxes(title_text="W (hourly avg)", row=2, col=1)
fig.show()
