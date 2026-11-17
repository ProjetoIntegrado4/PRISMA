from contextlib import asynccontextmanager
import hmac
import os
from uuid import UUID
from fastapi import FastAPI, Depends, Header, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
import store
import domain


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


class EvaluationRequest(BaseModel):
    recommendation_id: UUID
    reference: str = Field(default="", max_length=6000)


@app.get("/health")
def health():
    with store.connect() as db:
        db.execute("SELECT 1")
    from local_llm import BASE, MODEL, EMBEDDING_MODEL
    import httpx
    try:
        response = httpx.get(BASE + '/api/tags', timeout=5)
        response.raise_for_status()
        models = [m['name'] for m in response.json().get('models', [])]
    except Exception:
        models = []
    return {"status": "ok", "provider": "ollama_local", "model": MODEL,
            "local_ready": MODEL in models and EMBEDDING_MODEL in models}


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


@app.post("/recommend")
def recommend(request: ProfileRequest):
    from rag import recommend as generate
    return generate(profile(request))


@app.post("/predict")
def predict(request: Scores):
    from ml import predict as classify
    return classify(request.model_dump())


@app.post("/training/jobs", status_code=202)
def training():
    return store.new_job("train", {})


@app.post("/index/jobs", status_code=202)
def index():
    return store.new_job("index", {})


@app.post("/evaluation/jobs", status_code=202)
def evaluation(request: EvaluationRequest):
    record = store.recommendation(str(request.recommendation_id))
    if record["status"] != "generated":
        raise ValueError("A recomendação precisa ter sido gerada pela IA.")
    return store.new_job("evaluate", request.model_dump(mode="json"))


@app.get("/jobs/{job_id}")
def job(job_id: UUID):
    return store.get_job(str(job_id))
