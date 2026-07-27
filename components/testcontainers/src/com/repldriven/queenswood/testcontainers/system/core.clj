(ns com.repldriven.queenswood.testcontainers.system.core
  (:require
    [com.repldriven.queenswood.testcontainers.system.components.fdb :as fdb]

    ;; Queenswood owns the FDB container; Kafka, Keycloak and the rest are
    ;; still mono's. Requiring it here means a rig gets every container
    ;; kind from this one interface — without it, `kafka/container` is
    ;; never registered and its instance is nil.
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.system.interface :as system]))

;; FDB testcontainer components. Registered under the same `:fdb` kind the
;; fdb component uses, so a rig writes `fdb/container` alongside
;; `fdb/record-db`; the other kind names do not overlap, so the two
;; registrations merge.
(system/defcomponents :fdb {:container fdb/container})
