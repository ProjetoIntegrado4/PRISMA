"""Competence classification uses specific assessment scores, never raw usage volume."""
import csv
import os
import uuid
from pathlib import Path
import numpy as np
from psycopg.types.json import Jsonb
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score, confusion_matrix
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
import store

FEATURES = ["fundamentos", "verificacao", "aplicacao"]
LABELS = ["iniciante", "intermediario", "avancado"]


def train(job_id="manual", seed=42):
    path = Path(os.getenv("PRISMA_DADOS_DIR", "/data")) / "assessment_labels.csv"
    if not path.exists():
        raise ValueError("Falta assessment_labels.csv com avaliações e níveis rotulados. Consulte docs/AGENTE_GABARITO_A4.md.")
    with path.open(encoding="utf-8-sig", newline="") as stream:
        records = list(csv.DictReader(stream))
    if len(records) < 30:
        raise ValueError("São necessárias ao menos 30 avaliações, com ao menos 10 por nível.")
    ids = [r.get("assessment_id", "") for r in records]
    subjects = [r.get("subject_id", "") for r in records]
    if not all(ids) or len(set(ids)) != len(ids) or not all(subjects) or len(set(subjects)) != len(subjects):
        raise ValueError("Use assessment_id único e uma avaliação por subject_id para evitar vazamento entre treino e teste.")
    if any(not r.get("reviewer") or r.get("label") not in LABELS for r in records):
        raise ValueError("Informe avaliador e rótulo válido em cada avaliação.")
    x = np.array([[float(r[f]) for f in FEATURES] for r in records])
    if not np.isfinite(x).all() or (x < 0).any() or (x > 100).any():
        raise ValueError("Notas de avaliação devem estar entre 0 e 100.")
    y = np.array([r["label"] for r in records])
    if any(sum(y == label) < 10 for label in LABELS):
        raise ValueError("São necessárias ao menos 10 avaliações por nível.")
    train_x, test_x, train_y, test_y = train_test_split(x, y, test_size=0.25, random_state=seed, stratify=y)
    scaler = StandardScaler().fit(train_x)
    model = LogisticRegression(max_iter=1000, random_state=seed).fit(scaler.transform(train_x), train_y)
    predicted = model.predict(scaler.transform(test_x))
    artifact = {"features": FEATURES, "classes": model.classes_.tolist(), "mean": scaler.mean_.tolist(),
                "scale": scaler.scale_.tolist(), "coef": model.coef_.tolist(), "intercept": model.intercept_.tolist(),
                "source": path.name, "job_id": job_id, "seed": seed, "train_samples": len(train_y), "test_samples": len(test_y),
                "metrics": {"accuracy": accuracy_score(test_y, predicted), "f1_macro": f1_score(test_y, predicted, average="macro"),
                            "confusion_matrix": confusion_matrix(test_y, predicted, labels=model.classes_).tolist()},
                "limitations": "Métricas de uma divisão estratificada interna; não são validação externa nem diagnóstico."}
    mid = str(uuid.uuid4())
    with store.connect() as db:
        db.execute("INSERT INTO ai_models(id,artifact) VALUES (%s,%s)", (mid, Jsonb(artifact)))
    return {"model_id": mid, **artifact}


def predict(scores):
    with store.connect() as db:
        row = db.execute("SELECT id,artifact FROM ai_models ORDER BY created_at DESC LIMIT 1").fetchone()
    if not row:
        return {"status": "not_trained", "message": "Perfil ainda não avaliado: nenhum modelo treinado disponível."}
    a = row["artifact"]
    x = np.array([scores[f] for f in FEATURES])
    if not np.isfinite(x).all() or (x < 0).any() or (x > 100).any():
        raise ValueError("Notas de avaliação devem estar entre 0 e 100.")
    logits = np.array(a["coef"]) @ ((x - a["mean"]) / a["scale"]) + a["intercept"]
    probs = np.exp(logits - max(logits)); probs /= probs.sum()
    return {"status": "predicted", "profile": a["classes"][int(np.argmax(probs))], "model_id": str(row["id"]),
            "probabilities": dict(zip(a["classes"], probs.tolist())), "source": a["source"],
            "limitations": "Estimativa baseada nas notas informadas, sujeita a erro; probabilidades não calibradas. " + a["limitations"]}
