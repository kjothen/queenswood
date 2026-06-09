(ns com.repldriven.mono.bank-api.jobs.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private periodicity-enum
  (coercion/enum-coercion {"daily" :scheduler-periodicity-daily
                           "monthly" :scheduler-periodicity-monthly
                           "yearly" :scheduler-periodicity-yearly}
                          :scheduler-periodicity-unknown))

(def ^:private task-kind-enum
  (coercion/enum-coercion {"accrue" :scheduler-task-kind-accrue
                           "capitalize" :scheduler-task-kind-capitalize
                           "account-migration"
                           :scheduler-task-kind-account-migration}
                          :scheduler-task-kind-unknown))

(def ^:private run-status-enum
  (coercion/enum-coercion {"running" :scheduler-run-status-running
                           "succeeded" :scheduler-run-status-succeeded
                           "failed" :scheduler-run-status-failed}
                          :scheduler-run-status-unknown))

(def ^:private trigger-source-enum
  (coercion/enum-coercion {"scheduled" :scheduler-trigger-source-scheduled
                           "forced" :scheduler-trigger-source-forced}
                          :scheduler-trigger-source-unknown))

(def periodicity-enum-schema (:enum-schema periodicity-enum))
(def task-kind-enum-schema (:enum-schema task-kind-enum))
(def run-status-enum-schema (:enum-schema run-status-enum))
(def trigger-source-enum-schema (:enum-schema trigger-source-enum))
