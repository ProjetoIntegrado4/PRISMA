import httpx
import pytest
import local_llm

def test_client_only_targets_local_service(monkeypatch):
    monkeypatch.setenv('OPENAI_API_KEY', 'unused-test-secret')
    client = local_llm.client()
    assert str(client.base_url).startswith('http://ollama:11434/')
    assert client.api_key == 'ollama-local'

def test_local_embedding_dimension_checked(monkeypatch):
    class Session:
        def __init__(self, **kwargs): pass
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def post(self, url, json):
            return httpx.Response(200, json={'embeddings': [[1,2]]}, request=httpx.Request('POST',url))
    monkeypatch.setattr(local_llm.httpx, 'Client', Session)
    with pytest.raises(ValueError, match='768'): local_llm.embed(['test'])
