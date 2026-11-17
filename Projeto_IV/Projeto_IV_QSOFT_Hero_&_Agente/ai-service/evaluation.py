import math
import os
from ragas import evaluate, EvaluationDataset, SingleTurnSample
from ragas.metrics import Faithfulness, ResponseRelevancy, LLMContextPrecisionWithReference, LLMContextRecall
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas.run_config import RunConfig
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
import store
from local_llm import BASE, MODEL, EMBEDDING_MODEL


def run(payload):
    record = store.recommendation(payload["recommendation_id"])
    if record["status"] != "generated":
        raise ValueError("Só recomendações geradas podem ser avaliadas.")
    data = record["evaluation_input"]
    reference = payload.get("reference") or None
    sample = SingleTurnSample(user_input=data["question"], response=data["answer"],
                              retrieved_contexts=data["contexts"], reference=reference)
    metrics = [Faithfulness(), ResponseRelevancy()]
    if reference:
        metrics += [LLMContextPrecisionWithReference(), LLMContextRecall()]
    result = evaluate(EvaluationDataset(samples=[sample]), metrics=metrics,
                      llm=LangchainLLMWrapper(ChatOpenAI(model=MODEL, base_url=BASE+"/v1", api_key="ollama-local", timeout=140, max_retries=0, temperature=0, max_tokens=650)),
                      embeddings=LangchainEmbeddingsWrapper(OpenAIEmbeddings(model=EMBEDDING_MODEL, base_url=BASE+"/v1", api_key="ollama-local", check_embedding_ctx_length=False, request_timeout=140)),
                      run_config=RunConfig(timeout=600, max_retries=1, max_workers=1),
                      raise_exceptions=True, show_progress=False)
    scores = {key: float(value) if math.isfinite(float(value)) else None for key, value in result.scores[0].items()}
    return {"metrics": scores, "has_reference": bool(reference),
            "limitations": "RAGAS avalia a resposta e seu contexto; não valida a competência do colaborador nem substitui revisão humana."}
