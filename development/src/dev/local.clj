(ns dev.local)

(comment
  ;; open a debug portal
  (require '[portal.api :as p])
  (def p (p/open {:launcher :vs-code}))
  (add-tap #'p/submit) ; Add portal as a tap> target
  (tap> "Opened tap")
  (p/close)
  )

(comment
  ;; experiment with pulsar
  (require '[com.repldriven.mono.pulsar.interface :as pulsar]
           '[com.repldriven.mono.env.interface :as env]
           '[clojure.java.io :as io]
           '[donut.system :as ds])

  (env/set-env! (io/resource "pulsar/test-env.edn") :default)
  (def system-config (pulsar/create-system (get-in @env/env [:system :pulsar])))
  (tap> system-config)

  (def booted-system (ds/start system-config nil #{[:pulsar :admin] [:pulsar :service-url]}))
  (tap> booted-system)

  (def admin (get-in booted-system [::ds/instances :pulsar :admin]))
  (pulsar/ensure-topic admin "persistent://tenant/namespace/identity")

  (def system (ds/start booted-system))
  (ds/stop system)
  )
