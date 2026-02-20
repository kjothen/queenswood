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

**Note:** FDB testcontainers don't work reliably on macOS due to Docker
networking limitations (canonical port mismatch between mapped and internal
ports). The component test only verifies the structure loads correctly.

For full integration testing, run FDB natively:
1. Install FDB locally (via the releases page or package manager)
2. Start fdbserver: `fdbserver -p auto:4500`
3. Create a cluster file pointing to `127.0.0.1:4500`
4. Configure your test system to use that cluster file path
