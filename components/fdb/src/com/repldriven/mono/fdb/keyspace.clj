(ns com.repldriven.mono.fdb.keyspace
  (:import
    (com.apple.foundationdb.record.provider.foundationdb.keyspace
     DirectoryLayerDirectory
     KeySpace)))

(def ^:private ks
  (KeySpace. (into-array DirectoryLayerDirectory
                         [(DirectoryLayerDirectory. "persons")
                          (DirectoryLayerDirectory. "address-books")])))

(defn records-path
  "Returns the KeySpacePath for the named record store."
  [store-name]
  (.path ks store-name))
