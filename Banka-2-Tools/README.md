# Banka-2-Tools — Arbitro AI assistant stack (opcioni)

**Potpuno odvojen Docker compose** od `Banka-2-Backend/docker-compose.yml`.
Tim clan koji ne zeli da koristi LLM ne pokrece nista odavde — BE radi
normalno, FE FAB pokazuje "Offline" status, ostatak app-a je netaknut.
Arbitro je opcioni Celina 6 (NIJE deo Celine 1-5 KT3 zahteva).

## Servisi

Svi u jednom compose-u, port mapped na host:

- **`banka2_ollama`** (port 11434) — Gemma 4 E2B LLM **PRE-BAKED u image layer-u**
  + derived `gemma4:e2b-gpu` model sa `num_gpu=999` (force full VRAM offload).
  Korisnik dobija LLM odmah, bez 5-15min pull-a pri prvom startu.
- **`banka2_wikipedia_tool`** (port 8090) — FastAPI + wikipedia 1.4 + cachetools TTL 1h
- **`banka2_rag_tool`** (port 8091) — FastAPI + sentence-transformers + ChromaDB,
  auto-indeksira 236 spec dokumenata pri prvom startu
- **`banka2_kokoro_tts`** (port 8092) — Kokoro TTS (model `hexgrad/Kokoro-82M`,
  9 podrzanih jezika, voice `af_bella`)

## Quick start (idiot-proof)

```bash
# Iz Banka-2-Backend/Banka-2-Tools foldera:
docker login ghcr.io -u <user> -p <PAT>   # samo ako paket nije public
docker compose pull
docker compose up -d
```

To je sve. Stack se podiže odmah:

1. **Ollama startuje INSTANT** — Gemma 4 E2B + derived gemma4:e2b-gpu su vec u
   image layer-u (`./ollama/Dockerfile` pull-uje model TOKOM build-a u GHA CD).
   Nema vise `ollama-pull` one-shot kontejnera, nema vise 5-15min cekanja.
2. wikipedia-tool startuje odmah (~150MB image).
3. rag-tool indeksira spec dokumente (~30-60s prvi put), pa starta FastAPI.
4. kokoro-tts loaduje TTS model (~80MB).

Posle ~30-60s, `/assistant/health` na BE-u vraca svi reachable=true.

### Lokalni rebuild (kad GHCR nije dostupan)

```bash
docker compose up -d --build
# Build je dugacak ~10-15min jer Dockerfile pull-uje Gemma model i kreira
# derived varijantu, ali to je jednokratan trosak.
```

## Cross-compose komunikacija (BE ↔ Arbitro)

Kako BE i Arbitro stack-ovi nisu u istom compose-u, pristupaju se preko
**host published portova**. BE container sadrzi `extra_hosts:
host.docker.internal:host-gateway` u svom compose-u (jednolinijski overhead
koji ne aktivira Arbitro), pa kad BE pravi HTTP poziv na
`http://host.docker.internal:11434`, Docker rutira ka host-u (gde Arbitro
stack ima publish-ovan port 11434).

Posledica:

- **Mac/Windows Docker Desktop** — radi out-of-box (host.docker.internal je
  builtin alias).
- **Linux native Docker** — `extra_hosts: host-gateway` osigurava da alias
  resolve-uje na host gateway IP.
- **Arbitro stack ugasi** → BE pozivi puknu na connection-refused;
  AssistantService catch-uje i `/assistant/health` vraca `false` flag-ove.
  Korisnik vidi "Offline" badge ali aplikacija dalje radi.

## GPU passthrough (CUDA, opciono)

Ako imas NVIDIA GPU, ubrzanje je **5-20×**:

```bash
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d
```

Detaljni preduslov-i u `docker-compose.gpu.yml` headeru (NVIDIA Container
Toolkit setup za Linux/WSL2). Mac NIJE PODRZAN za Docker GPU passthrough —
Mac timovi mogu da pokrenu Ollama van Docker-a (`brew install ollama && ollama
serve && ollama pull gemma4:e2b`) i izostave `ollama` + `ollama-pull` servise
ovde.

**Performance (RTX 4070 + flash attn + Q8 KV cache):** AON odgovor 5s (CPU-only
105s — 20× brze). VRAM utilization 7767/8188 MiB = 94.9% (full GPU offload,
nema CPU split).

