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

The component includes a testcontainer setup using the
[testcontainers-foundationdb](https://github.com/aleris/testcontainers-foundationdb)
library for Linux/CI environments.

**macOS Limitation:** The testcontainers-foundationdb library (v1.1.0) has a
packaging issue on macOS - its bundled native library references a hardcoded
build-time path that doesn't exist on user systems. For local macOS
development, use a native FDB installation instead:

1. Install FDB: https://github.com/apple/foundationdb/releases/tag/7.3.27
2. Use native cluster file path in your system config

The testcontainer works correctly on Linux CI environments where Docker
networking is direct and the native library packaging doesn't cause issues.
