"""Serviço FastAPI RAGAS: o Spring Boot envia POST /experiments/run e consulta GET /experiments/{id}.
A chamada HTTP é síncrona e responde 202; a avaliação prossegue em segundo plano."""
from contextlib import asynccontextmanager
import hmac
import os
import uuid
from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from psycopg.types.json import Jsonb
from pydantic import BaseModel, Field
import store


@asynccontextmanager
async def lifespan(app):
    store.initialize()
    yield


def authorize(x_internal_token: str = Header(default="")):
    expected = os.getenv("AI_INTERNAL_TOKEN", "")
    if not expected or not hmac.compare_digest(expected, x_internal_token):
        raise HTTPException(401, "Acesso interno não autorizado")


app = FastAPI(title="Prisma RAGAS", lifespan=lifespan, dependencies=[Depends(authorize)])


@app.exception_handler(ValueError)
def invalid(request, exc):
    return JSONResponse(status_code=400, content={"message": str(exc)})


class ExperimentRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    answer: str = Field(min_length=1, max_length=12000)
    contexts: list[str] = Field(min_length=1, max_length=20)
    reference: str = Field(default="", max_length=6000)
    trace_id: str = Field(default="", max_length=64)


def execute(eid, request):
    from errors import public_error
    from evaluation import run
    try:
        with store.connect() as db:
            db.execute("UPDATE ai_experiments SET status='running',updated_at=now() WHERE id=%s", (eid,))
        result = run(request["question"], request["answer"], request["contexts"], request.get("reference"))
        with store.connect() as db:
            db.execute("UPDATE ai_experiments SET status='succeeded',metrics=%s,updated_at=now() WHERE id=%s", (Jsonb(result), eid))
    except Exception as error:
        message = "Configure OPENAI_API_KEY no arquivo .env." if str(error) == "OPENAI_API_KEY ausente" else public_error(error)
        with store.connect() as db:
            db.execute("UPDATE ai_experiments SET status='failed',error=%s,updated_at=now() WHERE id=%s", (message, eid))


@app.get("/health")
def health():
    return {"status": "ok", "openai_configured": bool(os.getenv("OPENAI_API_KEY"))}


@app.post("/experiments/run", status_code=202)
def run_experiment(request: ExperimentRequest, background: BackgroundTasks):
    eid = str(uuid.uuid4())
    payload = request.model_dump()
    with store.connect() as db:
        db.execute("INSERT INTO ai_experiments(id,status,input) VALUES (%s,'pending',%s)", (eid, Jsonb(payload)))
    background.add_task(execute, eid, payload)
    return {"id": eid, "status": "pending", "has_reference": bool(request.reference.strip())}


def public(row):
    data = dict(row)
    data["question"] = (row["input"] or {}).get("question")
    data["trace_id"] = (row["input"] or {}).get("trace_id")
    data["has_reference"] = bool(((row["input"] or {}).get("reference") or "").strip())
    data.pop("input", None)
    return data


@app.get("/experiments/{experiment_id}")
def experiment(experiment_id: uuid.UUID):
    with store.connect() as db:
        row = db.execute("SELECT * FROM ai_experiments WHERE id=%s", (str(experiment_id),)).fetchone()
    if not row:
        raise ValueError("Experimento não encontrado")
    return public(row)


@app.get("/experiments")
def experiments():
    with store.connect() as db:
        rows = db.execute("SELECT * FROM ai_experiments ORDER BY created_at DESC LIMIT 30").fetchall()
    return {"experiments": [public(r) for r in rows]}
