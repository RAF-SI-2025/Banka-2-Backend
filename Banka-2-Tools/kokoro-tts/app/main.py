"""
Kokoro TTS sidecar — Phase 5 voice output za Arbitro asistenta.

Hostuje Kokoro-82M lokalno (Apache 2.0, ~330MB FP32 / 165MB FP16).
Endpointi:
  POST /tts         — generise audio iz teksta (vraca WAV bytes)
  GET  /voices      — lista dostupnih glasova
  GET  /health      — status + ucitan model + broj glasova

Reference:
  - https://huggingface.co/hexgrad/Kokoro-82M
  - https://github.com/hexgrad/kokoro

Performance:
  - CPU: real-time factor ~0.3x (3 sekunde generisanja za 1s audio)
  - GPU (RTX 4070): real-time factor ~0.05x (instant)
"""

import io
import logging
import os
import time

import numpy as np
import soundfile as sf
import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Phonemizer / Kokoro misaki workaround (issue hexgrad/kokoro#206)
# ---------------------------------------------------------------------------
# kokoro paket koristi misaki koji u zavisnosti od verzije ocekuje
# EspeakWrapper.set_data_path() (deprecated, uklonjen u phonemizer 3.3.0+).
# Sa novijim phonemizer-om i misaki-jem koji jos uvek poziva taj method,
# prvi `/tts` poziv puca sa AttributeError. Workaround: direktno postavi
# atribute (`library_path`/`data_path`) PRE bilo kog KPipeline import-a,
# pokazujuci na sistemski espeak-ng (instaliran u Dockerfile-u kroz apt-get).
try:
    from phonemizer.backend.espeak.wrapper import EspeakWrapper  # type: ignore

    # Sistemski espeak-ng iz Debian apt-get paketa
    SYSTEM_ESPEAK_LIB = "/usr/lib/x86_64-linux-gnu/libespeak-ng.so.1"
    SYSTEM_ESPEAK_DATA = "/usr/lib/x86_64-linux-gnu/espeak-ng-data"

    if os.path.exists(SYSTEM_ESPEAK_LIB):
        EspeakWrapper.library_path = SYSTEM_ESPEAK_LIB
    if os.path.exists(SYSTEM_ESPEAK_DATA):
        EspeakWrapper.data_path = SYSTEM_ESPEAK_DATA

    # Backward-compat shim za stare pozive set_data_path() (misaki 0.7.x)
    if not hasattr(EspeakWrapper, "set_data_path"):
        @classmethod
        def _set_data_path(cls, path):
            cls.data_path = path
        EspeakWrapper.set_data_path = _set_data_path

    if not hasattr(EspeakWrapper, "set_library"):
        @classmethod
        def _set_library(cls, path):
            cls.library_path = path
        EspeakWrapper.set_library = _set_library
except Exception as _e:
    logging.warning("EspeakWrapper bootstrap failed (non-fatal): %s", _e)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("kokoro-tts")

# Lazy imports da se model ne ucita pre nego sto FastAPI startuje
KOKORO_PIPELINE = None
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
SAMPLE_RATE = 24000

# Default voice — engleski Apache 2.0 glas iz Kokoro-82M v1.0 release-a.
# 54 glasa preko 8 jezika; "af_bella" je primer female US English.
DEFAULT_VOICE = os.getenv("KOKORO_DEFAULT_VOICE", "af_bella")

# Lang code-ovi koje Kokoro podrzava (ISO 639-1 + dialect)
SUPPORTED_LANGS = {
    "en-us": "a",  # American English
    "en-gb": "b",  # British English
    "es": "e",     # Spanish
    "fr": "f",     # French
    "hi": "h",     # Hindi
    "it": "i",     # Italian
    "ja": "j",     # Japanese
    "pt-br": "p",  # Brazilian Portuguese
    "zh": "z",     # Mandarin Chinese
}

app = FastAPI(title="Banka 2 Kokoro TTS", version="1.0.0")


def get_pipeline(lang_code: str = "a"):
    """
    Lazy-load Kokoro pipeline. KPipeline je wrapper koji ucita model + g2p
    fonemizator za zadati jezik. Cache-uje se globalno da svaki request ne
    ucita model iznova.
    """
    global KOKORO_PIPELINE
    if KOKORO_PIPELINE is None:
        log.info("Loading Kokoro pipeline (lang=%s, device=%s)...", lang_code, DEVICE)
        t0 = time.time()
        from kokoro import KPipeline  # type: ignore
        KOKORO_PIPELINE = KPipeline(lang_code=lang_code, device=DEVICE)
        log.info("Kokoro pipeline loaded in %.2fs", time.time() - t0)
    return KOKORO_PIPELINE


# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------
class TtsRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=5000,
                       description="Tekst za sintezu (do 5000 chars)")
    voice: str = Field(default=DEFAULT_VOICE,
                        description="Voice ID (vidi /voices)")
    lang: str = Field(default="en-us",
                       description="Jezik (en-us/en-gb/es/fr/hi/it/ja/pt-br/zh)")
    speed: float = Field(default=1.0, ge=0.5, le=2.0,
                          description="Brzina govora 0.5x - 2.0x")


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/health")
def health():
    """Health check + model status."""
    loaded = KOKORO_PIPELINE is not None
    return {
        "status": "ok",
        "service": "kokoro-tts",
        "version": "1.0.0",
        "model": "hexgrad/Kokoro-82M",
        "device": DEVICE,
        "model_loaded": loaded,
        "default_voice": DEFAULT_VOICE,
        "sample_rate": SAMPLE_RATE,
        "supported_langs": list(SUPPORTED_LANGS.keys()),
    }


