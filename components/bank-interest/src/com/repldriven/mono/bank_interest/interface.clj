(ns com.repldriven.mono.bank-interest.interface
  (:require
    com.repldriven.mono.bank-interest.system

    [com.repldriven.mono.bank-interest.core :as core]))

(defn accrue-daily
  "Accrues daily interest for every customer account in the org as
  of `:as-of-date` (YYYYMMDD int). Iterates accounts, reads their
  posted/default balance, computes interest from the product's
  `:interest-rate-bps` with carry, and posts an accrual transaction
  per account when the whole-units round trips. Returns
  `{:organization-id :as-of-date :accounts-processed}` or anomaly."
  [config data]
  (core/accrue-daily config data))

(defn capitalize-monthly
  "Moves accrued interest to the customer's posted/default balance
  for every customer account in the org as of `:as-of-date`. Reads
  the `:balance-type-interest-accrued` balance, posts a
  capitalisation transaction. Returns
  `{:organization-id :as-of-date :accounts-processed}` or anomaly."
  [config data]
  (core/capitalize-monthly config data))
