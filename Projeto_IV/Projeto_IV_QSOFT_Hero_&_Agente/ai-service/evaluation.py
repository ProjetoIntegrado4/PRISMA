"""Métricas RAGAS com o LLM e os embeddings da OpenAI (chave em OPENAI_API_KEY, vinda do .env)."""
import math
import os
from ragas import evaluate, EvaluationDataset, SingleTurnSample
from ragas.metrics import Faithfulness, ResponseRelevancy, LLMContextPrecisionWithReference, LLMContextRecall
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas.run_config import RunConfig
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
EMBEDDING_MODEL = os.getenv("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")


def metrics_for(reference):
    """faithfulness e answer_relevancy sempre; context_precision e context_recall só com referência."""
    metrics = [Faithfulness(), ResponseRelevancy()]
    if reference:
        metrics += [LLMContextPrecisionWithReference(), LLMContextRecall()]
    return metrics


def run(question, answer, contexts, reference=None):
    if not os.getenv("OPENAI_API_KEY"):
        raise RuntimeError("OPENAI_API_KEY ausente")
    reference = (reference or "").strip() or None
    sample = SingleTurnSample(user_input=question, response=answer, retrieved_contexts=contexts, reference=reference)
    result = evaluate(EvaluationDataset(samples=[sample]), metrics=metrics_for(reference),
                      llm=LangchainLLMWrapper(ChatOpenAI(model=MODEL, temperature=0, timeout=120, max_retries=1)),
                      embeddings=LangchainEmbeddingsWrapper(OpenAIEmbeddings(model=EMBEDDING_MODEL, check_embedding_ctx_length=False)),
                      run_config=RunConfig(timeout=300, max_retries=1, max_workers=2),
                      raise_exceptions=True, show_progress=False)
    scores = {key: float(value) if value is not None and math.isfinite(float(value)) else None
              for key, value in result.scores[0].items()}
    return {"metrics": scores, "has_reference": bool(reference), "model": MODEL,
            "limitations": "RAGAS avalia a resposta e seu contexto; não valida a competência do colaborador nem substitui revisão humana."}
