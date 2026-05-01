"""
RAG sidecar za Arbitro asistenta.
Semanticka pretraga nad Banka 2 spec dokumentima (Celina 1-5 + Marzni + Opcije).

Reference: Info o predmetu/LLM_Asistent_Plan.txt v3.2 §10.4 i §11.

API (chromadb 1.5.x — Rust rewrite, NOVI klijent API):
  POST /search  {query, top_k=5}  ->  {results: [{text, source, score, chunk_id}]}
  GET  /health
"""
import hashlib
import os
from typing import Optional

import chromadb
from cachetools import TTLCache
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import CrossEncoder, SentenceTransformer

PERSIST_DIR = os.environ.get("CHROMA_PERSIST_DIR", "/app/chroma_db")
COLLECTION = os.environ.get("CHROMA_COLLECTION", "banka2_specs")
EMBED_MODEL = os.environ.get(
    "EMBED_MODEL", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)
SCORE_THRESHOLD = float(os.environ.get("RAG_SCORE_THRESHOLD", "0.5"))

# Phase 5 — cross-encoder reranking. Bi-encoder embedding daje "probably
# relevant" top-20, cross-encoder onda paroviran query+chunk pass-uje kroz
# pravu attention layer-u i daje precizan rank. ms-marco-MiniLM je 22MB
# distilirana verzija (faster nego BGE-reranker, ali manja precision).
RERANK_MODEL = os.environ.get(
    "RERANK_MODEL", "cross-encoder/ms-marco-MiniLM-L-6-v2"
)
RERANK_ENABLED = os.environ.get("RAG_RERANK_ENABLED", "true").lower() == "true"
# Fetch top-20 iz ChromaDB pa rerank na top-N koje user trazi.
RERANK_FETCH_K = int(os.environ.get("RAG_RERANK_FETCH_K", "20"))

# Phase 5 optimizacija — embedding cache. Iste query-je (npr. "kako kreiram fond")
# koje vise korisnika postavi unutar 1h ponovo embedduju isti vektor; ovo je
# cisti CPU gubitak. TTLCache sa 1000 entries i 1h TTL-om resava ~30%+
# CPU usteda u typical demo workload-u.
_embed_cache: TTLCache = TTLCache(maxsize=1000, ttl=3600)
_search_cache: TTLCache = TTLCache(maxsize=500, ttl=3600)

app = FastAPI(title="Banka 2 RAG Tool", version="1.0.0")

# Lazy load — model + collection se otvaraju pri prvom requestu (i nakon toga
# drze u memoriji).
_model: Optional[SentenceTransformer] = None
_reranker: Optional[CrossEncoder] = None
_collection = None


def _get_model() -> SentenceTransformer:
    global _model
    if _model is None:
        _model = SentenceTransformer(EMBED_MODEL)
    return _model


def _get_reranker() -> Optional[CrossEncoder]:
    """Lazy load cross-encoder. Vraca None ako iskljuceno ili download fail-uje."""
    global _reranker
    if not RERANK_ENABLED:
        return None
    if _reranker is None:
        try:
            _reranker = CrossEncoder(RERANK_MODEL)
        except Exception:
            # Ako download ne uspe (offline ili greska), ostani na bi-encoder rezultatima
            return None
    return _reranker


def _get_collection():
    global _collection
    if _collection is None:
        client = chromadb.PersistentClient(path=PERSIST_DIR)
        _collection = client.get_or_create_collection(
            name=COLLECTION,
            metadata={"hnsw:space": "cosine"},
        )
    return _collection


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=500)
    top_k: int = Field(default=5, ge=1, le=20)


@app.get("/health")
def health():
    try:
        col = _get_collection()
        count = col.count()
    except Exception as exc:
        return {"status": "degraded", "error": str(exc), "doc_count": 0}
    return {
        "status": "ok",
        "service": "rag-tool",
        "version": "1.0.0",
        "embed_model": EMBED_MODEL,
        "collection": COLLECTION,
        "doc_count": count,
        "rerank_enabled": RERANK_ENABLED,
        "rerank_model": RERANK_MODEL if RERANK_ENABLED else None,
        "rerank_loaded": _reranker is not None,
        "rerank_fetch_k": RERANK_FETCH_K,
        "embed_cache_size": len(_embed_cache),
        "search_cache_size": len(_search_cache),
    }


