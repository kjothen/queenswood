(ns com.repldriven.mono.bank-party.core
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb])
  (:import
    (com.apple.foundationdb.record RecordIndexUniquenessViolation)))

(defn- save-party
  "Saves party to store, writes changelog entry with
  serialized changelog proto, returns protobuf record or
  anomaly."
  [store party changelog]
  (let-nom> [_ (fdb/save-record store (schema/Party->java party))
             _ (fdb/write-changelog store
                                    "parties"
                                    (:party-id party)
                                    (schema/PartyChangelog->pb changelog))]
    (schema/Party->pb party)))

(defn- uniqueness-violation?
  "Returns true if anomaly was caused by a
  RecordIndexUniquenessViolation."
  [anomaly]
  (when (error/anomaly? anomaly)
    (loop [ex (:exception (error/payload anomaly))]
      (cond
       (nil? ex)
       false

       (instance? RecordIndexUniquenessViolation ex)
       true

       :else
       (recur (.getCause ex))))))

(defn- create-person
  "Creates a person party with person-identification and
  optional national-identifier in a single transaction."
  [txn data]
  (fdb/transact txn
                (fn [txn]
                  (let [party (domain/new-party data)
                        party-id (:party-id party)
                        person-id (domain/new-person-identification data
                                                                    party-id)
                        ni (:national-identifier data)]
                    (let-nom>
                      [_ (fdb/save-record
                          (fdb/open txn "person-identifications")
                          (schema/PersonIdentification->java person-id))
                       _ (when ni
                           (fdb/save-record
                            (fdb/open txn "party-national-identifiers")
                            (schema/PartyNationalIdentifier->java
                             (domain/new-party-national-identifier
                              ni
                              (:organization-id party)
                              party-id))))
                       result (save-party (fdb/open txn "parties")
                                          party
                                          {:organization-id
                                           (:organization-id party)
                                           :party-id party-id
                                           :status-after (:status party)})]
                      result)))))

(defn- create-internal
  "Creates an internal party — no person-identification or
  national-identifier."
  [txn data]
  (fdb/transact txn
                (fn [txn]
                  (let [party (domain/new-party data)]
                    (save-party (fdb/open txn "parties")
                                party
                                {:organization-id (:organization-id party)
                                 :party-id (:party-id party)
                                 :status-after (:status party)})))))

(defn new-party
  "Creates a party. Person parties include
  person-identification and optional national-identifier.
  Internal and organization parties skip both. Returns
  protobuf party record or anomaly."
  [txn data]
  (let [result (if (= :party-type-person (:type data))
                 (create-person txn data)
                 (create-internal txn data))]
    (if (uniqueness-violation? result)
      (error/reject :party/duplicate-national-identifier
                    "National identifier already exists")
      result)))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "party") (schema/pb->Party result))})))

(defn create-party
  "Creates a new party."
  [config data]
  (->response config (new-party config data)))
