import os
import uuid
import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb


def connect():
    return psycopg.connect(os.environ["AI_DATABASE_URL"], row_factory=dict_row, connect_timeout=5)


def initialize():
    with connect() as db:
        db.execute("SELECT pg_advisory_xact_lock(77281903)")
        db.execute("CREATE EXTENSION IF NOT EXISTS vector")
        db.execute("""CREATE TABLE IF NOT EXISTS ai_jobs (
            id uuid PRIMARY KEY, kind text NOT NULL, status text NOT NULL DEFAULT 'pending',
            payload jsonb NOT NULL, result jsonb, error text,
            created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now())""")
        db.execute("""CREATE TABLE IF NOT EXISTS ai_models (
            id uuid PRIMARY KEY, artifact jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now())""")
        # Experimentos RAGAS (serviço ragas_api.py).
        db.execute("""CREATE TABLE IF NOT EXISTS ai_experiments (
            id uuid PRIMARY KEY, status text NOT NULL, input jsonb NOT NULL, metrics jsonb, error text,
            created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now())""")


def new_job(kind, payload, status="pending"):
    jid = str(uuid.uuid4())
    with connect() as db:
        db.execute("INSERT INTO ai_jobs(id,kind,status,payload) VALUES (%s,%s,%s,%s)", (jid, kind, status, Jsonb(payload)))
    return {"id": jid, "kind": kind, "status": status}


def update_job(jid, status, result=None, error=None):
    with connect() as db:
        db.execute("UPDATE ai_jobs SET status=%s,result=%s,error=%s,updated_at=now() WHERE id=%s",
                   (status, Jsonb(result) if result is not None else None, error, jid))


def get_job(jid):
    with connect() as db:
        row = db.execute("SELECT id,kind,status,result,error,created_at,updated_at FROM ai_jobs WHERE id=%s", (jid,)).fetchone()
    if not row:
        raise ValueError("Processamento não encontrado")
    return row
