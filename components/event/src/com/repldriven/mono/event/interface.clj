(ns com.repldriven.mono.event.interface)

(comment
  (require '[clojure.java.io :as io]
           '[abracad.avro :as avro]
           '[abracad.io :as avro.io]
           '[abracad.avro.codec :as avro.codec]
           '[cddr.edn-avro :as edn.avro]
           '[deercreeklabs.lancaster :as l]
           '[clj-ulid :as ulid]
           '[tick.core :as tick])
  (import '(org.apache.avro.util RandomData))
  (def schema
    (avro/parse-schema (avro.io/read-json-resource "schema/cloudevent.avsc")))
  (def evt
    {:attribute {"id" (ulid/ulid)
                 "source" "persistent://tenant/namespace/event"}
     :data {"hello" "world"}})
  (avro/binary-encoded schema evt)
  (def lschema (l/json->schema (slurp (io/resource "schema/cloudevent.avsc"))))
  (def msg {:attribute {"id" (ulid/ulid)} :data {"hello" "world"}})
  (l/deserialize-same lschema (l/serialize lschema msg))
  (seq (RandomData. schema 1))
  (def cloudevt
    {:attribute {"id" (ulid/ulid)
                 ;; :source "persistent://tenant/namesapce/event"
                 ;; :specversion "1.0" :type
                 ;; "com.repldriven.mono.topic.v1.event" :datacontentype
                 ;; "application/cloudevents+avro" :dataschema
                 ;; "https://www.github.com/kjothen/mono/components/event/resources/schema/cloudevent.avsc"
                 ;; :subject "example"
                 ;; :time (tick/format (tick/formatter :iso-instant)
                 ;; (tick/now))
                }
     :data "some data"})
  (avro/json-encoded schema cloudevt))
