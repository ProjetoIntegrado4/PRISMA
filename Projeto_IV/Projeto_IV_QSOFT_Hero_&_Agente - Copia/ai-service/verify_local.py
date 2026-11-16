"""Opt-in real local integration verification; no paid API calls."""
import json
import rag
import domain
import store

if __name__ == '__main__':
    print(json.dumps(rag.index_catalog()), flush=True)
    p = domain.profile('C01', 'C01-E0001', 'CSV01')
    result = rag.recommend(p)
    print(json.dumps({'id': result['id'], 'model': result['model'], 'provider':result['provider'],
                      'recommendation':result['recommendation']}, ensure_ascii=False), flush=True)
