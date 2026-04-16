(ns com.repldriven.mono.bank-tier.interface
  (:require
    com.repldriven.mono.bank-tier.system

    [com.repldriven.mono.bank-tier.core :as core]
    [com.repldriven.mono.bank-tier.domain :as domain]))

(defn get-tiers
  "Lists all tiers. Returns a sequence of tier maps or
  anomaly."
  [txn]
  (core/get-tiers txn))

(defn get-tier
  "Finds a Tier by tier-type keyword. Returns the Tier
  map or rejection anomaly if not found."
  [txn tier-type]
  (core/get-tier txn tier-type))

(defn get-org-tier
  "Loads the tier for the given organization. Returns the
  Tier map or rejection anomaly if the org or tier is
  not found."
  [txn org-id]
  (core/get-org-tier txn org-id))

(defn new-tier
  "Creates a new Tier with the given type, policies, and
  limits. Returns the Tier map or anomaly."
  [txn tier-type policies limits]
  (core/new-tier txn tier-type policies limits))

(defn update-tier
  "Updates a tier's policies and limits. Returns the
  updated tier map or anomaly."
  [txn tier-type policies limits]
  (core/update-tier txn tier-type policies limits))

(defn policy
  "Returns the first policy matching capability, or nil."
  [tier capability]
  (domain/policy tier capability))

(defn limit
  "Returns the first limit matching type and kind, or
  nil."
  [tier limit-type kind]
  (domain/limit tier limit-type kind))
