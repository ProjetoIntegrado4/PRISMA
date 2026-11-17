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
    import app as api
    monkeypatch.setattr(api, 'publish_training', lambda seed=42: {'id': 'x', 'kind': 'train', 'status': 'queued'})
    r = client.post('/training/jobs', headers={'X-Internal-Token': 'unit-test-token'})
    assert r.status_code == 202 and r.json()['kind'] == 'train'


class FakeChannel:
    def __init__(self): self.published, self.declared, self.acks, self.nacks = [], [], [], []
    def queue_declare(self, queue, durable): self.declared.append((queue, durable))
    def basic_publish(self, exchange, routing_key, body, properties=None): self.published.append((exchange, routing_key, body, properties))
    def basic_ack(self, delivery_tag): self.acks.append(delivery_tag)
    def basic_nack(self, delivery_tag, requeue): self.nacks.append((delivery_tag, requeue))


def test_producer_publishes_on_default_exchange(monkeypatch):
    import json, app as api
    channel = FakeChannel()
    class Conn:
        def __init__(self, params): pass
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def channel(self): return channel
    monkeypatch.setenv('RABBITMQ_URL', 'amqp://u:p@rabbitmq:5672/%2F')
    monkeypatch.setattr(api.pika, 'BlockingConnection', Conn)
    monkeypatch.setattr(store, 'new_job', lambda kind, payload, status='pending': {'id': 'j1', 'kind': kind, 'status': status})
    job = api.publish_training(seed=7)
    exchange, key, body, props = channel.published[0]
    assert (exchange, key) == ('', 'ml.training.requests') and json.loads(body) == {'job_id': 'j1', 'seed': 7}
    assert props.delivery_mode == 2 and ('ml.training.requests', True) in channel.declared and job['status'] == 'queued'


def test_consumer_trains_publishes_result_and_acks_last(monkeypatch):
    import json, types, worker
    updates = []
    monkeypatch.setattr(store, 'update_job', lambda jid, status, result=None, error=None: updates.append(status))
    monkeypatch.setattr(ml, 'train', lambda job_id, seed: {'model_id': 'm', 'seed': seed})
    channel = FakeChannel()
    jid = '00000000-0000-0000-0000-000000000001'
    worker.on_message(channel, types.SimpleNamespace(delivery_tag=5), None, json.dumps({'job_id': jid, 'seed': 42}))
    assert updates == ['running', 'succeeded'] and channel.acks == [5]
    assert channel.published[0][1] == 'ml.training.results'


def test_consumer_failed_training_is_acked_with_failed_status(monkeypatch):
    import json, types, worker
    updates = []
    monkeypatch.setattr(store, 'update_job', lambda jid, status, result=None, error=None: updates.append((status, error)))
    def boom(job_id, seed): raise ValueError('Falta assessment_labels.csv')
    monkeypatch.setattr(ml, 'train', boom)
    channel = FakeChannel()
    worker.on_message(channel, types.SimpleNamespace(delivery_tag=6), None, json.dumps({'job_id': '00000000-0000-0000-0000-000000000002'}))
    assert updates[-1][0] == 'failed' and 'assessment_labels' in updates[-1][1] and channel.acks == [6]


def test_consumer_rejects_malformed_message():
    import types, worker
    channel = FakeChannel()
    worker.on_message(channel, types.SimpleNamespace(delivery_tag=9), None, b'not json')
    assert channel.nacks == [(9, False)] and channel.acks == []


def test_ragas_service_requires_auth_and_valid_body(monkeypatch):
    import ragas_api
    monkeypatch.setenv('AI_INTERNAL_TOKEN', 'unit-test-token')
    c = TestClient(ragas_api.app, raise_server_exceptions=False)
    assert c.post('/experiments/run', json={}).status_code == 401
    assert c.post('/experiments/run', json={'question': 'q'}, headers={'X-Internal-Token': 'unit-test-token'}).status_code == 422


def test_ragas_metrics_two_or_four():
    import evaluation
    assert len(evaluation.metrics_for(None)) == 2 and len(evaluation.metrics_for('referência')) == 4


def test_ragas_without_key_fails_explicitly(monkeypatch):
    import evaluation
    monkeypatch.delenv('OPENAI_API_KEY', raising=False)
    with pytest.raises(RuntimeError, match='OPENAI_API_KEY ausente'):
        evaluation.run('q', 'a', ['c'])


def test_no_synthetic_training(tmp_path, monkeypatch):
    monkeypatch.setenv('PRISMA_DADOS_DIR', str(tmp_path))
    with pytest.raises(ValueError, match='Falta assessment_labels.csv'):
        ml.train()


def test_provider_errors_are_not_leaked():
    from worker import safe_error
    assert 'secret' not in safe_error(RuntimeError('secret api credential'))
