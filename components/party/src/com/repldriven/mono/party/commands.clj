(ns com.repldriven.mono.party.commands
  (:require
    [com.repldriven.mono.party.domain :as domain]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn- save-party
  "Saves party to store, writes changelog entry with
  serialized changelog proto, returns protobuf record or
  anomaly."
  [store party changelog]
  (error/let-nom>
    [_ (fdb/save-record store (schema/Party->java party))
     _ (fdb/write-changelog store
                            "parties"
                            (:party-id party)
                            (schema/PartyChangelog->pb changelog))]
    (schema/Party->pb party)))

(defn- save-person-identification
  "Saves person-identification to store."
  [store person-id]
  (fdb/save-record store
                   (schema/PersonIdentification->java person-id)))

(defn- create
  "Creates a party and person-identification in a single
  transaction. Returns protobuf party record or anomaly."
  [config data]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact-multi
     record-db
     record-store
     (fn [open-store]
       (let [party (domain/new-party data)
             person-id (domain/new-person-identification
                        data
                        (:party-id party))
             party-store (open-store "parties")
             pid-store (open-store "person-identifications")]
         (error/let-nom>
           [_ (save-person-identification pid-store person-id)
            result (save-party party-store
                               party
                               {:party-id (:party-id party)
                                :status-after (:status party)})]
           result))))))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "party")
                                (schema/pb->Party result))})))

(defn create-party
  "Creates a new party."
  [config data]
  (->response config (create config data)))
