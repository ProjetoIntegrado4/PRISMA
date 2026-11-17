"""API FastAPI do agente: perfil observado (CSVs da base), ML (regressão logística) e producer do RabbitMQ."""
from contextlib import asynccontextmanager
import hmac
import json
import os
from uuid import UUID
import pika
from fastapi import FastAPI, Depends, Header, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
import store
import domain

TRAINING_QUEUE = "ml.training.requests"


@asynccontextmanager
async def lifespan(app):
    store.initialize()
    yield


def authorize(x_internal_token: str = Header(default="")):
    expected = os.getenv("AI_INTERNAL_TOKEN", "")
    if not expected or not hmac.compare_digest(expected, x_internal_token):
        raise HTTPException(401, "Acesso interno não autorizado")


app = FastAPI(title="Prisma Agente IA", lifespan=lifespan, dependencies=[Depends(authorize)])


@app.exception_handler(ValueError)
def invalid(request, exc):
    return JSONResponse(status_code=400, content={"message": str(exc)})


@app.exception_handler(Exception)
def unavailable(request, exc):
    from errors import public_error
    return JSONResponse(status_code=503, content={"message": public_error(exc)})


class Scores(BaseModel):
    fundamentos: float = Field(ge=0, le=100, allow_inf_nan=False)
    verificacao: float = Field(ge=0, le=100, allow_inf_nan=False)
    aplicacao: float = Field(ge=0, le=100, allow_inf_nan=False)


class ProfileRequest(BaseModel):
    company_id: str = Field(min_length=1, max_length=50, pattern=r"^[A-Za-z0-9_-]+$")
    employee_id: str = Field(min_length=1, max_length=70, pattern=r"^[A-Za-z0-9_-]+$")
    interest: str | None = Field(default=None, max_length=20)
    assessment: Scores | None = None


class TrainingRequest(BaseModel):
    seed: int = Field(default=42, ge=0, le=2**31 - 1)


@app.get("/health")
def health():
    with store.connect() as db:
        db.execute("SELECT 1")
    return {"status": "ok"}


@app.get("/options")
def options(company: str | None = None):
    return domain.options(company)


@app.post("/profile")
def profile(request: ProfileRequest):
    observed = domain.profile(request.company_id, request.employee_id, request.interest)
    if request.assessment:
        from ml import predict as classify
        observed["assessment"] = classify(request.assessment.model_dump())
    return observed


@app.post("/predict")
def predict(request: Scores):
    """Previsão síncrona (REST): não passa pelo RabbitMQ."""
    from ml import predict as classify
    return classify(request.model_dump())


def publish_training(seed=42):
    """Producer: publica uma solicitação de treino no RabbitMQ, sem treinar dentro da API."""
    job = store.new_job("train", {"seed": seed}, status="queued")
    jid = job["id"]
    try:
        params = pika.URLParameters(os.environ["RABBITMQ_URL"])
        params.blocked_connection_timeout = 10
        with pika.BlockingConnection(params) as conn:
            ch = conn.channel()
            ch.queue_declare(queue=TRAINING_QUEUE, durable=True)
            ch.basic_publish(exchange="", routing_key=TRAINING_QUEUE,
                             body=json.dumps({"job_id": jid, "seed": seed}),
                             properties=pika.BasicProperties(delivery_mode=2, content_type="application/json"))
    except Exception:
        store.update_job(jid, "failed", error="RabbitMQ indisponível: a solicitação de treino não foi publicada.")
        raise
    return job


@app.post("/training/jobs", status_code=202)
def training(request: TrainingRequest | None = None):
    """Treino assíncrono: responde 202 sem aguardar; o worker consome a fila e treina."""
    return publish_training((request or TrainingRequest()).seed)


@app.get("/jobs/{job_id}")
def job(job_id: UUID):
    return store.get_job(str(job_id))
