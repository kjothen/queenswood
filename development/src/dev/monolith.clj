(ns dev.monolith
  "Start the system as a modular monolith and testcontainers:
   * start docker (just docker-start),
   * start repl (just repl),
   * load this namespace, and evaluates lines from the comment block

   After the system has started:
   * start web console (just console-start), login and explore
   
   NOTE: on a fresh install, it may take several minutes to download
         required images for FoundationDB, Kafka, Keycloak, etc"
  (:require
    [com.repldriven.queenswood.testcontainers.interface]
    [com.repldriven.queenswood.monolith.main :as main]))

(comment
  (def sys (main/start "classpath:monolith/application-test.yml" :dev))
  (tap> sys)
  (main/stop sys)
  :-)

(comment
  (require '[dev.inspect :as i])
  ;; rank component instances by retained heap (infra components that
  ;; hold FDB/Jetty/Pulsar lambdas come back :unmeasurable)
  (i/instances-by-size sys)
  ;; size just your data — measurable, and tiny next to its print size
  (i/mem-size (get-in sys [:donut.system/instances :avro :serde]))
  (println (i/mem-footprint (get-in sys
                                    [:donut.system/instances :avro :serde])))
  :-)
