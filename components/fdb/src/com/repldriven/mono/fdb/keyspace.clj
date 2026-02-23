(ns com.repldriven.mono.fdb.keyspace
  (:import
    (com.apple.foundationdb.record.provider.foundationdb.keyspace
     DirectoryLayerDirectory
     KeySpace)))

;; Each store gets its own KeySpace. Multiple DirectoryLayerDirectory
;; entries cannot share the same level in one KeySpace (they would
;; overlap on type), so we create one per store.

(defn- store-keyspace
  [store-name]
  (KeySpace. (into-array DirectoryLayerDirectory
                         [(DirectoryLayerDirectory. store-name)])))

(def ^:private keyspaces
  {"persons" (store-keyspace "persons")
   "address-books" (store-keyspace "address-books")})

(defn records-path
  "Returns the KeySpacePath for the named record store."
  [store-name]
  (let [ks (get keyspaces store-name)]
    (when-not ks (throw (ex-info "Unknown keyspace" {:store store-name})))
    (.path ks store-name store-name)))
