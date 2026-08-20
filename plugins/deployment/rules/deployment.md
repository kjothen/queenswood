# Queenswood deployment

How Queenswood ships and runs — the Helm chart, the kind dev loop, and
the Crossplane-managed cloud infrastructure underneath it.

## Deploy each service as project + base + shared Dockerfile

A deployable service is a project (pure config, `deps.edn`) plus a
base (owns `main.clj`) plus an image built from the shared
`infra/docker/service/Dockerfile` with a `PROJECT_NAME` build-arg —
never a bespoke Dockerfile. Every service Deployment waits on the
bootstrap Job via a `wait-for-bootstrap` initContainer before
starting; neither the migrator nor the bootstrap Job may be skipped
in any deployment flow. A cross-pod startup dependency is expressed
via the deployment's `waitFor` list, which adds a `wait-for-<dep>`
initContainer polling the target's `/actuator/health/liveness`.
Deploy flows share the same Helm release name (`queenswood`) so resource
names don't diverge between them. Never bake an environment name into
a resource name — discriminate via `values.yaml` overrides and env
vars. A project MAY carry a service-specific `application.yml`; a
service MAY listen on more than one port, with `port` the primary
(probes and the Service's `http` port target it) and further
listeners in `extraPorts`; a service MAY set `replicas > 1` if it's
HTTP-fronted or a processor that doesn't own a changelog cursor.
Never delete a `Keycloak` resource while its database survives: the
operator owns the admin Secret and regenerates the password on the way
back, while the database keeps the old admin user, so Keycloak serves
the realm and nothing can administer it — the realm import fails to
obtain a token, bootstrap waits on the import, and every service waits
on bootstrap. Resetting the schema fixes that only where FoundationDB is
rebuilt in the same act: the realm returns from the committed JSON with
fresh user ids, and FDB records referencing the old ones are orphaned
silently. Where FDB survives, restore the realm from its export and
reset the credential in place instead.
`exclusive-dispatchers-service` stays at 1 — it owns every changelog
cursor and every cron trigger, and each admits exactly one
dispatcher; scale the relay tier by sharding stores across
deployments. Raising replicas elsewhere buys standbys
rather than throughput until `message-bus/send` carries a partition
key and topics have more than one partition.
See [deployment](../../../docs/recipes/deployment.md).

## A folder is an installation, and its foundations are not deleted

A GCP folder is what an installation is: an IAM boundary, the one place
an org-policy exemption is expressed, and the only stable handle, since
project ids carry random suffixes and everything else is discovered from
the folder down. Inside it, a management project running Crossplane and
Argo, never torn down, and one disposable project per instance. There
may be as many folders as the installer wants; each is independent and
identically shaped, with its own bootstrap identity and management
project because those rights are folder-scoped.

Protect a foundation in GCP rather than in a manifest. Projects, DNS
zones and backup buckets carry `managementPolicies` without `Delete` and
a project lien; a folder cannot carry a lien, so what protects it is
that nobody holds `resourcemanager.folders.delete`. The lien matters
more than the policy — a policy is a convention a later edit undoes, and
the hazard is not a deleted control plane, which leaves managed
resources with no finalizers running, but a live one watching its
resources vanish through a prune and doing what it was told. Only the
disposable tier — clusters, databases, addresses, certificates — is
fully managed with `Delete`.

Express off as a desired state rather than an absence of one:
Crossplane reconciles toward what is declared and has no notion of
stopped.
See [ADR-0022](../../../docs/adr/0022-cloud-foundation-and-environment-lifecycle.md).

## Build the plane before a merge can install anything

A control plane running another toolchain cannot apply an
`XManagementPlane` at all — the manifest names a kind its API
server has never heard of — so the plane comes before any merge that
would install something. Grant the bootstrap identity its rights on the
folder or the parent, never a key. Commit the manifest before applying
it, and before any plane takes over reading it from git, and pivot the
composite off a throwaway plane before discarding that plane. Never
grant a person `serviceAccountTokenCreator` on the platform identity or
create a key for any of the four identities, never assume you may create
a folder — ids are required and one may be handed to you instead — and
never delete a project as a side effect of an edit. Deploying with
`instances: []` is valid, asking a platform team for the folder and the
identity shortens this path without changing it, and another XRD and
composition loaded onto the plane deploys something else the same way.
See [crossplane-app-deployment](../../../docs/recipes/crossplane-app-deployment.md).

## An installation is one file, and changing it is a merge

Change what exists by editing the manifest rather than by acting on GCP,
and apply from merged state only, since a `pull_request` trigger gets no
cloud identity. Push the manifest before a plane takes over reading it
from git. Supply `management.projectId` always and
`createFolder.folderId` wherever the folder already exists, give
`metadata.name` and `spec.code` the same string, keep the manifests
repository private, and read `status` back rather than committing it.
Never commit anything secret beside the manifest, never name a principal
in `access` that does not exist — IAM rejects the binding, not the
manifest — never create a key for an identity the installation
composes, and never leave a new XRD field required when the manifest
that sets it lives in another repository. `management.source` may name
upstream, a fork, or a mirror that vendors the layout, pinned to a tag;
an empty `access` mapping installs and capabilities may be added later;
and one manifest per folder allows more than one installation.
See [queenswood-installation](../../../docs/recipes/queenswood-installation.md).

## Cloud infrastructure is Crossplane, not Terraform

Cloud infrastructure is declared via Crossplane, not Terraform. A
small local kind cluster (`boot-mgmt`, the boot management plane)
runs
Crossplane plus the GCP upjet providers and `provider-helm`; every
cloud resource is a Crossplane Managed Resource or a Composite of
them (XRDs + Compositions). Argo CD on the same kind cluster applies
the manifests from the repo, and workloads on GKE are themselves
Crossplane `Release` resources of `provider-helm`.
See [ADR-0016](../../../docs/adr/0016-crossplane-over-terraform.md).

## A composed resource is identified by its composition name

Change a resource's `- name:` to rebuild it under a new
`metadata.name`: Crossplane matches on the composition resource name
and recreates from the composite's record, so renaming the object alone
rebuilds the old one. Set `policy.fromFieldPath: Required` where a
missing source is a mistake rather than a meaning — a patch whose
source is absent is skipped silently, while a Required patch whose
source is absent drops the whole composed resource, which is how a
block becomes optional without a second composition and why such a
field cannot carry an XRD default — and put constants in `base`,
because a `Format` transform with no verb for its input corrupts the
value. Server-side apply gives every field a manager, and a manager
that stops declaring a field it solely owns removes it — so the
composition owns everything it patches, deleting a patch deletes the
field rather than leaving it, a field the composition never set stays
free for the provider to late-initialise or for a hand patch to hold,
and two managers declaring one field make it stable. Check
`metadata.managedFields` before assuming either way.
Count the live instances of a kind before removing its XRD, reading the
cluster rather than assuming from the fact that nothing in the
repository creates one: the XRD owns its CRD, so the kind and every
composite of it go together, and a Composition outlives the XRD it names
because nothing links them but a `compositeTypeRef`. Where the
Application carrying the XRD prunes, deleting the file is the removal;
where it does not, the plane goes on serving the kind and the edit reads
as a change that did nothing.
Withhold `Delete` from `managementPolicies` for anything whose loss is
unrecoverable: deleting the managed resource then orphans the cloud
resource rather than destroying it, and deleting a composite destroys
whatever its resources permit. Install a provider for every kind the
composite composes, on every plane that composes it, and never compose
a cluster-scoped kind from a namespaced composite. Neither failure
belongs to the resource it names: one pipeline step failing — an
unparseable template, a kind with no CRD — stops every composed
resource and reports on the composite. Read `Synced`, `Ready` and
`LastAsyncOperation` before concluding anything.
See [crossplane](../../../docs/recipes/crossplane.md).

## Provider resources are Terraform underneath

Read the CRD with `kubectl explain` before writing a composed resource,
not the provider's documentation — shapes differ between versions and
from what Terraform documents. Delete the managed resource to change
anything that identifies it: a ForceNew change is refused rather than
performed, and the refusal appears in `LastAsyncOperation`, so
diagnosing from `Synced` alone misreads it. A list-shaped field may
still be identity — a managed `Certificate`'s `managed.domains` replaces
the certificate rather than extending it, so a second name is a second
`Certificate` sharing the one `DNSAuthorization`. Pivot a
provider-assigned value up to the composite and compose from it rather
than committing a literal read out by hand, since a `DNSAuthorization`
issues the record it wants answered only once it exists.
Use the `.m.` API group.
Set the external name explicitly where it must differ from the
Kubernetes name or where something else spells the same string, and
feed a generated id back as an adopt value where the external name is
empty after create, or the resource never completes. Do not re-add a
patch for a field late-initialisation now owns.
See [crossplane-providers](../../../docs/recipes/crossplane-providers.md).

## An automation identity is granted, never inherited

Give every node pool its own service account holding
`roles/container.defaultNodeServiceAccount`, and never rely on the
default compute service account being powerless — that is an org policy
enforced somewhere else. Grant both halves of Workload Identity, the
GCP binding and the `iam.gke.io/gcp-service-account` annotation, and
pin the Kubernetes service account name so the binding matches
something. Grant `iam.serviceAccounts.actAs` on any account something
must attach to a resource. Audit an inheriting identity against every
resource it must manage before the identity that created them is
discarded: whoever creates a project owns it, so a bootstrap identity
holds rights nothing declared. Prefer a project custom role over a
predefined role granting writes you do not want, naming it with
underscores because a custom role id takes no hyphens, and do not
assume a role can be granted at the scope its feature acts on.
`gcloud auth login` does not refresh ADC.
See [gcp-iam](../../../docs/recipes/gcp-iam.md).

## A credential is a declared container and a written version

A composite composes the Secret Manager entry and never a value, a
person adds the version once from `secretsAdmin`, and external-secrets
reads it on the destination cluster — authenticated by Workload
Identity, through a `ClusterSecretStore` with no auth block, because the
controller's pod already carries the annotated service account. That is
what keeps the value out of git and out of Argo, and what makes a
rebuilt cluster re-read rather than re-upload. Name the entry
`sec-<code>-c-<what>` on a plane and `sec-<code>-<env>-<label>-<what>`
on an instance, pin the release name and the service account name that
binding spells literally rather than letting Argo derive either from an
Application's name, and withhold `Delete` where the value cannot be
regenerated. Put a version in with `just gcp-secret-version`, which
refuses to create a container the composite has not made and strips the
trailing newline from anything typed or piped: Secret Manager stores the
bytes it is given, and a consumer reading the value whole sends that
newline as part of the credential, failing as a rejected secret rather
than as a newline. Never commit a credential, private repository or not,
and never keep a second durable copy of one that can be regenerated —
`rm` is not deletion on a copy-on-write filesystem, and a local store is
a second authority on one machine with no audit trail. A container with
no version fails wherever the value was used rather than as a credential
nobody wrote, and a version added after the `ExternalSecret` synced
waits out the refresh interval, so delete the controller's pod and
restart whatever reads the value at startup.
See [external-secrets](../../../docs/recipes/external-secrets.md).

## Google sign-in is two console acts and an Admin API call

Create the OAuth client by hand, as a Web application: no API makes one
with a chosen redirect URI, so no automation holds the right and none
can — grant `roles/oauthconfig.editor` through `platformAdmin` rather
than `platformViewer`, since creating a client mints a credential and a
capability called viewer should not. Match that URI to
`https://keycloak.<domain>/realms/<realm>/broker/<alias>/endpoint`
exactly, alias included — a wrong one is accepted at setup and returns
`redirect_uri_mismatch` once the user has already left for Google. Create
one client per environment and never share one, since revoking a
development client must not touch what customers sign in through, and a
client in Testing mode is a different risk object from a published one.
Configure the consent screen first, since the client cannot be created
without one, and read its verification warning against the scopes
actually requested: Keycloak asks for `openid profile email` unless the
realm sets `defaultScope`, and verification binds only sensitive and
restricted scopes — so external, which is what a product whose users
bring their own identity needs, joins no queue. Publish out of testing
mode once the test-user list stops being the point, or refresh tokens go
on expiring after seven days and read as an application fault. Name the
app for the environment as well as the product, since each instance has
its own consent screen, and give the user support address a group rather
than a person — it is shown to users, while the contact addresses are
Google notifying you, and neither wants a personal account.

The realm keeps the placeholder pair it was imported with, whatever the
chart later says, so both values reach the running realm afterwards and
by different routes. The client id goes in over the Admin API, from the
realm-import Job, driven by the installation's `keycloak.googleClientId`
and carried in that Job's name — or a changed id leaves the same
completed Job and nothing applies it. The secret is never sent there at
all: the identity provider holds a vault expression, restored from the
committed definition rather than written back from what was read, since
the Admin API returns a configured secret masked and asterisks would
leave a realm looking entirely correct. Write the secret by hand into
`sec-<code>-<env>-<label>-google-oauth` and let an `ExternalSecret`
materialise it into the mount as `<realm>_<key>`, since Keycloak reads
that mount at startup — so install the operator and the store in earlier
sync waves than the bank, and restart Keycloak where a secret is stored
after the pod. Never read `401 invalid_client` on a rebuilt environment
as evidence the rebuild failed — it is the likelier cause and the wrong
conclusion.
See [google-sign-in](../../../docs/recipes/google-sign-in.md).

## A public zone needs proven ownership, and the registrar is touched once

Verify the domain before a public zone is created — Cloud DNS refuses a
zone whose name the calling identity has not verified, and says so as
`verifyManagedZoneDnsNameOwnership`. Verify as the operator account in
its own right, as a Domain property rather than a URL prefix, and add the
composition's identity as an Owner, since Full and Restricted grant
report access and confer no ownership. Verification belongs to an
identity rather than to a domain, so an existing
`google-site-verification` record is no evidence your account owns
anything, an absent Search Console property is no evidence the domain is
unverified, and an unattributed token is not yours to tidy away. Where
Google auto-verified through the DNS provider, add the TXT method
explicitly, or the delegation change ends the relationship the
verification rested on. Never leave the installation's identities
delegated from a personal account, and never regenerate a token to move
one: the same string is copied, and answers from both authorities across
the switch.

Inventory every record type at the registrar before moving a domain, and
the underscore-prefixed names with it, since `_dmarc` carries policy an
apex sweep does not show. Verification tokens, SPF and DMARC carry into
the new zone before the delegation moves; a placeholder site's records
and the registrar's one-click DNS endpoint do not. Check the new zone by
querying its assigned nameservers directly rather than a public
resolver, which still answers from the old authority, and check the
delegation itself against the registry's authority section, since a
referral carries the NS records there and a `+short` query reads as
empty.

Check for a DS record before delegating and, where one exists, unsign at
the registrar first: moving a signed domain while its DS names the old
keys makes every validating resolver read the new authority as forged,
taking the verification TXT down with it. Watch the parent registry
rather than the zone, expect the outage to start at the unsigning rather
than at the delegation, and take several spaced probes across more than
one resolver before calling recovery complete, since anycast nodes cache
independently and one clean answer proves nothing. Unsign first for a
domain not yet serving anything so the wait overlaps everything else,
last for one serving traffic to keep the window tight.

Replace all of the registrar's nameservers rather than some, leave its
old records in place as the way back, and do not re-enable DNSSEC there
afterwards. Never delete and recreate a zone to change it: the
nameservers change with it, the registrar does not follow, and each
fresh zone draws from a finite per-domain pool. Move the apex once
rather than delegating a subdomain per environment, and set a CAA record
naming the issuing CA.
See [cloud-dns](../../../docs/recipes/cloud-dns.md).

## A parent Application holds only kinds that already exist

Keep concrete resources out of a parent Application: anything whose
kind a child installs belongs in a child of its own, or the parent
fails building a task for an unknown kind and never applies the child
that would install it. Sync waves do not resolve a missing kind, and
`SkipDryRunOnMissingResource` skips the dry run rather than making an
apply succeed. Set `ServerSideApply=true` for charts with large CRDs,
whose client-side apply exceeds the annotation limit. Set retry budgets
that outlast an operator install, since an Application that exhausts
its retries waits for a revision change or a manual sync — a merged fix
does not reach it. A running retry loop is worse: an operation pins the
revision it started with and replays that revision's manifests, so read
`.operation.sync.revisions` rather than `status.sync.revisions`, which
shows only what would be synced next, and remove `.operation` to cancel
a loop replaying a fault already fixed. One object the API server
rejects fails the whole sync and leaves every well-formed resource
beside it `OutOfSync`, and server-side apply refuses an undeclared field
rather than dropping it — so a template that renders is not a template
that applies. Set `prune: false` where pruning would delete
something a missing file should not delete. Merge a change before
expecting Argo to apply it: it reads the revision an Application names,
never a working tree. Never rely on `lookup` to preserve a generated
value — Argo renders with `helm template`, where it returns nothing, so
the generate branch always wins and every sync applies a fresh value
over whatever the last one left. Generate such a value in the cluster
instead, from the same Job that registers it wherever its counterpart
lives, and let the chart declare the Secret without `data` so
server-side apply leaves the contents to whoever wrote them.
See [argocd](../../../docs/recipes/argocd.md).

## Argo reads a private repository as a GitHub App

Use a GitHub App, never a deploy key or a personal access token: it
belongs to the organisation rather than to a person, is revoked by
uninstalling it, and needs no SSH egress. Grant it Contents read-only
and install it only on the repositories Argo reads. Keep the private key
in a secret store and let an operator place the Secret, and add a new
key before deleting the one it replaces, since an App may hold two at
once. Never commit the `.pem` or pass it on a command line, and never
give the App webhook access or any write permission. One App may serve
several repositories in the same organisation where the same reader
should reach all of them.
See [argocd-github](../../../docs/recipes/argocd-github.md).

## A recipe fails loudly or not at all

Under `set -e`, `cmd && break`, `[[ test ]] && cmd` and a bare
`VAR=$(cmd)` whose command may fail each end the recipe rather than the
line — and before its first `echo`, so the symptom is an instant exit
with no output. Consume the failure you expect instead: `if cmd; then
break; fi`, or `|| true` where emptiness is handled explicitly. Capture
a command's output into a variable before piping it, since a pipeline
takes the last command's status and a denial otherwise reads as an
empty result. Use whatever the caller supplied and discover only what
they did not, because discovery fails where an argument would have
worked. Declare an overridable variable with `env_var_or_default`, and
name a recipe for what it acts on.
See [justfile-recipes](../../../docs/recipes/justfile-recipes.md).
