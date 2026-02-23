(ns com.repldriven.mono.fdb.bridge
  (:require
    [protojure.protobuf :as proto])
  (:import
    (com.google.protobuf MessageLite)))

(defn clj->java
  "Converts a Clojure map to a Java protobuf Message via wire bytes.
  parse-fn is typically #(JavaClass/parseFrom %) where JavaClass is
  the generated Java class e.g. PersonProto$Person."
  [parse-fn new-fn m]
  (parse-fn (proto/->pb (new-fn m))))

(defn java->clj
  "Converts a Java protobuf Message to a Clojure map via wire bytes.
  decode-fn is the protojure decoder e.g. pb->Person."
  [decode-fn ^MessageLite msg]
  (decode-fn (.toByteArray msg)))
