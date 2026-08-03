(ns com.repldriven.queenswood.interest.domain.run
  (:require
    [com.repldriven.queenswood.policy.interface :as policy]

    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private kind->action
  {:accrual :interest-action-accrue :capitalize :interest-action-capitalize})

(defn check-daily-count
  [policies kind aggregates]
  (policy/check-limit
   policies
   :interest
   {:action (kind->action kind)
    :aggregate :count
    :window :time-window-daily
    :value (inc (get-in aggregates [kind #{:bank-id :business-day}]))}))

(def ^:private eligible-cash-account-statuses
  #{:cash-account-status-opened :cash-account-status-suspended})

(defn eligible-cash-account?
  [account]
  (contains? eligible-cash-account-statuses (:account-status account)))

(defn- new-run
  [bank-id business-day kind]
  {:bank-id bank-id
   :business-day business-day
   :kind kind
   :state :interest-run-state-running
   :created-at (utility/now)})

(defn- close
  [run]
  (assoc run
         :state :interest-run-state-closed
         :closed-at (utility/now)))

(defn closed
  [bank-id business-day kind]
  (close (new-run bank-id business-day kind)))