@app.post("/search")
def search(req: SearchRequest):
    try:
        model = _get_model()
        col = _get_collection()
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"RAG not initialized: {exc}") from exc

    if col.count() == 0:
        # Indeksiranje jos nije pokrenuto — Day 2 task
        return {"results": [], "warning": "Index empty; run scripts/index_specs.py first."}

    # Phase 5 optimizacija — search cache za pun (query, top_k) par.
    # Hash-ujemo query da klucevi ne sadrze osetljive substringe.
    cache_key = (
        hashlib.sha256(req.query.encode("utf-8")).hexdigest()[:16],
        req.top_k,
    )
    cached_result = _search_cache.get(cache_key)
    if cached_result is not None:
        return {**cached_result, "cached": True}

    # Embedding cache — ako vec imamo embedding za query, preskaci CPU compute.
    embed_key = hashlib.sha256(req.query.encode("utf-8")).hexdigest()[:16]
    embedding = _embed_cache.get(embed_key)
    if embedding is None:
        embedding = model.encode([req.query], show_progress_bar=False).tolist()[0]
        _embed_cache[embed_key] = embedding

    # Phase 5 — fetch top-RERANK_FETCH_K (default 20) iz ChromaDB ako rerank
    # je aktivan, pa cross-encoder pass-uje paroviran query+chunk i bira
    # finalnih top_k. Bez rerank-a, fetch direktno top_k.
    fetch_k = max(req.top_k, RERANK_FETCH_K) if RERANK_ENABLED else req.top_k
    raw = col.query(
        query_embeddings=[embedding],
        n_results=fetch_k,
        include=["documents", "metadatas", "distances"],
    )

    candidates = []
    documents = raw.get("documents", [[]])[0]
    metadatas = raw.get("metadatas", [[]])[0]
    distances = raw.get("distances", [[]])[0]
    for doc, meta, dist in zip(documents, metadatas, distances):
        score = 1.0 - float(dist)  # cosine distance -> similarity (bi-encoder)
        candidates.append({
            "text": doc,
            "source": meta.get("source", "unknown"),
            "chunk_index": meta.get("chunk_index"),
            "celina": meta.get("celina"),
            "bi_score": round(score, 4),
            "score": round(score, 4),  # default; preko-write-uje rerank ako se desi
        })

    # Phase 5 — cross-encoder rerank na top-RERANK_FETCH_K → top-top_k
    reranker = _get_reranker()
    reranked = False
    if reranker is not None and len(candidates) > 1:
        try:
            pairs = [(req.query, c["text"]) for c in candidates]
            rerank_scores = reranker.predict(pairs).tolist()
            for c, s in zip(candidates, rerank_scores):
                c["rerank_score"] = round(float(s), 4)
                c["score"] = round(float(s), 4)
            candidates.sort(key=lambda x: x.get("rerank_score", 0), reverse=True)
            reranked = True
        except Exception:
            # Ako rerank pukne, padaj na bi-encoder redoslled (vec sortiran)
            pass

    # Filter po threshold-u i ogranici na top_k.
    # NAPOMENA: kad se rerank desio, score je na drugoj skali (logit, ~ -10 do +10)
    # pa SCORE_THRESHOLD ne primenjujemo na rerank scores; primenjujemo samo na
    # bi_score koji je cosine similarity [0, 1].
    results = []
    for c in candidates[:req.top_k]:
        if c.get("bi_score", 0) < SCORE_THRESHOLD:
            continue
        results.append(c)

    response = {
        "results": results,
        "threshold": SCORE_THRESHOLD,
        "reranked": reranked,
    }
    _search_cache[cache_key] = response
    return response
