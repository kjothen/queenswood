# Cloud naming

<!-- tessl-plugin: deployment -->

## Problem

[ADR-0023](../adr/0023-installation-naming-and-access.md) decided that
names follow the Google Cloud security foundations guide, and listed
enough kinds to build the first installation. It is not a complete
inventory, and it never can be: a kind arrives before the list does.

The guide cannot fill the gap either. Its published PDF now serves a
stub, so its own table is reconstructible only from the Wayback Machine
or the Terraform example repository, and it covers nothing specific to
how this platform is built.

So a name gets invented at the moment somebody needs one, which is the
worst moment to be deciding a convention. Worse, most of these names
cannot be changed later: a project id is consumed permanently, and a
folder, cluster or bucket renames only by being rebuilt.

## Solution

### The rule

```
<prefix>-<code>-<env>-<label>[-<qualifier>]
```

- **prefix** — the kind, abbreviated. Fixed per kind, listed below.
- **code** — the installation's four-character code, chosen when it is
  created and carried in its manifest. `qw01` stands in for it
  everywhere below, and is an example rather than a constant: a second
  installation is `qw02`, and one built for somebody else is neither.
- **env** — one letter: `b` bootstrap, `c` common, `d` dev, `n` nonprod,
  `p` prod.
- **label** — what this one is for, within its kind and environment.
- **qualifier** — only where a name would otherwise collide or lose
  meaning: a region for anything regional, a uniqueness suffix where the
  id is globally unique.

A kind not listed below takes this shape. If it does not fit, that is
worth a paragraph in this recipe rather than a decision in a pull
request nobody reads twice.

### Why the code precedes the environment

The guide has no installation code — it assumes one organisation divided
into environments. Inserting the code after the prefix keeps the guide's
`<env>-<label>` tail intact, and sorts an installation's resources
together, which matches the folder being the unit that gets created and
destroyed as a whole.

### The exceptions, and why each is one

- **Folder** — `fldr-qw01`. No environment: the folder *is* the
  installation and holds every environment inside it.
- **Seed project** — `prj-b-seed-<suffix>`. No code: it holds the
  identity that creates installations, so it exists before any folder
  and one serves the whole organisation.
- **Service account** — no environment where the identity is one per
  installation, following the guide, which names an identity for its job
  rather than its tier: `sa-qw01-platform` runs the whole installation,
  `sa-qw01-boot` sits outside the folder entirely. An identity belonging
  to one cluster takes the environment, because every cluster wants the
  same job done and the name has to say whose:
  `sa-qw01-c-nodes` for the management cluster's nodes,
  `sa-qw01-c-secrets` for the operator reading secrets on it, and
  `sa-qw01-d-nodes` or `sa-qw01-p-nodes` for an instance's.
- **Group** — `grp-gcp-<capability>` at the organisation,
  `grp-gcp-qw01-<capability>` for an installation. The `gcp` segment is
  the guide's, marking these as cloud access groups within a directory
  that holds other kinds.
- **Custom role** — `rl_<function>`, underscores rather than hyphens: a
  custom role id takes letters, numbers, underscores and periods, and
  GCP rejects anything else. Defined at the organisation where it is
  shared, and in a project where an installation must own it outright —
  an installation built in an organisation somebody else administers
  cannot define one above its own folder.

### The inventory

Resource-manager and identity:

- **folder** — `fldr-qw01`
- **project** — `prj-qw01-c-mgmt-<suffix>`. The suffix is six hex
  characters, because a project id is globally unique.
- **API enablement** — `svc-qw01-c-<api>`, the API's first label:
  `svc-qw01-c-iam`, `svc-qw01-c-container`. The management project ends
  the name there because there is one of it. An instance keeps its
  label and takes the API as the qualifier —
  `svc-qw01-n-test-compute` — since two nonprod instances would
  otherwise both claim `svc-qw01-n-compute`.
- **service account** — `sa-qw01-platform`
- **Kubernetes objects** — two rules, by how the name is arrived at. A
  name a composition builds from the installation's code, or that
  another resource then references by that constructed name, follows
  the scheme above: `sa-qw01-secrets`, `qw01-mgmt`,
  `sec-qw01-c-github-app`. A cluster-singleton — a chart, an Argo
  Application, a configuration nothing constructs a name for — is named
  in plain words instead: `management-plane`, `crossplane-providers`,
  `external-secrets`. There is one installation per management cluster,
  so a code on those would distinguish nothing.
- **custom role** — `rl_<function>`, with underscores. A custom role id
  takes letters, numbers, underscores and periods, and no hyphens, so
  this is the one kind that cannot follow the separator every other name
  uses. Where the same role is also a Kubernetes object, its Kubernetes
  name keeps hyphens and the id is the external name — `rl-pod-log-reader`
  naming `rl_pod_log_reader`.
- **group** — `grp-gcp-qw01-platform-viewer`

Network:

- **VPC** — `vpc-qw01-c-mgmt`
- **subnet** — `sb-qw01-c-mgmt-euw2`. Regional, so the region is
  abbreviated and carried: GCP publishes no short form, so the
  installation manifest states it.
