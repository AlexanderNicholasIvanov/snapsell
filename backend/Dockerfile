FROM python:3.12-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY README.md ./
COPY snapsell ./snapsell
COPY playbooks ./playbooks
RUN uv sync --frozen --no-dev
# Cloud Run injects PORT. Default matches local runs.
ENV PORT=8000
CMD ["sh", "-c", "uv run --no-sync uvicorn snapsell.main:app --host 0.0.0.0 --port ${PORT}"]
