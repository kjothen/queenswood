# Local Jaeger container for the monolith loop, which ships OTLP to
# localhost:4318 (UI on :16686). The kind loop gets its Jaeger from the
# Helm chart instead -- reach that one with `telemetry-ui`.
telemetry-start:
  docker run -d --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/jaeger:2.20.0 \
    --set receivers.otlp.protocols.http.endpoint=0.0.0.0:4318

# Stop and remove the local Jaeger container
telemetry-stop:
  docker stop jaeger && docker rm jaeger

# Port-forward the in-cluster Jaeger UI to http://localhost:16686
telemetry-ui:
  kubectl -n queenswood port-forward svc/queenswood-jaeger 16686:16686
