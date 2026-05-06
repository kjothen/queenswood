# Start a local Jaeger container for OTLP traces (UI on :16686, OTLP/http on :4318)
telemetry-start:
  docker run -d --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/jaeger:latest \
    --set receivers.otlp.protocols.http.endpoint=0.0.0.0:4318

# Stop and remove the local Jaeger container
telemetry-stop:
  docker stop jaeger && docker rm jaeger
