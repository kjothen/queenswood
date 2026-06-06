(ns dev.bank-monolith
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.bank-monolith.main :as main]))

;; before starting the system:
;; * on Mac OS X, start docker (just start-docker),
;; * start repl (just repl),
;; * connect the repl to your IDE and evaluate file
;; after starting the system:
;; * once Jetty is listening, run the bank-app (just start-bank-app),
;;   and browse to the advertised port, e.g. http://localhost:5173.
;;   the openapi3 documentation can be viewed at http://localhost:8080
;; NOTE: on a fresh install, it may take several minutes to download
;; required images for FDB, Pulsar, etc

(comment
  (def sys (main/start "classpath:bank-monolith/application-test.yml" :dev))
  (tap> sys)
  (main/stop sys)

  (require '[dev.inspect :as i])
  ;; rank component instances by retained heap (infra components that
  ;; hold FDB/Jetty/Pulsar lambdas come back :unmeasurable)
  (i/instances-by-size sys)
  ;; size just your data — measurable, and tiny next to its print size
  (i/mem-size (get-in sys [:donut.system/instances :avro :serde]))
  (println (i/mem-footprint (get-in sys
                                    [:donut.system/instances :avro :serde])))
  ;
)
