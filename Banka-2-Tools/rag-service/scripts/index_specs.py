"""
Indeksira Banka 2 spec dokumente u ChromaDB kolekciju.

Pokreni jednom posle rebuilds:
    python scripts/index_specs.py

Ulazni fajlovi su mount-ovani u /app/data preko docker compose volume-a:
    ./Info o predmetu  ->  /app/data

Reference: Info o predmetu/LLM_Asistent_Plan.txt v3.2 §11.5.
"""
import os
import sys

import chromadb
from langchain_text_splitters import RecursiveCharacterTextSplitter
from sentence_transformers import SentenceTransformer

SPEC_DIR = os.environ.get("SPEC_DIR", "/app/data")
PERSIST_DIR = os.environ.get("CHROMA_PERSIST_DIR", "/app/chroma_db")
COLLECTION = os.environ.get("CHROMA_COLLECTION", "banka2_specs")
EMBED_MODEL = os.environ.get(
    "EMBED_MODEL", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)

INCLUDE_FILES = [
    "Celina 1.txt",
    "Celina 2.txt",
    "Celina 3.txt",
    "Celina 4(Nova).txt",
    "Celina 5(Nova).txt",
    "Marzni_Racuni.txt",
    "Opcije.txt",
    "E2E Scenario.txt",
]


def detect_celina(filename: str) -> str:
    """Izvuce broj celine iz imena fajla, npr. 'Celina 4(Nova).txt' -> '4'."""
    if filename.startswith("Celina ") and len(filename) >= 8:
        return filename[7]
    return "0"


def main() -> int:
    print(f"Embed model: {EMBED_MODEL}")
    print(f"Spec dir:    {SPEC_DIR}")
    print(f"Persist dir: {PERSIST_DIR}")
    print(f"Collection:  {COLLECTION}")
    print("---")

    model = SentenceTransformer(EMBED_MODEL)
    client = chromadb.PersistentClient(path=PERSIST_DIR)
    collection = client.get_or_create_collection(
        name=COLLECTION,
        metadata={"hnsw:space": "cosine"},
    )

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=800,
        chunk_overlap=200,
        separators=["\n## ", "\n### ", "\n\n", "\n", ". "],
    )

    total_chunks = 0
    for fname in INCLUDE_FILES:
        path = os.path.join(SPEC_DIR, fname)
        if not os.path.exists(path):
            print(f"WARN: {fname} ne postoji u {SPEC_DIR}, preskacem")
            continue
        with open(path, "r", encoding="utf-8") as fh:
            text = fh.read()
        chunks = splitter.split_text(text)
        if not chunks:
            print(f"WARN: {fname} prazan posle chunkinga, preskacem")
            continue

        embeddings = model.encode(chunks, show_progress_bar=False).tolist()
        celina = detect_celina(fname)
        metadatas = [
            {"source": fname, "chunk_index": i, "celina": celina}
            for i, _ in enumerate(chunks)
        ]
        ids = [f"{fname}::{i}" for i in range(len(chunks))]
        collection.upsert(
            ids=ids,
            documents=chunks,
            embeddings=embeddings,
            metadatas=metadatas,
        )
        print(f"OK   {fname}: {len(chunks)} chunks")
        total_chunks += len(chunks)

    print("---")
    print(f"Ukupno chunks: {total_chunks}")
    print(f"Collection size: {collection.count()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
