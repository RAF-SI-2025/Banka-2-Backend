FROM python:3.12-slim

# build essentials za chromadb (rust crates) i sentence-transformers (numpy/torch wheels)
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

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
