"""Durable outbox + publisher confirms + manual ACK + idempotent terminal jobs."""
import json
import logging
import os
import time
import uuid
import pika
from psycopg.types.json import Jsonb
import store

logging.basicConfig(level=logging.INFO)
QUEUE = os.getenv("AI_QUEUE", "qai.training")
KINDS = {"qai.training": ["train", "index"], "qai.evaluation": ["evaluate"]}


def execute(kind, payload):
    if kind == "train":
        from ml import train
        return train()
    if kind == "index":
        from rag import index_catalog
        return index_catalog()
    if kind == "evaluate":
        from evaluation import run
        return run(payload)
    raise ValueError("Tipo de processamento inválido")


def safe_error(error):
    # Provider exceptions can include request details. Persist only local validation errors.
    from errors import public_error
    return public_error(error)


def process(channel, method, properties, body):
    try:
        jid = str(uuid.UUID(json.loads(body)["id"]))
    except (ValueError, KeyError, TypeError):
        channel.basic_nack(method.delivery_tag, requeue=False)
        return
    with store.connect() as db:
        # A session advisory lock survives commits, but is released if the worker crashes.
        locked = db.execute("SELECT pg_try_advisory_lock(hashtextextended(%s,0)) AS locked", (jid,)).fetchone()["locked"]
        if not locked:
            channel.basic_nack(method.delivery_tag, requeue=True)
            return
        row = db.execute("SELECT * FROM ai_jobs WHERE id=%s", (jid,)).fetchone()
        if not row or row["status"] in ("succeeded", "failed") or row["kind"] not in KINDS[QUEUE]:
            channel.basic_ack(method.delivery_tag)
            return
        db.execute("UPDATE ai_jobs SET status='running',updated_at=now() WHERE id=%s", (jid,)); db.commit()
        try:
            result = execute(row["kind"], row["payload"])
            db.execute("UPDATE ai_jobs SET status='succeeded',result=%s,error=NULL,updated_at=now() WHERE id=%s", (Jsonb(result), jid))
        except Exception as error:
            logging.error("Job %s failed (%s)", jid, type(error).__name__)
            db.execute("UPDATE ai_jobs SET status='failed',error=%s,updated_at=now() WHERE id=%s", (safe_error(error), jid))
        db.commit()
    channel.basic_ack(method.delivery_tag)


def main():
    store.initialize()
    while True:
        try:
            params = pika.URLParameters(os.environ["RABBITMQ_URL"])
            params.heartbeat = 600
            params.blocked_connection_timeout = 30
            with pika.BlockingConnection(params) as connection:
                channel = connection.channel()
                channel.queue_declare(queue=QUEUE, durable=True)
                channel.confirm_delivery()
                channel.basic_qos(prefetch_count=1)
                channel.basic_consume(QUEUE, process, auto_ack=False)
                while connection.is_open:
                    with store.connect() as db:
                        pending = db.execute("""SELECT id FROM ai_jobs WHERE kind=ANY(%s) AND
                            (status='pending' OR (status='published' AND updated_at < now()-interval '5 minutes'))
                            ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED""", (KINDS[QUEUE],)).fetchall()
                        for job in pending:
                            channel.basic_publish(exchange="", routing_key=QUEUE,
                                body=json.dumps({"id": str(job["id"])}), mandatory=True,
                                properties=pika.BasicProperties(delivery_mode=2, content_type="application/json"))
                            db.execute("UPDATE ai_jobs SET status='published',updated_at=now() WHERE id=%s", (job["id"],))
                    connection.process_data_events(time_limit=2)
        except Exception as error:
            logging.error("Worker reconnecting (%s)", type(error).__name__)
            time.sleep(5)


if __name__ == "__main__":
    main()
