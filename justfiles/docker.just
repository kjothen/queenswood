# Start Docker via Colima
docker-start:
    colima status 2>/dev/null || colima start --arch aarch64 --vm-type vz --vz-rosetta --cpu 6 --memory 24 --disk 100
    # Raise inotify limits — kind nodes plus per-pod kubectl/curl
    # polling exhaust the defaults (128 instances, 8192 watches)
    # and surface as "too many open files" in init containers.
    colima ssh -- sudo sysctl -w fs.inotify.max_user_instances=8192 fs.inotify.max_user_watches=524288
    docker context use colima

# Stop Docker via Colima
docker-stop:
    colima stop

# Build a single service Docker image: `just docker-build bank-api-service dev`
docker-build project tag="dev":
    docker buildx build \
      --build-arg PROJECT_NAME={{ project }} \
      -t {{ DOCKER_REGISTRY }}/{{ project }}:{{ tag }} \
      -f infra/docker/service/Dockerfile \
      --load \
      .

# Build all service images in parallel via docker buildx bake
docker-build-all tag="dev":
    # Targets are declared in infra/docker/bake.hcl; the Clojure base layer is shared
    # so this is dramatically faster than building images one at a time.
    REGISTRY={{ DOCKER_REGISTRY }} TAG={{ tag }} \
      docker buildx bake -f infra/docker/bake.hcl
