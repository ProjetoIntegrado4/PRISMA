"""Opt-in integration test: verifies a failed training job and duplicate delivery.
Run only in an isolated environment without assessment_labels.csv.
"""
import json
import os
import time
from pathlib import Path
import pika
import store


if __name__ == '__main__':
    if (Path(os.getenv('QSOFT_DADOS_DIR', '/data')) / 'assessment_labels.csv').exists():
        raise SystemExit('Use an isolated test environment without real assessment labels.')
    job = store.new_job('train', {})
    for _ in range(30):
        result = store.get_job(job['id'])
        if result['status'] == 'failed': break
        time.sleep(1)
    assert result['status'] == 'failed' and 'assessment_labels.csv' in result['error'], result
    updated = result['updated_at']
    connection = pika.BlockingConnection(pika.URLParameters(os.environ['RABBITMQ_URL']))
    channel = connection.channel(); channel.confirm_delivery()
    for _ in range(2):
        channel.basic_publish('', 'qai.training', json.dumps({'id':job['id']}), mandatory=True,
                              properties=pika.BasicProperties(delivery_mode=2))
    connection.close()
    time.sleep(5)
    assert store.get_job(job['id'])['updated_at'] == updated
    print(json.dumps({'durable_job':'passed','rabbitmq_delivery':'passed','terminal_duplicate_not_reexecuted':'passed'}))