@app.get("/voices")
def list_voices():
    """
    Vraca listu poznatih voice ID-ova. Kokoro v1.0 ima 54 glasa, ovo je
    podskup engleskih glasova koji se cesto koriste.
    """
    voices = [
        # American English Female
        {"id": "af_bella", "lang": "en-us", "gender": "F"},
        {"id": "af_sarah", "lang": "en-us", "gender": "F"},
        {"id": "af_nicole", "lang": "en-us", "gender": "F"},
        {"id": "af_sky", "lang": "en-us", "gender": "F"},
        # American English Male
        {"id": "am_adam", "lang": "en-us", "gender": "M"},
        {"id": "am_michael", "lang": "en-us", "gender": "M"},
        # British English
        {"id": "bf_emma", "lang": "en-gb", "gender": "F"},
        {"id": "bf_isabella", "lang": "en-gb", "gender": "F"},
        {"id": "bm_george", "lang": "en-gb", "gender": "M"},
        {"id": "bm_lewis", "lang": "en-gb", "gender": "M"},
    ]
    return {"voices": voices, "default": DEFAULT_VOICE}


@app.post("/tts")
def synthesize(req: TtsRequest):
    """
    Glavna sinteza — vraca WAV bytes (16-bit PCM, sample rate 24kHz).
    FE moze direktno da prosledi u <audio> tag preko Blob URL-a.
    """
    lang_code = SUPPORTED_LANGS.get(req.lang.lower())
    if lang_code is None:
        raise HTTPException(400, f"Nepodrzan jezik: {req.lang}. "
                                  f"Dostupni: {list(SUPPORTED_LANGS.keys())}")

    try:
        pipeline = get_pipeline(lang_code)
    except Exception as e:
        log.exception("Failed to load pipeline")
        raise HTTPException(500, f"Pipeline load error: {e}")

    t0 = time.time()
    try:
        # KPipeline vraca generator (text, phonemes, audio) tuples za chunk-ove.
        # Spojimo sve audio chunkove u jedan numpy array.
        audio_chunks = []
        for _, _, audio in pipeline(req.text, voice=req.voice, speed=req.speed):
            if audio is None or len(audio) == 0:
                continue
            audio_chunks.append(audio)

        if not audio_chunks:
            raise HTTPException(500, "Pipeline vratila prazan audio")

        full_audio = np.concatenate(audio_chunks)
        # Klipuj na [-1, 1] da sprecimo overflow pri konverziji u PCM 16-bit
        full_audio = np.clip(full_audio, -1.0, 1.0)

        # Encode kao WAV (PCM 16-bit, 24kHz mono)
        buf = io.BytesIO()
        sf.write(buf, full_audio, SAMPLE_RATE, format="WAV", subtype="PCM_16")
        buf.seek(0)
        wav_bytes = buf.read()

        duration_s = len(full_audio) / SAMPLE_RATE
        elapsed_s = time.time() - t0
        rtf = elapsed_s / duration_s if duration_s > 0 else 0
        log.info(
            "TTS OK voice=%s lang=%s text_chars=%d audio_s=%.2f elapsed_s=%.2f rtf=%.2fx",
            req.voice, req.lang, len(req.text), duration_s, elapsed_s, rtf,
        )

        return Response(
            content=wav_bytes,
            media_type="audio/wav",
            headers={
                "X-Audio-Duration-Seconds": f"{duration_s:.2f}",
                "X-Inference-Time-Seconds": f"{elapsed_s:.2f}",
                "X-Real-Time-Factor": f"{rtf:.2f}",
            },
        )
    except HTTPException:
        raise
    except Exception as e:
        log.exception("TTS synthesis failed")
        raise HTTPException(500, f"TTS error: {e}")


@app.on_event("startup")
def startup():
    log.info("Kokoro TTS sidecar startup (device=%s, default_voice=%s)",
              DEVICE, DEFAULT_VOICE)
    # Pre-load pipeline za English (default lang) tako da prvi request
    # nije sporiji od narednih.
    try:
        get_pipeline("a")
        log.info("Pre-loaded English pipeline")
    except Exception as e:
        log.warning("Failed to pre-load pipeline (will lazy-load on first request): %s", e)
