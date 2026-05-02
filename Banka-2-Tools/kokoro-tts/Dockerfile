# Kokoro TTS sidecar — Phase 5 voice output
# Reference: huggingface.co/hexgrad/Kokoro-82M

FROM python:3.11-slim

# espeak-ng je phonemizer dep za fallback fonemizaciju jezika koje
# Kokoro ne podrzava nativno (ali g2p Kokoro-a ne treba espeak za en/es/fr/...)
RUN apt-get update && apt-get install -y --no-install-recommends \
        espeak-ng \
        libsndfile1 \
        ffmpeg \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .

# Torch CPU-only iz default index-a; ako se pokrece sa GPU, override:
#   docker build --build-arg TORCH_INDEX=https://download.pytorch.org/whl/cu124 .
ARG TORCH_INDEX=https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir torch==2.5.1 --index-url ${TORCH_INDEX} \
    && pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# Kokoro download cache mount point — hf hub cache-uje model u
# ~/.cache/huggingface; mountuje se kao volume da preživi rebuild.
ENV HF_HOME=/app/hf_cache
ENV TRANSFORMERS_CACHE=/app/hf_cache

# Force phonemizer da koristi sistemski espeak-ng (apt-get) umesto bug-ovanog
# espeakng-loader bundled path-a (issue hexgrad/kokoro#206).
ENV PHONEMIZER_ESPEAK_LIBRARY=/usr/lib/x86_64-linux-gnu/libespeak-ng.so.1
ENV PHONEMIZER_ESPEAK_PATH=/usr/bin/espeak-ng
ENV ESPEAK_DATA_PATH=/usr/lib/x86_64-linux-gnu/espeak-ng-data

EXPOSE 8092

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8092/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8092"]
