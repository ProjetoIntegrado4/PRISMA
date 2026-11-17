"""Consumer do RabbitMQ: treina o modelo de ML para cada solicitação da fila ml.training.requests."""
import json
import logging
import os
import time
import uuid
import pika
import store

logging.basicConfig(level=logging.INFO)
QUEUE = "ml.training.requests"
RESULTS = "ml.training.results"


def safe_error(error):
    # Exceções de provedores podem conter detalhes da requisição; persiste só mensagens seguras.
    from errors import public_error
    return public_error(error)


def on_message(channel, method, properties, body):
    try:
        job = json.loads(body)
        jid = str(uuid.UUID(str(job["job_id"])))
        seed = int(job.get("seed", 42))
    except (ValueError, KeyError, TypeError):
        # Mensagem malformada: descarta sem reentrega infinita.
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        return
    store.update_job(jid, "running")
    try:
        from ml import train
        result = train(job_id=jid, seed=seed)
        store.update_job(jid, "succeeded", result=result)
        status = "succeeded"
    except Exception as error:
        logging.error("Treino %s falhou (%s)", jid, type(error).__name__)
        store.update_job(jid, "failed", error=safe_error(error))
        status = "failed"
    channel.basic_publish(exchange="", routing_key=RESULTS, body=json.dumps({"job_id": jid, "status": status}),
                          properties=pika.BasicProperties(delivery_mode=2))
    # ACK manual só depois de salvar o status e publicar o resultado.
    channel.basic_ack(delivery_tag=method.delivery_tag)


def connect():
    params = pika.URLParameters(os.environ["RABBITMQ_URL"])
    params.heartbeat = 600
    return pika.BlockingConnection(params)


def main():
    store.initialize()
    while True:
        try:
            conn = connect(); ch = conn.channel()
            ch.queue_declare(queue=QUEUE, durable=True)
            ch.queue_declare(queue=RESULTS, durable=True)
            ch.basic_qos(prefetch_count=1)
            ch.basic_consume(queue=QUEUE, on_message_callback=on_message)
            print("ML worker pronto", flush=True)
            ch.start_consuming()
        except pika.exceptions.AMQPConnectionError:
            logging.warning("RabbitMQ indisponível; nova tentativa em 5 s")
            time.sleep(5)


if __name__ == "__main__":
    main()