- **firewall rule** — `fw-qw01-c-mgmt-<direction>-<action>-<target>`
- **route** — `rt-qw01-c-mgmt-<destination>`
- **Cloud Router** — `cr-qw01-c-mgmt-euw2`
- **Cloud NAT** — `nat-qw01-c-mgmt-euw2`
- **static address** — `addr-qw01-c-<label>`
- **DNS zone** — `dz-qw01-<env>-<label>`

Compute and data:

- **GKE cluster** — `qw01-c-mgmt`, without a kind prefix: clusters are
  never listed beside other kinds, and GKE prefixes every name it
  derives from one with `gke-` already, so ours would only double it
- **node pool** — `np-<label>`, `np-primary` where there is one, never
  `np-default`: GKE creates its own `default-pool` on every cluster, and
  a name that echoes it leaves a reader guessing whose is whose. A child
  of a cluster, so its parent already settles the installation and the
  environment. GKE builds node names from both, so the
  saving is visible everywhere a node is named:
  `gke-qw01-c-mgmt-np-primary-d5a1cdac-mx0x` rather than
  `gke-gke-qw01-c-mgmt-np-qw01-c-mgmt-d5a1cdac-mx0x`, forty against
  forty-eight of a permitted sixty-three
- **CloudSQL instance** — `sql-qw01-<env>-<label>`
- **bucket** — `bkt-qw01-<label>`
- **secret** — `sec-qw01-<env>-<label>`

### Installation qw01, as built

Above the folder and named by whoever owns the organisation, so no
concern of this recipe: the organisation itself, and a billing account.
Their identifiers are deliberately not reproduced here — a public
document should carry names, which is what this is about, and not
account identifiers, which are what somebody pretexting a support call
would want.

Outside the folder, one per organisation:

- seed project — `prj-b-seed-xxxxxx`
- boot identity — `sa-qw01-boot@prj-b-seed-xxxxxx.iam.gserviceaccount.com`

The installation itself:

- folder — `fldr-qw01`
- management project — `prj-qw01-c-mgmt-xxxxxx`
- APIs — `svc-qw01-c-iam`, `svc-qw01-c-iamcredentials`,
  `svc-qw01-c-serviceusage`, `svc-qw01-c-resourcemanager`,
  `svc-qw01-c-compute`, `svc-qw01-c-container`
- VPC — `vpc-qw01-c-mgmt`
- subnet — `sb-qw01-c-mgmt-euw2`
- cluster — `qw01-c-mgmt`
- node pool — `np-primary`
- platform identity —
  `sa-qw01-platform@prj-qw01-c-mgmt-xxxxxx.iam.gserviceaccount.com`
- node identity —
  `sa-qw01-c-nodes@prj-qw01-c-mgmt-xxxxxx.iam.gserviceaccount.com`
- secrets identity —
  `sa-qw01-c-secrets@prj-qw01-c-mgmt-xxxxxx.iam.gserviceaccount.com`

The worked example is what a new installation is built to. `qw01` was
built before the cluster and node pool names were shortened, and keeps
`gke-qw01-c-mgmt` and `np-qw01-c-mgmt`: renaming either destroys and
rebuilds the cluster, which is not worth doing to a working one.

Its capabilities, in a directory we happen to own:

- `grp-gcp-qw01-platform-viewer@queenswood.io`
- `grp-gcp-qw01-platform-admin@queenswood.io`
- `grp-gcp-qw01-cluster-admin@queenswood.io`
- `grp-gcp-qw01-secrets-admin@queenswood.io`

Only three values here were chosen rather than derived: the code
`qw01`, and the two project suffixes, which are random because a
project id is globally unique. Everything else follows.

### Kubernetes names are the same names

A Crossplane managed resource is named for what it manages, so
`kubectl get managed` and the Cloud Console read alike — the composite
is `qw01`, and the resources under it are `fldr-qw01`,
`prj-qw01-c-mgmt`, `gke-qw01-c-mgmt`. This costs an explicit patch per
resource where one format string would otherwise do, and it is what
makes a console tab and a terminal describe the same thing.

A composed resource whose name is generated cannot be referenced by
another, so this is load-bearing rather than cosmetic.

### Names the manifest derives

Nothing in a composition hard-codes an installation's code. The XR
carries `spec.code`, every name patches from it, and the region
abbreviation comes from `spec.regionCode` beside it. A second
installation is that field and nothing else.

## Rules

**MUST:**

- Derive every composed name from `spec.code`, never from the
  composite's own name.
- Carry the environment letter on anything scoped to one, including the
  kinds the guide does not list.
- Add a kind to the inventory above when you name one that is not
  already there.

**MUST NOT:**

- Bake an environment name, a domain or a customer name into a resource
  name. The code and the environment letter are the only identifiers a
  name carries.
- Rename a project id, a folder or a bucket in place. None of them
  supports it — the id is consumed and the resource is rebuilt.
- Invent a prefix where the inventory already has one.

**MAY:**

- Give a name a qualifier where it would otherwise collide, most often
  a region.
- Keep a name a supplier chose, where a folder or project is handed to
  us already named.

## References

- [ADR-0023](../adr/0023-installation-naming-and-access.md) — the
  decision this recipe carries out, and the reasoning behind the code.
- [ADR-0022](../adr/0022-cloud-foundation-and-environment-lifecycle.md)
  — the folder as an installation, and its lifecycle.
- [crossplane-app-deployment](crossplane-app-deployment.md) — what a
  deployment builds, and the two identities that build it.
