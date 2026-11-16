import httpx
from openai import RateLimitError
from errors import public_error

def test_credit_balance_error_is_actionable_without_secrets():
    error = RateLimitError('private account details', response=httpx.Response(429, request=httpx.Request('POST', 'https://api.openai.com/v1/embeddings')),
                           body={'code':'credit_balance_exhausted','type':'insufficient_quota'})
    message = public_error(error)
    assert 'saldo ou cota insuficiente' in message
    assert 'private' not in message
