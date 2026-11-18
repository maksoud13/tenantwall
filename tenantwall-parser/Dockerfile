FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app
COPY app ./app

# ═══ Default Environment Variables ═══
# Bind to 0.0.0.0 inside the container so it can be reached from outside
ENV TENANTWALL_PARSER_HOST=0.0.0.0
ENV TENANTWALL_PARSER_PORT=8000
# Workers for concurrency (CPU-bound sqlglot)
ENV TENANTWALL_PARSER_WORKERS=4
# Always enforce auth in production
ENV TENANTWALL_PARSER_ENFORCE_AUTH=1

EXPOSE 8000

# Use exec form with environment variables
CMD exec uvicorn app.main:app --host ${TENANTWALL_PARSER_HOST} --port ${TENANTWALL_PARSER_PORT} --workers ${TENANTWALL_PARSER_WORKERS}