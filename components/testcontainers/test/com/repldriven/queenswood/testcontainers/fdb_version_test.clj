(ns com.repldriven.queenswood.testcontainers.fdb-version-test
  "Guards the FDB server version against versions.json.

  versions.json is the single source for the toolchain — the native client
  in flake.nix, the client library in CI, the Dockerfile and Helm copies
  (asserted by scripts/check-versions.sh). The testcontainers server version
  is a Clojure constant that script cannot reach, so it is asserted here
  instead.

  FDB requires a compatible protocol version between client and cluster, so
  a mismatch is not cosmetic: tests fail at connect time with an error that
  names neither side."
  (:require
    [com.repldriven.queenswood.testcontainers.system.components.fdb :as fdb]

    [com.repldriven.mono.json.interface :as json]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

(deftest fdb-version-matches-versions-json-test
  (testing "the testcontainers FDB server version matches versions.json"
    (let [f (io/file "versions.json")]
      (is (.exists f)
          (str "versions.json not found at "
               (.getAbsolutePath f)
               " — this test reads it relative to the workspace root"))
      (when (.exists f)
        (let [declared (-> (slurp f)
                           (json/read-str)
                           (get-in ["foundationdb" "version"]))]
          (is (= declared fdb/fdb-version)
              (str "fdb-version in the testcontainers component is "
                   fdb/fdb-version
                   " but versions.json declares " declared
                   ". The FDB client and the testcontainers server share a "
                   "protocol version and must be bumped together.")))))))
