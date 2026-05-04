"""
Alert Router — bridge izmedju Prometheus AlertManager-a i Discord webhook-a.

AlertManager ne podrzava native Discord receiver. Generic webhook payload
salje na ovaj servis, koji ga prevodi u Discord embed format (sa bojom po
severity, struktuiranim poljima, mention-om za critical alert-e).

Endpoint-i:
    POST /alert     — AlertManager webhook payload primalac
    GET  /health    — health check (koristi se u Docker healthcheck-u)
    GET  /metrics   — Prometheus metrike (broj prosledjenih alert-a po severity)
"""

from __future__ import annotations

import logging
import os
from collections import defaultdict
from datetime import datetime
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Request, Response
from pydantic import BaseModel, Field

# ─── Logging ─────────────────────────────────────────────────────────────

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger("alert-router")

# ─── Konfiguracija ───────────────────────────────────────────────────────

DISCORD_WEBHOOK_URL = os.getenv("DISCORD_WEBHOOK_URL", "").strip()
SERVICE_NAME = "banka2-alert-router"
SERVICE_VERSION = "1.0.0"

# Mapa AlertManager severity → Discord embed boja (decimalni RGB).
SEVERITY_COLORS = {
    "critical": 0xE74C3C,  # crvena
    "warning":  0xF1C40F,  # zuta
    "info":     0x3498DB,  # plava
    "resolved": 0x2ECC71,  # zelena (kad je alert resolved)
}

# Ikonice za vizuelnu prepoznatljivost u Discord channel-u.
SEVERITY_EMOJI = {
    "critical": "🚨",
    "warning":  "⚠️",
    "info":     "ℹ️",
    "resolved": "✅",
}

# In-memory counter za Prometheus metrike. Reset-uje se na restart, sto je
# u redu jer Prometheus i dalje sam vodi total counter (rate kalkulisemo
# preko vremena, ne kroz nas).
metrics_counter: dict[str, int] = defaultdict(int)


# ─── Pydantic modeli ─────────────────────────────────────────────────────

class AlertManagerLabels(BaseModel):
    """Labels iz AlertManager payload-a (alertname + custom severity/team/cluster)."""
    alertname: str
    severity: str = "info"
    job: str | None = None
    instance: str | None = None
    team: str | None = None
    cluster: str | None = None


class AlertManagerAnnotations(BaseModel):
    summary: str = ""
    description: str = ""


class AlertManagerAlert(BaseModel):
    status: str  # "firing" ili "resolved"
    labels: AlertManagerLabels
    annotations: AlertManagerAnnotations
    starts_at: datetime | None = Field(None, alias="startsAt")
    ends_at: datetime | None = Field(None, alias="endsAt")
    generator_url: str | None = Field(None, alias="generatorURL")
    fingerprint: str | None = None


class AlertManagerWebhook(BaseModel):
    """Payload struktura koju AlertManager salje."""
    receiver: str
    status: str
    alerts: list[AlertManagerAlert]
    group_labels: dict[str, Any] = Field(default_factory=dict, alias="groupLabels")
    common_labels: dict[str, Any] = Field(default_factory=dict, alias="commonLabels")
    common_annotations: dict[str, Any] = Field(default_factory=dict, alias="commonAnnotations")
    external_url: str | None = Field(None, alias="externalURL")
    version: str = "4"
    group_key: str = Field("", alias="groupKey")


# ─── Discord embed builder ───────────────────────────────────────────────

