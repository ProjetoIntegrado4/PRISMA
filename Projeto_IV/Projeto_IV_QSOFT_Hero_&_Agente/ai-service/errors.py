from openai import AuthenticationError, PermissionDeniedError, RateLimitError, APITimeoutError, APIConnectionError, NotFoundError


def public_error(error):
    import httpx
    if isinstance(error, httpx.TimeoutException):
        return "O serviço excedeu o tempo disponível. Tente novamente."
    if isinstance(error, (httpx.HTTPError,)):
        return "Serviço externo indisponível. Verifique a conexão e tente novamente."
    if isinstance(error, AuthenticationError):
        return "A OpenAI recusou a chave configurada. Verifique se está ativa e correta."
    if isinstance(error, PermissionDeniedError):
        return "A chave OpenAI não tem permissão para esta operação ou modelo."
    if isinstance(error, RateLimitError):
        if getattr(error, "code", None) in {"insufficient_quota", "credit_balance_exhausted"} or getattr(error, "type", None) == "insufficient_quota":
            return "A OpenAI informou saldo ou cota insuficiente. Confira o faturamento e os limites do projeto na plataforma."
        return "Limite de chamadas da OpenAI atingido. Aguarde e confira os limites do projeto."
    if isinstance(error, NotFoundError):
        return "Modelo OpenAI indisponível para este projeto. Confira OPENAI_MODEL."
    if isinstance(error, (APITimeoutError, APIConnectionError)):
        return "Não foi possível conectar à OpenAI no prazo disponível. Tente novamente."
    if isinstance(error, ValueError):
        return str(error)[:500]
    return "Falha no serviço externo ou no processamento. Verifique configuração e disponibilidade."
