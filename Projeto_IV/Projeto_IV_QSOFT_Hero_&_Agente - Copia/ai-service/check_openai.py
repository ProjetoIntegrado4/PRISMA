"""Compatibility entry point: now checks Ollama, never calls a paid API."""
import json
import httpx
from local_llm import BASE, MODEL, EMBEDDING_MODEL
if __name__ == '__main__':
    response = httpx.get(BASE + '/api/tags', timeout=10)
    response.raise_for_status()
    models = [m['name'] for m in response.json().get('models', [])]
    print(json.dumps({'provider': 'ollama_local', 'model':MODEL, 'ready': MODEL in models and EMBEDDING_MODEL in models}))