(ns com.repldriven.mono.fdb.keyspace
  (:import
    (com.apple.foundationdb.record.provider.foundationdb.keyspace
     DirectoryLayerDirectory
     KeySpace)))

(defn records-path
  "Returns the KeySpacePath for the named record store."
  [store-name]
  (-> (KeySpace. (into-array DirectoryLayerDirectory
                             [(DirectoryLayerDirectory. store-name)]))
      (.path store-name store-name)))
