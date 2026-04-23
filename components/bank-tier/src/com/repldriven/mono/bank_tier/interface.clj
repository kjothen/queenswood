(ns com.repldriven.mono.bank-tier.interface
  (:require
    com.repldriven.mono.bank-tier.system

    [com.repldriven.mono.bank-tier.core :as core]
    [com.repldriven.mono.bank-tier.domain :as domain]))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly."
  ([txn] (core/get-tiers txn))
  ([txn opts] (core/get-tiers txn opts)))

(defn get-tier
  "Finds a Tier by tier-id. Returns the Tier map or
  rejection anomaly if not found."
  [txn tier-id]
  (core/get-tier txn tier-id))

(defn get-org-tier
  "Loads the tier for the given organization. Returns the
  Tier map or rejection anomaly if the org or tier is
  not found."
  [txn org-id]
  (core/get-org-tier txn org-id))

(defn new-tier
  "Creates a new named Tier. Returns the persisted tier
  (including its generated `:tier-id`) or anomaly."
  [txn name policies limits]
  (core/new-tier txn name policies limits))

(defn update-tier
  "Updates a tier's policies and limits. Returns the
  updated tier map or anomaly."
  [txn tier-id policies limits]
  (core/update-tier txn tier-id policies limits))

(defn policy
  "Returns the first policy matching capability, or nil."
  [tier capability]
  (domain/policy tier capability))

(defn limit
  "Returns the first limit matching type and kind, or
  nil."
  [tier limit-type kind]
  (domain/limit tier limit-type kind))
