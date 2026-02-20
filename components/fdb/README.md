# FDB Component

FoundationDB integration component providing client lifecycle management and
system component registration.

## Requirements

The FoundationDB Java client requires the native `libfdb_c.dylib` library to
be installed on the system. Without it, the client will fail at runtime when
attempting to connect to FDB.

### Installation

**macOS:**
Download and install from https://github.com/apple/foundationdb/releases/tag/7.3.27

The Nix flake in this repo automatically installs the native library via a
custom derivation that fetches the pre-built binary.

**Linux:** Install via package manager or from the releases page above.

## Usage

The component registers the `:fdb/database` system component:

```clojure
{:fdb/database {:cluster-file-path "/path/to/fdb.cluster"
                :api-version 730}}  ; optional, defaults to 730
```

Access the database instance:

```clojure
(system/with-system [sys (system-config)]
  (let [db (system/instance sys [:fdb :database])]
    ;; Use FDB Java client API directly on db
    (.run db
          (reify java.util.function.Function
            (apply [_ tr]
              ;; transaction operations
              )))))
```

## Testing

### macOS Limitation

FDB testcontainers **do not work on macOS** due to Docker Desktop networking
architecture. Container IPs (e.g., `172.17.0.x`) run in a Linux VM and are not
routable from the macOS host.

The [reference implementation](https://github.com/aleris/testcontainers-foundationdb)
solves this with a Socat proxy container that bridges the networking gap, but
this adds significant complexity.

**Workaround for macOS:**
1. Download FDB from https://github.com/apple/foundationdb/releases/tag/7.3.27
2. Install and start the server
3. Use native cluster file path in tests instead of testcontainer config

**Linux:** Testcontainers should work as container IPs are directly routable.

### Component Test

The included test verifies the component structure loads correctly without
requiring a running FDB instance. For full integration testing with actual
transactions, use a native FDB installation.
