FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app
COPY app ./app

# Cloud Run uses PORT env
ENV PORT=8000
EXPOSE 8000

# Run inside container.
#
# NOTE on --host 0.0.0.0:
#   Inside a Docker container, binding to 0.0.0.0 is REQUIRED for the
#   container to be reachable via port mapping. The container's localhost
#   is isolated from the host network, so this is NOT the same as binding
#   to 0.0.0.0 on bare metal.
#
#   The security boundary is the HOST-side port mapping:
#     docker-compose ports: "127.0.0.1:8000:8000"  ✅ (host-only)
#     docker-compose ports: "8000:8000"            ❌ (exposed to network)
#
#   See docs/06-security-model.md#parser-isolation
CMD exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT} --workers 1