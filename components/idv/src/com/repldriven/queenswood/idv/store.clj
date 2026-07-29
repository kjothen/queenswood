(ns com.repldriven.queenswood.idv.store
  (:require
    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(def ^:private store-name "idvs")

(def transact fdb/transact)
(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-idv
  [txn idv changelog]
  (fdb/transact
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
                     :bank-id (:bank-id idv)
                     :party-id (:party-id idv))))]
         idv)))
   :idv/save
   "Failed to save IDV"))

(defn get-idv
  [txn bank-id verification-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name)
                              bank-id
                              verification-id)
             schema/pb->Idv))
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
