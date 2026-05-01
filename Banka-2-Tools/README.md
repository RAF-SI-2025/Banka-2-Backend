# Banka-2-Tools — Arbitro AI assistant stack (opcioni)

**Potpuno odvojen Docker compose** od `Banka-2-Backend/docker-compose.yml`.
Tim clan koji ne zeli da koristi LLM ne pokrece nista odavde — BE radi
normalno, FE FAB pokazuje "Offline" status, ostatak app-a je netaknut.

Servisi (svi u jednom compose-u, port mapped na host):

- **`ollama`** (port 11434) — Gemma 4 E4B LLM, auto-pull pri prvom startu
- **`ollama-pull`** — one-shot kontejner koji pull-uje model pa zavrsava
- **`wikipedia-tool`** (port 8090) — FastAPI + wikipedia 1.4 + cachetools TTL 1h
- **`rag-tool`** (port 8091) — FastAPI + sentence-transformers + ChromaDB 1.5,
  auto-indeksira spec dokumente pri prvom startu

## Quick start

```bash
# Iz Banka-2-Backend/Banka-2-Tools foldera:
docker compose up -d
```

To je sve. Stack se sam podiže:

1. Ollama startuje, `ollama-pull` pull-uje `gemma4:e4b` (~9.6GB, prvi put
   traje 5-15min na 50 MB/s vezi). Naredni startovi su instant.
2. wikipedia-tool startuje odmah (~150MB image).
3. rag-tool indeksira spec dokumente (~30-60s prvi put), pa stara FastAPI.

Dok Ollama pull jos traje, `/assistant/health` na BE-u vraca
`llmReachable=false`. Cim pull zavrsi, sve se aktivira automatski.

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

Ako imas NVIDIA GPU, ubrzanje je 5-10×:

```bash
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d
```

Detaljni preduslov-i u `docker-compose.gpu.yml` headeru (NVIDIA Container
Toolkit setup za Linux/WSL2). Mac NIJE PODRZAN za Docker GPU passthrough —
Mac timovi mogu da pokrenu Ollama van Docker-a (`brew install ollama && ollama
serve && ollama pull gemma4:e4b`) i izostave `ollama` + `ollama-pull` servise
ovde.

Brzine:
- CPU (modern x86): 5-15 tok/s
- RTX 3060: 30-50 tok/s
- RTX 4070+: 80-120 tok/s
- Mac M2/M3 native (van Docker-a): 40-70 tok/s

## Smoke testovi

```bash
# 1. Ollama API + model
curl http://localhost:11434/api/tags        # mora sadrzati "gemma4:e4b"

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

# 4. End-to-end preko BE-a (BE compose mora biti up)
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"marko.petrovic@banka.rs","password":"Admin12345"}' \
  http://localhost:8080/auth/login | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/assistant/health
# → svi 3 reachable=true posle Ollama pull-a
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
ollama serve
ollama pull gemma4:e4b
```

## Re-indeksiranje RAG-a (kad se promeni spec)

```bash
docker exec banka2_rag_tool rm /app/chroma_db/.indexed
docker compose restart rag-tool
```

Reference: `Info o predmetu/LLM_Asistent_Plan.txt` v3.3 §10.3-10.4 i §11.
