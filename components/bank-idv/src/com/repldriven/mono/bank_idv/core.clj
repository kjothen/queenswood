(ns com.repldriven.mono.bank-idv.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- publish-submit-idv-check
  "Fire-and-forget: publishes a `submit-idv-check` command on
  the IDV-provider command channel. No-ops when the bus, schema,
  or channel is missing — lets brick tests run without a bus."
  [config idv data]
  (let [{:keys [bus schemas idv-command-channel]} config
        {:keys [organization-id verification-id party-id]} idv
        {:keys [given-name family-name date-of-birth]} data
        schema (clojure.core/get schemas "submit-idv-check")]
    (when (and bus schema idv-command-channel)
      (let [payload (avro/serialize
                     schema
                     {:organization-id organization-id
                      :verification-id verification-id
                      :party-id party-id
                      :first-name (or given-name "")
                      :last-name (or family-name "")
                      :date-of-birth (when date-of-birth
                                       (str date-of-birth))})]
        (if (error/anomaly? payload)
          (log/error "Failed to serialize submit-idv-check" payload)
          (let [envelope {:command "submit-idv-check"
                          :id (str (utility/uuidv7))
                          :correlation-id (str (utility/uuidv7))
                          :causation-id verification-id
                          :payload payload}]
            (message-bus/send bus idv-command-channel envelope)))))))

(defn initiate
  "Initiates IDV: writes the IDV record (status pending) and
  publishes a `submit-idv-check` command on the IDV-provider
  channel. Mirrors `bank-payment.core/submit-outbound` — a domain
  write followed by a fire-and-forget publish to the upstream
  adapter.

  `config` carries both the FDB seam (`:record-db`,
  `:record-store`, or a Txn for composition with an outer
  transaction) and the bus seam (`:bus`, `:schemas`,
  `:idv-command-channel`). The publish is skipped silently when
  any of those are absent.

  Returns the saved IDV map or anomaly."
  [config data]
  (let-nom>
    [idv (domain/new-idv data)
     saved (store/save-idv config
                           idv
                           {:verification-id (:verification-id idv)
                            :status-after (:status idv)})]
    (publish-submit-idv-check config saved data)
    saved))

(defn get
  "Returns the current IDV or rejection anomaly."
  [txn data]
  (let [{:keys [organization-id verification-id]} data]
    (store/get-idv txn organization-id verification-id)))
