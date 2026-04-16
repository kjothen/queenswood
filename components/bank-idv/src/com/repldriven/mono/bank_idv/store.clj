(ns com.repldriven.mono.bank-idv.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "idvs")

(def transact fdb/transact)

(defn save-idv
  "Saves an IDV and writes a changelog entry. Returns the
  IDV map or anomaly."
  [txn idv changelog]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (let-nom>
         [_ (fdb/save-record store (schema/Idv->java idv))
          _ (fdb/write-changelog store
                                 store-name
                                 (:verification-id idv)
                                 (schema/IdvChangelog->pb
                                  (assoc changelog
                                         :organization-id
                                         (:organization-id idv))))]
         idv)))
   :idv/save
   "Failed to save IDV"))

(defn find-idv
  "Loads an IDV by composite PK if it exists. Returns the
  IDV map, nil, or anomaly on I/O failure. For existence
  probes (e.g. watcher handlers)."
  [txn organization-id verification-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name)
                              organization-id
                              verification-id)
             schema/pb->Idv))
   :idv/find
   "Failed to load IDV"))

(defn get-idv
  "Loads an IDV by composite PK. Returns the IDV map or
  rejection anomaly if not found."
  [txn organization-id verification-id]
  (let-nom> [idv (find-idv txn organization-id verification-id)]
    (or idv
        (error/reject :idv/not-found
                      {:message "IDV not found"
                       :organization-id organization-id
                       :verification-id verification-id}))))