Brzine:

- CPU (modern x86): 5-15 tok/s
- RTX 3060: 30-50 tok/s
- RTX 4070+: 80-120 tok/s
- Mac M2/M3 native (van Docker-a): 40-70 tok/s

## Sidecar paket: alert-router-service

Pored Arbitro sidecar-a, ovaj folder sadrzi i `alert-router-service/` koji
se ne pokrece kroz `Banka-2-Tools/docker-compose.yml`, vec kroz
**`Banka-2-Backend/monitoring/docker-compose.yml`** (build referenca: `build:
../Banka-2-Tools/alert-router-service`). To je Python Flask servis koji
prevodi AlertManager webhook payload u Discord embed format. Pokrece se kao
deo MLA monitoring stack-a (Prometheus + Grafana + AlertManager + alert-router).

## Smoke testovi

```bash
# 1. Ollama API + model
curl http://localhost:11434/api/tags        # mora sadrzati "gemma4:e2b" ili "gemma4:e2b-gpu"

# 2. Wikipedia tool
curl http://localhost:8090/health
curl -X POST http://localhost:8090/search \
  -H "Content-Type: application/json" \
  -d '{"query":"BELIBOR","lang":"sr","limit":3}'

# 3. RAG tool (doc_count > 0 znaci da je indeksiranje uspelo)
curl http://localhost:8091/health
curl -X POST http://localhost:8091/search \
  -H "Content-Type: application/json" \
  -d '{"query":"kako kreiram fond","top_k":3}'

# 4. Kokoro TTS
curl http://localhost:8092/health

# 5. End-to-end preko BE-a (BE compose mora biti up)
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"marko.petrovic@banka.rs","password":"Admin12345"}' \
  http://localhost:8080/auth/login | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/assistant/health
# → svi 4 reachable=true posle Ollama pull-a
```

## Lokalni dev bez Docker-a

```bash
cd wikipedia-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090

# u drugom terminalu:
cd rag-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/index_specs.py     # prvi put
uvicorn app.main:app --host 0.0.0.0 --port 8091

# u trecem terminalu:
cd kokoro-tts
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8092

# u cetvrtom terminalu:
ollama serve
ollama pull gemma4:e2b
```

## Re-indeksiranje RAG-a (kad se promeni spec)

```bash
docker exec banka2_rag_tool rm /app/chroma_db/.indexed
docker compose restart rag-tool
```

## Arbitro funkcionalnosti (Phase 1-5 KOMPLET)

- **32 write tools + 8 read = 40 alata** (full backend coverage)
- **Action preview → OTP gate → BE izvrsi → SSE javi confirmed** flow
- **Interactive wizard (Phase 4.5)**: 15 najvaznijih write akcija sa multi-step izborom
  opcija (CHOICE/TEXT/NUMBER/CONFIRM) preko SSE `agent_choice` event-a
- **LLM intent classification (Phase 4.6)**: Gemma 4 E2B sa `tool_choice="required"`
  + 15 minimal tool schemas — agentic mode bira pravi tool iz prirodnog jezika
- **Voice IN/OUT**: Gemma 4 native ASR + Kokoro TTS sidecar
- **Multi-step plan + RAG cache + cross-encoder rerank + multimodal-ready**

## Cypress test-ovi (lokalno only)

- `arbitro-mock.cy.ts` — radi nezavisno bez Tools stack-a (mock-ovani SSE + tools)
- `arbitro-live.cy.ts` — zahteva Banka-2-Tools stack up (sa GPU za realan latency)

Ova dva spec fajla **NISU u GH CI** — Arbitro je opcioni Celina 6 i trazi
LLM + sidecar-e koje GH runner nema.

## Image sizes (multi-stage build optimizacija 03.05.2026)

- `banka2_rag_tool`: ~3GB (sa Python venv + sentence-transformers + ChromaDB)
- `banka2_wikipedia_tool`: ~250MB
- `banka2_kokoro_tts`: ~1.5GB (sa torch + Kokoro model)
- `banka2_ollama`: ~600MB (base) + 3.5GB Gemma model u volume-u

Reference: `Info o predmetu/LLM_Asistent_Plan.txt` v3.3 §10.3-10.4 i §11.
