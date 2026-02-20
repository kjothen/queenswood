# FDB Component

FoundationDB integration component providing database lifecycle management,
system component registration, and key-value operations.

## Requirements

The FoundationDB Java client requires the native `libfdb_c` library to be
installed on the host. Without it, the client will fail at runtime.

### Installation

**macOS (with Nix):** The Nix flake provides the native library automatically.

**macOS (without Nix):** `brew install foundationdb`

**Linux:** Download and install `foundationdb-clients_7.3.27-1_amd64.deb`
from https://github.com/apple/foundationdb/releases/tag/7.3.27

## Usage

The component registers `:fdb/cluster-file-path` and `:fdb/database` system
components. Include via `testcontainers/fdb-test.yml` in tests, or configure
directly with a cluster file path in production:

```yaml
system:
  fdb:
    cluster-file-path: /path/to/fdb.cluster
    database:
      api-version: 730  # optional, defaults to 730
```

Access the database and use the interface:

```clojure
(system/with-system [sys (system-config)]
  (let [db (system/instance sys [:fdb :database])]
    (fdb/set db "key" "value")
    (fdb/get db "key")))
```

## Testing

Tests use the `earth.adi/testcontainers-foundationdb` testcontainer, which
works on both macOS and Linux provided `libfdb_c` is installed on the host.
