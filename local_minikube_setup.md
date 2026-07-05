# Local k8s setup

Host (docker compose): `kafka`, `kafka-init`, `emqx-dt`, `influxdb`, `timescaledb`
Cluster (minikube, namespace `iot`): `kafka-ui`, `kafka-exporter`, `postgres-exporter`, `prometheus`, `grafana`, `sink-service`, `traffic-control`, `core-service`

## Fresh start (cold boot)

```powershell
# 1. Host services
docker compose -f infrastructure/docker-compose.yml up -d

# 2. Cluster
minikube start --cpus=4 --memory=6g

# 3. Build local images inside minikube's docker daemon
& minikube docker-env --shell powershell | Invoke-Expression
docker build -t sink-service:dev    sink-service/
docker build -t traffic-control:dev traffic-control/traffic-control/
docker build -t core-service:dev    core-service/

# 4. Create the core-service Secret (Google OIDC client + JWT signing key)
#    See "core-service secrets" below for where the values come from.
kubectl create secret generic core-service-secrets -n iot `
  --from-literal=GOOGLE_CLIENT_ID=<your-google-client-id> `
  --from-literal=GOOGLE_CLIENT_SECRET=<your-google-client-secret> `
  --from-literal=APP_JWT_SECRET=$([Convert]::ToBase64String((1..48 | % { Get-Random -Maximum 256 })))

# 5. Apply all manifests
kubectl apply -f k8s/
kubectl apply -f infrastructure/sink-service-scaler.yaml

# 6. Verify
kubectl get pods -n iot

kubectl rollout restart deployment/sink-service -n iot (BUG)
```

## core-service

REST + Google OIDC backend for the Vue frontend. Talks to TimescaleDB on the host. Auth state lives in an HttpOnly JWT cookie (`auth`), not a server session.

### One-time: Google OIDC client

The GCP console was reshuffled into "Google Auth Platform"; the path below uses the current names. If a label doesn't match, search the console top bar for **"OAuth consent screen"** — that always jumps to the right place.

1. https://console.cloud.google.com → create/select a project.
2. **APIs & Services → OAuth consent screen** (or **Google Auth Platform → Branding**). Run the **Get started** wizard:
   - App name: `Horizon IoT`. Support email + contact email: your gmail.
   - **Audience** = **External** (the only option unless your account sits inside a Google Workspace).
   - Finish. The consent screen lands in **Testing** mode by default.
3. Left nav → **Audience**, scroll to **Test users** → **+ Add users** → add `mihneabostina5@gmail.com` and `mihneabostina8@gmail.com`. Only these emails can complete OIDC while the app stays in Testing.
4. Left nav → **Clients** (or **APIs & Services → Credentials**) → **+ Create client** → **Web application**.
   - **Authorized redirect URIs**: add **only one** — `http://localhost:8083/login/oauth2/code/google`.
   - This single URI covers both local `mvnw` runs AND the cluster pod (via the `kubectl port-forward` below), so it never needs editing when the minikube IP changes.
5. Save **Client ID** and **Client Secret**.

### Reaching core-service from the browser (port-forward, not NodePort)

Google requires the redirect URI to match exactly, but `minikube ip` shifts between `minikube delete` cycles and sometimes between `stop`/`start`. To keep the OIDC config stable, **do not** hit `http://<minikube-ip>:30083` for OAuth. Instead, forward the cluster Service to `localhost`:

```powershell
kubectl port-forward -n iot svc/core-service 8083:8083
# leave running; browser now reaches the pod at http://localhost:8083
```

The Vue dev server (also on `localhost`) talks to that URL via the default `VITE_API_URL=http://localhost:8083` in `frontend/.env.development`. The NodePort 30083 stays available for non-OAuth probes (`curl http://<minikube-ip>:30083/actuator/health`) — just don't use it for the Google round-trip.

If you ever want a stable cluster IP instead, pin it on `minikube start --static-ip 192.168.200.200 ...` and register `http://192.168.200.200:30083/login/oauth2/code/google` as a second redirect URI. Optional; the port-forward path is simpler.

### core-service secrets (in the cluster)

```powershell
# Generate a 48-byte signing key (>= 32 bytes required).
$jwt = [Convert]::ToBase64String((1..48 | % { Get-Random -Maximum 256 }))

kubectl create secret generic core-service-secrets -n iot `
  --from-literal=GOOGLE_CLIENT_ID=<id>.apps.googleusercontent.com `
  --from-literal=GOOGLE_CLIENT_SECRET=<secret> `
  --from-literal=APP_JWT_SECRET=$jwt

# Rotating later: delete + recreate, then bounce the pod.
kubectl delete secret core-service-secrets -n iot
# (re-run create)
kubectl rollout restart deployment/core-service -n iot
```

The Deployment in `k8s/core-service.yaml` mounts this Secret via `envFrom`, so the three keys land as env vars Spring picks up automatically.

### Running locally (no cluster)

Useful for fast iteration without rebuilding the image. Needs the host services (`docker compose up -d`) and Java 25 (`temurin-25`).

```powershell
$env:GOOGLE_CLIENT_ID     = "<id>.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET = "<secret>"
$env:APP_JWT_SECRET       = [Convert]::ToBase64String((1..48 | % { Get-Random -Maximum 256 }))

cd core-service
./mvnw spring-boot:run
# -> http://localhost:8083
# -> seeds two demo users + demo devices on first boot (APP_DEMO_SEED=true)
```

Smoke checks:

```powershell
curl -i http://localhost:8083/api/auth/me                            # 401 before login
start http://localhost:8083/oauth2/authorization/google              # log in via Google
curl -i --cookie "auth=<value-from-devtools>" http://localhost:8083/api/devices
```

### Frontend wiring

The Vue app (`frontend/`) talks to whatever `VITE_API_URL` points at. Defaults to `http://localhost:8083` via `frontend/.env.development`. For the cluster, set it to `http://<minikube-ip>:30083` and rebuild the FE, or front everything with an ingress / port-forward.

`fetch(..., { credentials: 'include' })` is used everywhere, so CORS on the backend (`APP_CORS_ALLOWED_ORIGINS`) must list the FE origin. Default allows `http://localhost:5173`.

## Hot rebuild after editing a service

The Docker CLI must be pointing at minikube's daemon (step 3 above). Once it is, every rebuild is just:

```powershell
docker build -t sink-service:dev sink-service/
kubectl rollout restart deployment/sink-service -n iot
```

Same pattern for `traffic-control`. No `minikube image load`, no tag bumping — the build lands directly inside the cluster's runtime.

To switch the Docker CLI back to your host daemon (e.g. before `docker compose`):

```powershell
& minikube docker-env --shell powershell -u | Invoke-Expression
```

## Browse UIs

```powershell
minikube service kafka-ui        -n iot   # Kafka topics
minikube service prometheus      -n iot   # Metrics
minikube service grafana         -n iot   # Dashboards (admin/admin)
minikube service traffic-control -n iot
```

Static NodePorts (also reachable at `http://<minikube ip>:<port>`):

| Service          | NodePort |
| ---------------- | -------- |
| kafka-ui         | 30080    |
| prometheus       | 30090    |
| grafana          | 30030    |
| traffic-control  | 30082    |
| core-service     | 30083    |

## Shutdown

```powershell
minikube stop                                              # pause cluster (preserves PVCs, images, manifests)
docker compose -f infrastructure/docker-compose.yml down   # stop host services

# Nuclear: also wipe minikube data (PVCs, images, all state)
minikube delete
```
