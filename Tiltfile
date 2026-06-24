# Tiltfile — Queenswood dev loop on kind.
#
# Prereqs: `just start-docker` (raises kind-node inotify limits and
# starts colima); a kind cluster (`kind create cluster --name queenswood`).
#
# Builds every bank-*-service image (including the one-shot
# bank-bootstrap-service) from the shared parameterised Dockerfile
# (`infra/docker/service/Dockerfile`, PROJECT_NAME build-arg),
# renders the Helm chart at `infra/helm/queenswood` under release
# name `queenswood` (matching the Justfile + README so resource
# names don't diverge between the two flows), and applies it to
# the current kind context.
#
# Ordering: bank-bootstrap-service runs first as a Job (applies
# FDB metadata + Pulsar tenant/namespace/topics/schemas + seeds
# the internal organization + platform/micro policies). All HTTP
# and processor services gate on its completion via Tilt
# `resource_deps` AND a `kubectl wait --for=condition=complete`
# initContainer in the deployment template.
#
# Re-running bootstrap on source change: k8s Job specs are
# immutable, so editing bank-bootstrap-service code and rebuilding
# the image cannot in-place replace the running Job. Trigger the
# `bootstrap-reset` resource manually (button in Tilt UI), which
# deletes the Job; Tilt then re-applies a fresh one with the new
# image content. The `ttlSecondsAfterFinished: 86400` on the Job
# means completed pods stick around for 24h for log inspection.
#
# TODO(live-update): each service rebuilds the uberjar from scratch
# on every change (~minutes). Once a clojure-aware runtime image
# exists, switch to `live_update` for in-container incremental
# rebuilds.

# Adjust to match `kubectl config current-context` if your kind
# cluster is named differently.
allow_k8s_contexts(['kind-kind', 'kind-queenswood'])

# Helm release name. Must match the one Justfile's `helm-install`
# uses (`queenswood`); diverging here would mean Tilt's resource
# names all start with a different prefix and your kubectl muscle
# memory wouldn't translate.
RELEASE = 'queenswood'

SERVICES = [
    'bank-migrator-service',
    'bank-bootstrap-service',
    'bank-api-service',
    'bank-cash-account-processor-service',
    'bank-party-processor-service',
    'bank-payment-processor-service',
    'bank-interest-processor-service',
    'bank-transaction-processor-service',
    'bank-idv-processor-service',
    'bank-scheduler-processor-service',
    'bank-clearbank-adapter-service',
    'bank-clearbank-simulator-service',
    'bank-onfido-adapter-service',
    'bank-onfido-simulator-service',
    'bank-uk-companies-house-simulator-service',
]

# Build each service image. The chart renders image refs as
# `{{ .Values.image.registry }}/<svcName>:{{ .Values.image.tag }}`;
# Tilt matches them against these registrations and re-tags its
# locally built image so the rendered manifest points at content
# Tilt actually built — no `image_keys` / `--set` plumbing needed.
for svc in SERVICES:
    docker_build(
        'ghcr.io/repldriven/' + svc,
        '.',
        dockerfile='infra/docker/service/Dockerfile',
        build_args={'PROJECT_NAME': svc},
    )

# Render + apply the chart. Subchart Helm hooks lose their hook
# semantics under `helm template` and run as plain workloads —
# fine for dev because the bootstrap Job is idempotent (skips
# already-created Pulsar topology, treats FDB "meta-data version
# must increase" as no-op, re-enriches the existing internal org).
k8s_yaml(helm(
    './infra/helm/queenswood',
    name=RELEASE,
    values=['./infra/helm/queenswood/values-dev.yaml'],
))

# Migrator Job (FDB schema + Pulsar topology). Runs first.
MIGRATOR_JOB = '%s-bank-migrator-dev' % RELEASE
k8s_resource(
    workload=MIGRATOR_JOB,
    labels=['bootstrap'],
)
local_resource(
    'migrator-reset',
    cmd='kubectl delete job %s --ignore-not-found' % MIGRATOR_JOB,
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['bootstrap'],
)

# Bootstrap Job (org seed + policies). Depends on migrator.
BOOTSTRAP_JOB = '%s-bank-bootstrap-dev' % RELEASE
k8s_resource(
    workload=BOOTSTRAP_JOB,
    labels=['bootstrap'],
    resource_deps=[MIGRATOR_JOB],
)

# Manual trigger: delete the existing bootstrap Job. Use after
# editing bank-bootstrap-service code so Tilt can re-apply with
# the freshly built image — k8s won't overwrite an immutable Job
# spec, so the old Job has to go first.
local_resource(
    'bootstrap-reset',
    cmd='kubectl delete job %s --ignore-not-found' % BOOTSTRAP_JOB,
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
    labels=['bootstrap'],
)

# HTTP-fronted services: forward each port + group as `http` +
# gate startup on the bootstrap Job.
HTTP_PORTS = {
    'bank-api-service':                 8080,
    'bank-clearbank-simulator-service': 8081,
    'bank-clearbank-adapter-service':   8082,
    'bank-onfido-simulator-service':    8083,
    'bank-onfido-adapter-service':      8084,
}
for svc, port in HTTP_PORTS.items():
    k8s_resource(
        workload='%s-%s' % (RELEASE, svc),
        port_forwards='{p}:{p}'.format(p=port),
        labels=['http'],
        resource_deps=[BOOTSTRAP_JOB],
    )

# Companies House simulator. Its container listens on 8084 (the same
# port onfido-adapter forwards on the host), so forward it to host
# 8085 to avoid the collision.
k8s_resource(
    workload='%s-bank-uk-companies-house-simulator-service' % RELEASE,
    port_forwards='8085:8084',
    labels=['http'],
    resource_deps=[BOOTSTRAP_JOB],
)

# Pulsar processors: group + gate on bootstrap.
PROCESSORS = [
    'bank-cash-account-processor-service',
    'bank-party-processor-service',
    'bank-payment-processor-service',
    'bank-interest-processor-service',
    'bank-transaction-processor-service',
    'bank-idv-processor-service',
    'bank-scheduler-processor-service',
]
for svc in PROCESSORS:
    k8s_resource(
        workload='%s-%s' % (RELEASE, svc),
        labels=['processors'],
        resource_deps=[BOOTSTRAP_JOB],
    )
