(ns com.repldriven.mono.fdb.metadata
  (:import
    (com.apple.foundationdb.record RecordMetaData)
    (com.apple.foundationdb.record.metadata Index Key$Expressions)
    (com.repldriven.mono.schema SchemaProto)))

(defn build-persons-metadata
  "Builds RecordMetaData for the persons store.
  Registers Person with id as primary key and an index on email."
  []
  (let [b (-> (RecordMetaData/newBuilder)
              (.setRecords (SchemaProto/getDescriptor)))]
    (-> (.getRecordType b "Person")
        (.setPrimaryKey (Key$Expressions/field "id")))
    (.addIndex b "Person" (Index. "email_idx" (Key$Expressions/field "email")))
    (.build b)))
