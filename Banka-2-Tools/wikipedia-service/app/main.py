"""
Wikipedia sidecar za Arbitro asistenta.

Reference: Info o predmetu/LLM_Asistent_Plan.txt v3.2 §10.3.
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import wikipedia
from cachetools import TTLCache

app = FastAPI(title="Banka 2 Wikipedia Tool", version="1.0.0")

# wikipedia paket trazi user_agent — Wikipedia ToS
wikipedia.set_user_agent("Banka2-Arbitro/1.0 (luka.stojiljkovic@raf.rs)")

# TTL 1h — popularne queries (BELIBOR, akcije, opcije) ce biti cached posle prvog poziva
cache = TTLCache(maxsize=500, ttl=3600)


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=200)
    lang: str = Field(default="sr", pattern="^(sr|en)$")
    limit: int = Field(default=5, ge=1, le=10)


class SummaryRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    lang: str = Field(default="sr", pattern="^(sr|en)$")
    sentences: int = Field(default=3, ge=1, le=10)


@app.get("/health")
def health():
    return {"status": "ok", "service": "wikipedia-tool", "version": "1.0.0"}


@app.post("/search")
def search(req: SearchRequest):
    """
    Pretraga sa multi-stage fallback strategijom:
      1. Probaj zadati jezik (default `sr`) sa originalnim query
      2. Ako prazno → probaj engleski (siroki indeks vise tema)
      3. Ako i dalje prazno → simplifikuj query (uzmi samo "primarne" reci,
         skidaj funkcijske reci i znakove interpunkcije) i ponovi en search
    Vraca uvek strukturu sa `results` (mozda prazne) + `attempts` listom
    sto je tried (debug). Ne raise-uje 5xx za prazne — to nije error stanje.
    """
    cache_key = f"search:{req.lang}:{req.query.lower()}:{req.limit}"
    if cache_key in cache:
        return cache[cache_key]

    attempts: list[dict] = []
    results: list[str] = []
    final_lang = req.lang

    def _try(lang: str, q: str, label: str):
        nonlocal results, final_lang
        try:
            wikipedia.set_lang(lang)
            r = wikipedia.search(q, results=req.limit)
            attempts.append({"strategy": label, "lang": lang, "query": q, "hits": len(r)})
            if r:
                results = r
                final_lang = lang
                return True
        except Exception as exc:  # noqa: BLE001
            attempts.append({"strategy": label, "lang": lang, "query": q, "error": str(exc)})
        return False

    # Stage 1: traziti jezik
    if _try(req.lang, req.query, "primary"):
        out = {"results": results, "lang": final_lang, "attempts": attempts}
        cache[cache_key] = out
        return out

    # Stage 2: english fallback (uvek bolji indeks)
    if req.lang != "en":
        if _try("en", req.query, "english_fallback"):
            out = {"results": results, "lang": final_lang, "attempts": attempts}
            cache[cache_key] = out
            return out

    # Stage 3: simplified query (skidaj kratke reci + interpunkciju)
    import re
    cleaned = re.sub(r"[?!.,;:\"'()\[\]]", " ", req.query)
    tokens = [t for t in cleaned.split() if len(t) > 3]
    if tokens and len(tokens) < len(req.query.split()):
        simplified = " ".join(tokens[:5])  # max 5 najduzih reci
        if _try("en", simplified, "simplified_query"):
            out = {"results": results, "lang": final_lang, "attempts": attempts}
            cache[cache_key] = out
            return out

    out = {"results": [], "lang": req.lang, "attempts": attempts,
           "fallback_message": "Wikipedia nije pronasla relevantne rezultate. "
                               "Asistent treba da koristi sopstveno znanje."}
    cache[cache_key] = out
    return out


@app.post("/summary")
def summary(req: SummaryRequest):
    """
    Multi-stage fallback za clanak:
      1. Pokusaj target lang (default `sr`)
      2. PageError → probaj english (siroki indeks)
      3. Page i dalje nije pronadjena → search-aj title u engleskom,
         vrati prvi hit kao novi title pa pokusaj summary
      4. Sve fail-ovalo → vraca `summary: null` + `fallback_message` koji
         instruktuje asistenta da koristi sopstveno znanje
    DisambiguationError uvek vraca options listu — agent treba da pozove
    summary ponovo sa preciznijim naslovom.
    """
    cache_key = f"sum:{req.lang}:{req.title.lower()}:{req.sentences}"
    if cache_key in cache:
        return cache[cache_key]

    def _try_summary(lang: str, title: str):
        wikipedia.set_lang(lang)
        return wikipedia.summary(title, sentences=req.sentences,
                                 auto_suggest=True, redirect=True)

    # Stage 1: target lang
    try:
        text = _try_summary(req.lang, req.title)
        out = {"title": req.title, "lang": req.lang, "summary": text}
        cache[cache_key] = out
        return out
    except wikipedia.exceptions.DisambiguationError as exc:
        out = {
            "title": req.title,
            "lang": req.lang,
            "summary": None,
            "disambiguation_options": exc.options[:10],
            "fallback_message": "Naslov je dvosmislen. Pozovi summary ponovo "
                                "sa preciznijim naslovom iz disambiguation_options.",
        }
        cache[cache_key] = out
        return out
    except wikipedia.exceptions.PageError:
        pass

    # Stage 2: english fallback
    if req.lang != "en":
        try:
            text = _try_summary("en", req.title)
            out = {"title": req.title, "lang": "en", "summary": text, "fallback": "english"}
            cache[cache_key] = out
            return out
        except wikipedia.exceptions.DisambiguationError as exc:
            out = {
                "title": req.title,
                "lang": "en",
                "summary": None,
                "disambiguation_options": exc.options[:10],
                "fallback_message": "Naslov je dvosmislen na engleskom. Pozovi "
                                    "summary ponovo sa preciznijim naslovom.",
            }
            cache[cache_key] = out
            return out
        except wikipedia.exceptions.PageError:
            pass
        except Exception:  # noqa: BLE001
            pass

    # Stage 3: search english + uzmi prvi hit kao title pa pokusaj summary
    try:
        wikipedia.set_lang("en")
        hits = wikipedia.search(req.title, results=3)
        for hit in hits:
            try:
                text = _try_summary("en", hit)
                out = {"title": hit, "lang": "en", "summary": text,
                       "fallback": "search_first_hit",
                       "original_title": req.title}
                cache[cache_key] = out
                return out
            except Exception:  # noqa: BLE001
                continue
    except Exception:  # noqa: BLE001
        pass

    # Stage 4: nista nije nadjeno — agent treba da koristi sopstveno znanje
    out = {
        "title": req.title,
        "lang": req.lang,
        "summary": None,
        "error": "Page not found",
        "fallback_message": "Wikipedia nije pronasla clanak ni na srpskom ni na "
                            "engleskom (probano: direkt, simplified, search-first). "
                            "Asistent treba da koristi sopstveno znanje da odgovori.",
    }
    cache[cache_key] = out
    return out
