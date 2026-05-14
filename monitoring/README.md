# Banka 2 — Monitoring stack (MLA bonus)

**Bonus aktivnost (KT3 koeficijent +0.033):** Prometheus + Grafana + AlertManager + Discord webhook bridge. Potpuno odvojen Docker compose od `Banka-2-Backend/docker-compose.yml`.

## Servisi

Compose project name: **`banka-2-monitoring`** (matchuje konvenciju `banka-2-backend`, `banka-2-frontend`, `banka-2-tools`).

| Container | Port (host → kontejner) | URL | Login |
|-----------|-------------------------|-----|-------|
| `banka2_prometheus` | 9090 → 9090 | <http://localhost:9090> | – |
| `banka2_grafana` | 3001 → 3000 | <http://localhost:3001> | admin / admin (prvi login menja password) |
| `banka2_alertmanager` | 9093 → 9093 | <http://localhost:9093> | – |
| `banka2_alert_router` | 8093 → 8093 | <http://localhost:8093/health> | – (Discord webhook bridge) |

## Quick start

**Preduslov**: BE stack mora biti up (compose mreza `banka-2-backend_default` se referencira kao `external: true`).

```bash
# Iz Banka-2-Backend foldera prvo:
docker compose up -d
docker logs banka2_seed   # cekati "Seed uspesno ubasen!"

# Onda iz monitoring/ foldera:
cd monitoring
docker compose up -d
```

To je sve. Posle ~30s sve servise su healthy. Otvori Grafana na <http://localhost:3001> (admin/admin).

## Arhitektura

```
BE expose-uje  →  Prometheus skrejpuje  →  AlertManager evaluira  →  alert-router  →  Discord webhook
/actuator/         banka-2-monitoring-     pravila iz                 (Python Flask)
prometheus         prometheus              alertmanager.yml
                            ↑
                            └──── Grafana cita iz Prometheus-a (datasource provisioning)
```

### Tok podataka

1. **BE expose** `/actuator/prometheus` (Spring Actuator + Micrometer Prometheus registry). Endpoint dostupan bez auth-a (whitelist u `GlobalSecurityConfig`).
2. **Prometheus** skrejpuje BE svakih `scrape_interval` (default 15s) i cuva metrike u TSDB sa retencijom 30 dana.
3. **Prometheus alert rules** (u `prometheus/alert-rules.yml`) evaluiraju metrike i kreiraju alerts. Ako uslov istina za `for` interval, alert se salje na AlertManager.
4. **AlertManager** grupie + deduplifikuje + rute alerts u definisani receiver (`alertmanager.yml` `webhook_configs`).
5. **alert-router** (Python Flask) prima JSON od AlertManager-a i prevodi u Discord embed format, pa POST-uje na `DISCORD_WEBHOOK_URL` env var.
6. **Grafana** cita iz Prometheus datasource-a (provisioning u `grafana/provisioning/datasources/prometheus.yml`) + autoload-uje dashboard-ove iz `grafana/dashboards/`.

## Direktorijumska struktura

```text
monitoring/
├── docker-compose.yml                     # 4 kontejnera + 3 volumes
├── prometheus/
│   ├── prometheus.yml                     # scrape config (banka2_backend:8080/actuator/prometheus)
│   └── alert-rules.yml                    # PromQL alert pravila
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/prometheus.yml     # auto-add Prometheus DS
│   │   └── dashboards/dashboards.yml      # auto-load JSON dashboard-e
│   └── dashboards/                        # JSON dashboard exports (Banka2 BE overview, JVM, ...)
└── alertmanager/
    └── alertmanager.yml                   # routing + receivers + Discord webhook
```

## Konfiguracija

### Discord webhook

Postavi env var pre `docker compose up -d`:

```bash
export DISCORD_WEBHOOK_URL="https://discord.com/api/webhooks/123456/abc..."
docker compose up -d
```

Ili u `.env` fajlu pored `docker-compose.yml`:

```env
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/123456/abc...
```

Bez webhook-a, alert-router je no-op (alerts se ignorisu, log-uje warning).

### Grafana admin password

Default `admin/admin`. Override:

```env
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=securepass
```

### Prometheus retention

Default 30 dana. Promena u `docker-compose.yml`:

```yaml
command:
  - '--storage.tsdb.retention.time=30d'   # 7d / 90d / 1y
```

## Scrape targets

`prometheus/prometheus.yml` skrejpuje:

- **`banka2_backend:8080/actuator/prometheus`** — Spring Boot metrike (HTTP request latency, JVM heap, GC, Hikari connection pool, custom Micrometer counter-i)

Cilj target je dostupan kroz interno docker network ime `banka2_backend` (oba stack-a su u istoj `banka-2-backend_default` mrezi).

## Smoke testovi

```bash
# 1. Prometheus health
curl http://localhost:9090/-/healthy
# → "Prometheus Server is Healthy."

# 2. Prometheus targets (BE actuator mora biti UP)
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {scrapeUrl, health}'
# → {"scrapeUrl": "http://banka2_backend:8080/actuator/prometheus", "health": "up"}

# 3. Grafana health
curl http://localhost:3001/api/health
# → {"database": "ok", "version": "11.6.0"}

# 4. AlertManager health
curl http://localhost:9093/-/healthy

# 5. alert-router health
curl http://localhost:8093/health

# 6. End-to-end test: pokreni BE alarm, gledaj Discord
# (zavisi od alert-rules.yml — npr. ako BE ima alarm "InstanceDown", ugasi BE i sacekaj 1min)
```

## Bonus aktivnost — koeficijent

Ovo je deo MLA (Modeliranje, Logovanje, Alarmiranje) bonus aktivnosti za **+0.033 koeficijent na KT3 ocenu**. Ostali bonusi (read replike, particionisanje, GKE deploy) su odvojeni — vidi `Banka-2-Backend/README.md` za pun spisak.

## Reference

- Prometheus dokumentacija: <https://prometheus.io/docs/>
- Grafana dokumentacija: <https://grafana.com/docs/grafana/latest/>
- Spring Boot Actuator + Micrometer Prometheus: <https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.export.prometheus>
- AlertManager: <https://prometheus.io/docs/alerting/latest/alertmanager/>
- Discord webhook format: <https://discord.com/developers/docs/resources/webhook>