def build_discord_embed(alert: AlertManagerAlert) -> dict[str, Any]:
    """Konvertuje jedan AlertManager alert u Discord embed objekat."""
    severity_key = "resolved" if alert.status == "resolved" else alert.labels.severity.lower()
    color = SEVERITY_COLORS.get(severity_key, SEVERITY_COLORS["info"])
    emoji = SEVERITY_EMOJI.get(severity_key, SEVERITY_EMOJI["info"])

    title = f"{emoji} [{severity_key.upper()}] {alert.labels.alertname}"
    description = alert.annotations.summary or "(bez opisa)"

    fields: list[dict[str, Any]] = []

    if alert.annotations.description:
        fields.append({
            "name": "Detalji",
            "value": alert.annotations.description[:1024],  # Discord limit
            "inline": False,
        })

    if alert.labels.instance:
        fields.append({"name": "Instance", "value": alert.labels.instance, "inline": True})
    if alert.labels.job:
        fields.append({"name": "Job", "value": alert.labels.job, "inline": True})
    if alert.labels.team:
        fields.append({"name": "Tim", "value": alert.labels.team, "inline": True})

    started_at = alert.starts_at.isoformat() if alert.starts_at else "—"
    fields.append({"name": "Pocetak", "value": started_at, "inline": True})

    if alert.status == "resolved" and alert.ends_at:
        fields.append({
            "name": "Resolved",
            "value": alert.ends_at.isoformat(),
            "inline": True,
        })

    embed: dict[str, Any] = {
        "title": title[:256],
        "description": description[:4096],
        "color": color,
        "fields": fields,
        "timestamp": (alert.starts_at or datetime.utcnow()).isoformat(),
        "footer": {"text": SERVICE_NAME},
    }

    if alert.generator_url:
        embed["url"] = alert.generator_url

    return embed


def build_discord_payload(webhook: AlertManagerWebhook) -> dict[str, Any]:
    """Pravi Discord webhook payload sa do 10 embed-ova (Discord limit)."""
    embeds = [build_discord_embed(alert) for alert in webhook.alerts[:10]]

    # Mention @here samo za critical alert-e (bez spam-ovanja kanala).
    has_critical = any(
        a.labels.severity.lower() == "critical" and a.status == "firing"
        for a in webhook.alerts
    )
    content = "@here" if has_critical else None

    payload: dict[str, Any] = {"embeds": embeds}
    if content:
        payload["content"] = content
    return payload


# ─── FastAPI app ─────────────────────────────────────────────────────────

app = FastAPI(
    title=SERVICE_NAME,
    version=SERVICE_VERSION,
    description="AlertManager → Discord webhook bridge.",
)


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "service": SERVICE_NAME,
        "version": SERVICE_VERSION,
        "discord_webhook_configured": bool(DISCORD_WEBHOOK_URL),
    }


@app.get("/metrics")
async def prometheus_metrics() -> Response:
    """Prometheus exposition format — broj prosledjenih alert-a po severity."""
    lines = [
        "# HELP alert_router_forwarded_total Total broj prosledjenih alert-a ka Discord-u.",
        "# TYPE alert_router_forwarded_total counter",
    ]
    for severity, count in metrics_counter.items():
        lines.append(f'alert_router_forwarded_total{{severity="{severity}"}} {count}')

    return Response(content="\n".join(lines) + "\n", media_type="text/plain; version=0.0.4")


@app.post("/alert")
async def receive_alert(request: Request) -> dict[str, Any]:
    """Glavni endpoint — AlertManager salje webhook payload ovamo."""
    raw_body = await request.json()
    log.info("Primljen alert payload: receiver=%s, alerts=%d",
             raw_body.get("receiver"), len(raw_body.get("alerts", [])))

    try:
        webhook = AlertManagerWebhook.model_validate(raw_body)
    except Exception as e:
        log.warning("Nevalidan AlertManager payload: %s", e)
        raise HTTPException(status_code=400, detail=f"Invalid payload: {e}") from e

    if not DISCORD_WEBHOOK_URL:
        log.warning("DISCORD_WEBHOOK_URL nije postavljen — alert se logira ali ne salje na Discord.")
        for alert in webhook.alerts:
            log.info("[%s] %s — %s", alert.labels.severity, alert.labels.alertname,
                     alert.annotations.summary)
            metrics_counter[alert.labels.severity] += 1
        return {"forwarded": False, "reason": "discord_not_configured", "count": len(webhook.alerts)}

    payload = build_discord_payload(webhook)

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(DISCORD_WEBHOOK_URL, json=payload)
            response.raise_for_status()
    except httpx.HTTPError as e:
        log.error("Slanje na Discord nije uspelo: %s", e)
        raise HTTPException(status_code=502, detail=f"Discord forward failed: {e}") from e

    for alert in webhook.alerts:
        metrics_counter[alert.labels.severity] += 1

    log.info("Prosledjeno %d alert(a) na Discord.", len(webhook.alerts))
    return {"forwarded": True, "count": len(webhook.alerts)}
