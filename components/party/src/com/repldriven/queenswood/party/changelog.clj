(ns com.repldriven.queenswood.party.changelog
  (:require
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.java.io :as io]))

(def ^:private event-name "party-status-changed")

;; Loaded from the classpath rather than the injected `avro/serde`: the
;; payload schema is a property of this brick, and `store.clj` only ever
;; receives a Txn, never the system config the serde arrives in.
(def ^:private schema
  (delay (avro/json->schema
          (slurp (io/resource
                  "schemas/party/party-status-changed.avsc.json")))))

(defn status-changed
  "Build the shared-envelope changelog bytes for a party status
  transition. `changelog` carries `:bank-id`, `:party-id`,
  `:status-before` and `:status-after`."
  [{:keys [bank-id party-id status-before status-after]}]
  (let-nom> [payload (avro/serialize @schema
                                     {:bank-id bank-id
                                      :party-id party-id
                                      :status-before status-before
                                      :status-after status-after})]
    (schema/ChangelogEvent->pb
     (utility/assoc-some
      {:event-id (str (utility/uuidv7))
       :dedup-key (str party-id ":" (name status-after))
       :event-name event-name
       :payload payload
       :causation-id party-id
       :ordering-key party-id
       :created-at (utility/now)}
      ;; Written inside the command's transaction, so this is the
      ;; `process-command` span — which is itself under the request. The
      ;; relay republishes it and the consumer's span joins that trace.
      :traceparent
      (telemetry/inject-traceparent)))))
