(ns com.repldriven.queenswood.fdb.keyspace
  (:import
    (com.apple.foundationdb.record.provider.foundationdb.keyspace
     DirectoryLayerDirectory
     KeySpace)))

(defn scoped
  "Qualifies name with prefix, or returns it unchanged when prefix is
  blank or nil. A blank prefix must leave the name byte-identical:
  every existing deployment's records, changelog and cursors are keyed
  by the unqualified form."
  [prefix name]
  (if (seq prefix)
    (str prefix "." name)
    name))

(defn path
  "Returns the KeySpacePath for the given name."
  [name]
  (-> (KeySpace. (into-array DirectoryLayerDirectory
                             [(DirectoryLayerDirectory. name)]))
      (.path name name)))
