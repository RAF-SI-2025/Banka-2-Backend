# Pre-baked Ollama image sa Gemma 4 E2B model-om (~7.2GB) ucitanim u layer.
#
# Cilj: idiot-proof setup. Bilo ko ko klonira repo i pokrene
#   cd Banka-2-Tools && docker compose up -d
# dobije ODMAH upotrebljiv Arbitro stack — bez ikakvog "Cekam Ollama..."
# spinning, bez 5-15min model pull-a, bez `ollama-pull` one-shot kontejnera.
#
# Build:
#   docker build -t banka2-ollama ./ollama
#
# Build je dugacak (~10-15min za pull + create derived model + commit), ali
# se to dogadja JEDNOM po push-u na main u GHA workflow-u (`backend-cd.yml`
# ollama job). Kraj-korisnici samo `docker compose pull` i imaju gotov image.
#
# Image sadrzi:
#   - gemma4:e2b           — base model (7.2GB)
#   - gemma4:e2b-gpu       — derived sa PARAMETER num_gpu 999 (force full VRAM offload)
#                            i PARAMETER num_ctx 4096 (eksplicitan context limit)

FROM ollama/ollama:latest

# Model tag — override-ovan preko build arg-a u CI ili lokalno:
#   docker build --build-arg OLLAMA_MODEL=gemma4:e4b -t banka2-ollama .
ARG OLLAMA_MODEL=gemma4:e2b
ENV OLLAMA_MODEL=${OLLAMA_MODEL}

# Default Ollama image koristi `/bin/ollama serve` kao entrypoint+cmd.
# Da bismo pull-ovali model TOKOM build-a, moramo da:
#   1. Startujemo ollama serve u pozadini
#   2. Sacekamo da API podigne (`ollama list` proba dok ne uspe)
#   3. `ollama pull <model>` u istom RUN sloju (Docker keshira layer)
#   4. `ollama create <model>-gpu -f Modelfile` sa PARAMETER num_gpu=999
#   5. Stop-ujemo serve da slobodimo build proces
#
# Sve u jednom RUN-u jer Docker layer mora biti final u istom step-u.
RUN /bin/ollama serve & \
    SERVER_PID=$! && \
    echo "Cekam Ollama API da se podigne..." && \
    for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do \
        /bin/ollama list >/dev/null 2>&1 && break ; \
        echo "  pokusaj $i" ; \
        sleep 2 ; \
    done && \
    /bin/ollama list >/dev/null 2>&1 || (echo "Ollama nije podigla API za 40s, abort" && exit 1) && \
    echo "Pull-ujem ${OLLAMA_MODEL} (~7.2GB za gemma4:e2b)..." && \
    /bin/ollama pull "${OLLAMA_MODEL}" && \
    echo "Pull zavrsen, kreiram derived ${OLLAMA_MODEL}-gpu sa num_gpu=999..." && \
    DERIVED_MODEL="${OLLAMA_MODEL%%:*}:${OLLAMA_MODEL##*:}-gpu" && \
    printf 'FROM %s\nPARAMETER num_gpu 999\nPARAMETER num_ctx 4096\n' "${OLLAMA_MODEL}" > /tmp/Modelfile.gpu && \
    /bin/ollama create "${DERIVED_MODEL}" -f /tmp/Modelfile.gpu && \
    /bin/ollama list && \
    echo "Modeli ${OLLAMA_MODEL} + ${DERIVED_MODEL} u image layer-u. Stop-ujem build server..." && \
    kill $SERVER_PID 2>/dev/null || true && \
    sleep 3 && \
    rm -f /tmp/Modelfile.gpu

# Restore upstream entrypoint+cmd (ne menjamo runtime ponasanje, samo
# dodajemo modele u /root/.ollama/models/blobs/* koji su sada u image layer-u).
ENTRYPOINT ["/bin/ollama"]
CMD ["serve"]

EXPOSE 11434

# Health check — Ollama ima native /api/tags endpoint koji vraca listu modela.
HEALTHCHECK --interval=10s --timeout=5s --retries=6 --start-period=15s \
    CMD curl -fsS http://localhost:11434/api/tags >/dev/null || exit 1
