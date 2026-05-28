(ns com.repldriven.mono.bank-interest.interface
  "Interest accrual and capitalisation for customer cash accounts.
  Iterates an organisation's customer accounts on a given as-of date
  and posts the resulting accrual or capitalisation transactions.
  Returns a per-call summary map or an anomaly."
  (:require
    com.repldriven.mono.bank-interest.system

    [com.repldriven.mono.bank-interest.core :as core]))

(defn accrue-daily
  "Post a daily interest accrual for every customer account in the
  organisation as of the given date.

  Args:
  - config: FDB handle plus product/balance/transaction interfaces.
  - data: map with :bank-id and :as-of-date (YYYYMMDD int).

  Returns `{:bank-id :as-of-date :accounts-processed}` or
  an anomaly."
  [config data]
  (core/accrue-daily config data))

(defn capitalize-monthly
  "Move accrued interest into the customer's posted/default balance
  for every customer account in the organisation as of the given
  date.

  Args:
  - config: FDB handle plus product/balance/transaction interfaces.
  - data: map with :bank-id and :as-of-date (YYYYMMDD int).

  Returns `{:bank-id :as-of-date :accounts-processed}` or
  an anomaly."
  [config data]
  (core/capitalize-monthly config data))
