"""Opt-in local RAGAS check on the latest persisted real recommendation."""
import json
import store
from evaluation import run

if __name__ == '__main__':
    with store.connect() as db:
        row = db.execute("SELECT id FROM ai_recommendations WHERE result->>'provider'='ollama_local' ORDER BY created_at DESC LIMIT 1").fetchone()
    if not row:
        raise SystemExit('Generate a local recommendation first.')
    print(json.dumps(run({'recommendation_id':str(row['id']), 'reference':''}), ensure_ascii=False), flush=True)
