import pytest
from fastapi.testclient import TestClient
from app import app
import store
import ml


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv('AI_INTERNAL_TOKEN', 'unit-test-token')
    return TestClient(app, raise_server_exceptions=False)


def test_requires_internal_auth(client):
    assert client.get('/options').status_code == 401


@pytest.mark.parametrize('payload', [{'fundamentos': -1, 'verificacao': 50, 'aplicacao': 50},
                                    {'fundamentos': 101, 'verificacao': 50, 'aplicacao': 50}, {}])
def test_rejects_invalid_scores(client, payload):
    assert client.post('/predict', json=payload, headers={'X-Internal-Token': 'unit-test-token'}).status_code == 422


def test_missing_model_explicit(client, monkeypatch):
    monkeypatch.setattr(ml, 'predict', lambda scores: {'status': 'not_trained'})
    response = client.post('/predict', json=dict(fundamentos=50, verificacao=50, aplicacao=50), headers={'X-Internal-Token': 'unit-test-token'})
    assert response.json()['status'] == 'not_trained'


def test_training_queues_and_returns_202(client, monkeypatch):
    monkeypatch.setattr(store, 'new_job', lambda kind, payload: {'kind': kind, 'status': 'pending'})
    r = client.post('/training/jobs', headers={'X-Internal-Token': 'unit-test-token'})
    assert r.status_code == 202 and r.json()['kind'] == 'train'


def test_no_synthetic_training(tmp_path, monkeypatch):
    monkeypatch.setenv('QSOFT_DADOS_DIR', str(tmp_path))
    with pytest.raises(ValueError, match='Falta assessment_labels.csv'):
        ml.train()


def test_provider_errors_are_not_leaked():
    from worker import safe_error
    assert 'secret' not in safe_error(RuntimeError('secret api credential'))
