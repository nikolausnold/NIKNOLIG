FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DB_PATH=/data/sandbox.sqlite3 \
    BIND_HOST=0.0.0.0 \
    ALLOW_INTERNAL_CONTAINER_BIND=1 \
    PORT=8080

WORKDIR /opt/sandbox
RUN groupadd --gid 10001 sandbox \
    && useradd --uid 10001 --gid 10001 --no-create-home sandbox \
    && mkdir /data \
    && chown 10001:10001 /data
COPY --chown=10001:10001 *.py ./backend/
COPY --chown=10001:10001 migrations/ ./backend/migrations/
USER 10001:10001
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD ["python", "-c", "import urllib.request; r=urllib.request.urlopen('http://127.0.0.1:8080/v1/health', timeout=3); assert r.status == 200"]
CMD ["python", "-m", "backend"]
