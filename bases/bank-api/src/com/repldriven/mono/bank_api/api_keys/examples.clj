(ns com.repldriven.mono.bank-api.api-keys.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def registry (examples-registry []))

(def ApiKey
  {:id "key_01JMABC123" :key-prefix "sk_live_" :raw-key "sk_live_abc123def456"})
