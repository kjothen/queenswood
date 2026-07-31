(ns com.repldriven.queenswood.cash-account.changelog
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.java.io :as io]))

(def ^:private event-name "cash-account-status-changed")

;; Loaded from the classpath rather than the injected `avro/serde`: the
;; payload schema is a property of this brick, and `store.clj` only ever
;; receives a Txn, never the system config the serde arrives in.
(def ^:private schema
  (delay (avro/json->schema
          (slurp (io/resource
                  "schemas/cash-accounts/account-status-changed.avsc.json")))))

(defn status-changed
  "Build the shared-envelope changelog bytes for a cash-account status
  transition. `changelog` carries `:bank-id`, `:account-id`,
  `:status-before` and `:status-after`."
  [{:keys [bank-id account-id status-before status-after]}]
  (let-nom> [payload (avro/serialize @schema
                                     {:bank-id bank-id
                                      :account-id account-id
                                      :status-before status-before
                                      :status-after status-after})]
    (schema/ChangelogEvent->pb
     (utility/assoc-some
      {:event-id (str (utility/uuidv7))
       :dedup-key (str account-id ":" (name status-after))
       :event-name event-name
       :payload payload
       :causation-id account-id
       :ordering-key account-id
       :created-at (utility/now)}
      ;; Written inside the command's transaction, so this is the
      ;; `process-command` span — which is itself under the request. The
      ;; relay republishes it and the consumer's span joins that trace.
      :traceparent
      (telemetry/inject-traceparent)))))
