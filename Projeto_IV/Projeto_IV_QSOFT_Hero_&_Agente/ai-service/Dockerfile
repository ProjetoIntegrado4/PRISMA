FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 RAGAS_DO_NOT_TRACK=true
COPY requirements.txt requirements.lock ./
RUN pip install --no-cache-dir -r requirements.txt -c requirements.lock
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
COPY . .
RUN useradd --uid 10001 --create-home app
USER app
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
