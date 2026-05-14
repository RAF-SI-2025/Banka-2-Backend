# syntax=docker/dockerfile:1.7

# ─── Build stage ─────────────────────────────────────────────────────────
# Instalira Python deps + sve build dependencies (gcc, rust toolchain) potrebne
# za chromadb (rust crates kompajluju iz source-a) i sentence-transformers
# (numpy/torch wheels imaju C extension dependencies).
# `--mount=type=cache,target=/root/.cache/pip` keshira pip-ove downloads — no-cache
# rebuild ide sa ~12 min na ~2 min ako requirements.txt nije menjan.
FROM python:3.12-slim AS build

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Instaliramo deps u virtualenv da bismo mogli da kopiramo CELE .venv u runtime stage
# bez pip-a, build-essential-a, ili apt cache-a.
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip wheel && \
    pip install -r requirements.txt

# ─── Runtime stage ───────────────────────────────────────────────────────
# Slim Python (~40MB) + samo virtualenv (~3GB sa torch/sentence-transformers/chroma).
# build-essential (~600MB), gcc, rust, pip cache, apt list-e — sve odsutno.
# Image ide sa ~9.2GB → ~3.0GB.
FROM python:3.12-slim AS runtime

# curl je samo za healthcheck; ostatak apt-get-a koji je pao u build stage-u nije
# potreban u runtime-u (chromadb/torch su vec kompajlovani u venv-u).
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Kopiramo virtualenv iz build stage-a — sve site-packages dependencies, kompajlovani
# C extension-i, vec instalirani na ~/.local. Nema pip-a u final image-u, sto smanjuje
# attack surface (CVE-jeve u pip-u ne uticu).
COPY --from=build /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /app

COPY app/ ./app/
COPY scripts/ ./scripts/

# Volumes (kreiraju se kroz docker-compose):
#   ./Info o predmetu  -> /app/data         (read-only spec dokumenti)
#   chroma_persist     -> /app/chroma_db    (named volume sa indeksom)
RUN mkdir -p /app/data /app/chroma_db

EXPOSE 8091

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8091/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091"]
