(ns com.repldriven.mono.bank-idv.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "idvs")

(def transact fdb/transact)
(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-idv
  [txn idv changelog]
  (let [result (fdb/transact
                txn
                (fn [txn]
                  (let [store (fdb/open txn store-name)]
                    (let-nom>
                      [_ (fdb/save-record store (schema/Idv->java idv))
                       _ (fdb/write-changelog
                          store
                          store-name
                          (:verification-id idv)
                          (schema/IdvChangelog->pb
                           (assoc changelog
                                  :organization-id
                                  (:organization-id idv))))]
                      idv)))
                :idv/save
                "Failed to save IDV")]
    (if (uniqueness-violation? result)
      (error/reject :idv/already-verified
                    {:message "IDV already exists for party"
                     :party-id (:party-id idv)})
      result)))

(defn get-idv
  [txn organization-id verification-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      organization-id
                                      verification-id)]
       (schema/pb->Idv record)
       (error/reject :idv/not-found
                     {:message "IDV not found"
                      :organization-id organization-id
                      :verification-id verification-id})))
   :idv/get
   "Failed to load IDV"))

(defn get-idv-by-party
  [txn party-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/query-record (fdb/open txn store-name)
                               "Idv"
                               "party_id"
                               party-id
                               {:index "Idv_by_party"})
             schema/pb->Idv))
   :idv/get-by-party
   "Failed to look up IDV by party"))
