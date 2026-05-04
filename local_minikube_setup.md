# Local k8s setup

Host (docker compose): `kafka`, `kafka-init`, `emqx-dt`, `influxdb`, `timescaledb`
Cluster (minikube, namespace `iot`): `kafka-ui`, `kafka-exporter`, `postgres-exporter`, `prometheus`, `grafana`, `sink-service`, `traffic-control`

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

# 4. Apply all manifests
kubectl apply -f k8s/

# 5. Verify
kubectl get pods -n iot

kubectl rollout restart deployment/sink-service -n iot (BUG)
```

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

## Shutdown

```powershell
minikube stop                                              # pause cluster (preserves PVCs, images, manifests)
docker compose -f infrastructure/docker-compose.yml down   # stop host services

# Nuclear: also wipe minikube data (PVCs, images, all state)
minikube delete
```
