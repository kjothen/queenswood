(ns com.repldriven.mono.bank-restriction.store
  (:require
    [com.repldriven.mono.bank-restriction.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn new-restrictions
  "Persists a Restrictions record for the given owner.
  Returns the saved map or anomaly."
  [config owner-id opts]
  (let [{:keys [record-db record-store]} config
        {:keys [organization-id]} opts
        record (domain/new-restrictions owner-id
                                        organization-id
                                        opts)
        result (fdb/transact
                record-db
                record-store
                "restrictions"
                (fn [store]
                  (fdb/save-record
                   store
                   (schema/Restrictions->java record))))]
    (if (error/anomaly? result) result record)))

(defn get-restrictions
  "Returns the Restrictions record for the given owner-id,
  or nil if not found."
  [config owner-id]
  (let [{:keys [record-db record-store]} config]
    (error/try-nom
     :restriction/get
     "Failed to load restrictions"
     (fdb/transact
      record-db
      record-store
      "restrictions"
      (fn [store]
        (->> (fdb/query-records store
                                "Restrictions"
                                "owner_id"
                                owner-id)
             (mapv schema/pb->Restrictions)
             first))))))
