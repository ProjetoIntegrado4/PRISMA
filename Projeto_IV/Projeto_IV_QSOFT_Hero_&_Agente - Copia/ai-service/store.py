import json
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
        db.execute("""CREATE TABLE IF NOT EXISTS ai_recommendations (
            id uuid PRIMARY KEY, result jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now())""")
        db.execute("""CREATE TABLE IF NOT EXISTS ai_courses (
            id text PRIMARY KEY, content_hash text NOT NULL, embedding_model text NOT NULL,
            document text NOT NULL, embedding vector(1536) NOT NULL)""")
        db.execute("""CREATE TABLE IF NOT EXISTS ai_models (
            id uuid PRIMARY KEY, artifact jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now())""")
        db.execute("""CREATE TABLE IF NOT EXISTS ai_courses_local (
            id text PRIMARY KEY, content_hash text NOT NULL, embedding_model text NOT NULL,
            document text NOT NULL, embedding vector(768) NOT NULL)""")


def new_job(kind, payload):
    jid = str(uuid.uuid4())
    with connect() as db:
        db.execute("INSERT INTO ai_jobs(id,kind,payload) VALUES (%s,%s,%s)", (jid, kind, Jsonb(payload)))
    # Database is the outbox. Worker dispatcher publishes pending jobs using publisher confirms.
    return {"id": jid, "kind": kind, "status": "pending"}


def get_job(jid):
    with connect() as db:
        row = db.execute("SELECT id,kind,status,result,error,created_at,updated_at FROM ai_jobs WHERE id=%s", (jid,)).fetchone()
    if not row:
        raise ValueError("Processamento não encontrado")
    return row


def save_recommendation(result):
    rid = str(uuid.uuid4())
    result["id"] = rid
    with connect() as db:
        db.execute("INSERT INTO ai_recommendations VALUES (%s,%s,now())", (rid, Jsonb(result)))
    return result


def recommendation(rid):
    with connect() as db:
        row = db.execute("SELECT result FROM ai_recommendations WHERE id=%s", (rid,)).fetchone()
    if not row:
        raise ValueError("Recomendação não encontrada")
    return row["result"]
