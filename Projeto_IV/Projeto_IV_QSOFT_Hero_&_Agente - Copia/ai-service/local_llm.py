"""Ollama on the private Compose network. No cloud fallback."""
import os
import httpx
from openai import OpenAI

BASE = os.getenv('OLLAMA_URL', 'http://ollama:11434').rstrip('/')
MODEL = os.getenv('OLLAMA_MODEL', 'qwen2.5:1.5b')
EMBEDDING_MODEL = 'nomic-embed-text:v1.5'

def client():
    return OpenAI(base_url=BASE + '/v1', api_key='ollama-local', timeout=140, max_retries=0)

def embed(texts):
    with httpx.Client(timeout=140) as session:
        response = session.post(BASE + '/api/embed', json={'model': EMBEDDING_MODEL, 'input': texts, 'keep_alive': 0})
        response.raise_for_status()
        vectors = response.json()['embeddings']
    if any(len(v) != 768 for v in vectors):
        raise ValueError('Dimensão inesperada do modelo local de embeddings. Esperado: 768.')
    return vectors

def generate(messages, schema):
    with httpx.Client(timeout=140) as session:
        response = session.post(BASE + '/api/chat', json={'model': MODEL, 'messages': messages,
            'format': schema, 'stream': False, 'keep_alive': 0,
            'options': {'temperature': 0, 'num_ctx': 4096, 'num_predict': 650}})
        response.raise_for_status()
        return response.json()['message']['content']
