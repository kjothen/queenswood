(ns com.repldriven.queenswood.scheduler.system
  (:require
    [com.repldriven.queenswood.scheduler.core :as core]

    [com.repldriven.mono.system.interface :as system]))

;; Registers a cronut trigger per enabled job at startup. The
;; `:scheduler/scheduler` component owns the Quartz lifecycle; this
;; runner only wires triggers against it. The resolved config map
;; (record-db / record-store / schemas / scheduler) is the instance,
;; so consumers can run jobs through it.
(def ^:private runner
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (do (core/register-all! config) config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component
                   :scheduler system/required-component}
   :system/instance-schema some?})

(system/defcomponents :bank-scheduler {:runner runner})
