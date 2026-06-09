(ns com.repldriven.mono.bank-scheduler.interface
  "Per-bank job scheduler. Jobs are preset sequences of tasks (accrue,
  capitalize, future account-migration) seeded into a bank at
  provisioning and editable by the operator within task-defined
  periodicity limits. The `:bank-scheduler/runner` system component
  registers a cronut trigger per enabled job at startup; FDB is the
  source of truth for jobs and runs.

  `config` throughout is the FDB+interfaces map (`:record-db`,
  `:record-store`, `:schemas`, and — for trigger edits — `:scheduler`)."
  (:require
    com.repldriven.mono.bank-scheduler.system

    [com.repldriven.mono.bank-scheduler.core :as core]))

(defn seed-jobs
  "Seed `bank-id`'s default scheduled jobs (FDB only — no triggers).
  Idempotent by `[bank_id, job_id]`. Run inside the caller's
  transaction (e.g. bank creation) so a failure rolls back with it.

  Args:
  - txn: an open FDB transaction or a config map.
  - bank-id: the bank to seed jobs for."
  [txn bank-id]
  (core/seed-jobs txn bank-id))

(defn force-start
  "Run `job-id` now (trigger source forced). Returns the final run map
  or an anomaly. Safe to repeat — tasks are idempotent.

  Args:
  - config: FDB+interfaces map.
  - bank-id: the bank owning the job.
  - job-id: the job to run."
  [config bank-id job-id]
  (core/force-start config bank-id job-id))

(defn update-schedule
  "Edit a job's `:periodicity`, `:run-time-minutes`, and/or `:enabled`,
  within the periodicities its tasks allow. Persists, recomputes
  next-run, and updates the live trigger when `config` has `:scheduler`.
  Returns the updated job or an anomaly.

  Args:
  - config: FDB+interfaces map.
  - bank-id: the bank owning the job.
  - job-id: the job to edit.
  - edits: map of any of `:periodicity` `:run-time-minutes` `:enabled`."
  [config bank-id job-id edits]
  (core/update-schedule config bank-id job-id edits))

(defn list-jobs
  "All scheduled jobs for `bank-id`.

  Args:
  - config: FDB+interfaces map.
  - bank-id: the bank to list jobs for."
  [config bank-id]
  (core/list-jobs config bank-id))

(defn get-job
  "One job by id, or nil if absent.

  Args:
  - config: FDB+interfaces map.
  - bank-id: the bank owning the job.
  - job-id: the job to fetch."
  [config bank-id job-id]
  (core/get-job config bank-id job-id))

(defn list-runs
  "Runs of `job-id`, newest first.

  Args:
  - config: FDB+interfaces map.
  - bank-id: the bank owning the job.
  - job-id: the job whose runs to list."
  [config bank-id job-id]
  (core/list-runs config bank-id job-id))

(defn get-run
  "One run by id, or nil if absent.

  Args:
  - config: FDB+interfaces map.
  - bank-id: the bank owning the run.
  - run-id: the run to fetch."
  [config bank-id run-id]
  (core/get-run config bank-id run-id))
