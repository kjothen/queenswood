(ns com.repldriven.mono.bank-policy.interface
  (:require
    com.repldriven.mono.bank-policy.system

    [com.repldriven.mono.bank-policy.capability :as capability]
    [com.repldriven.mono.bank-policy.core :as core]
    [com.repldriven.mono.bank-policy.limit :as limit]))

(defn new-policy
  "Persists a policy. Returns the policy map or anomaly."
  [config data]
  (core/new-policy config data))

(defn get-policy
  "Loads a policy by policy-id. Returns the policy map or
  rejection anomaly if not found."
  [txn policy-id]
  (core/get-policy txn policy-id))

(defn get-policies
  "Lists policies with pagination. Returns
  {:items [...] :before id|nil :after id|nil} or anomaly."
  ([txn]
   (core/get-policies txn))
  ([txn opts]
   (core/get-policies txn opts)))

(defn new-binding
  "Persists a policy binding tying a policy to a target. Returns
  the binding map or anomaly."
  [config data]
  (core/new-binding config data))

(defn get-binding
  "Loads a binding by binding-id. Returns the binding map or
  rejection anomaly if not found."
  [txn binding-id]
  (core/get-binding txn binding-id))

(defn get-bindings
  "Lists policy bindings with pagination. Returns
  {:items [...] :before id|nil :after id|nil} or anomaly."
  ([txn]
   (core/get-bindings txn))
  ([txn opts]
   (core/get-bindings txn opts)))

(defn check-capability
  "Returns `true` when `policies` allow the requested capability
  `(kind, request)`. Returns an `:unauthorized/policy-denied`
  anomaly otherwise."
  [policies kind request]
  (capability/check policies kind request))

(defn check-limit
  "Returns `true` when `policies` impose no violated limit on the
  request `(kind, request)`. Returns an
  `:unauthorized/policy-limit-exceeded` anomaly otherwise.

  `request` shape:
    {:aggregate :count|:amount
     :window    :instant|:daily|:weekly|:monthly|:rolling
     :value     <number>}"
  [policies kind request]
  (limit/check policies kind request))

(defn get-effective-policies
  "Returns the policies effective for the given binding target
  selectors. `selectors` is a map keyed by target id field
  (e.g. `{:organization-id <id>}`). For now always loads the
  platform policy (`labels.tier = platform`); selectors are
  reserved for binding resolution in a later round."
  [txn selectors]
  (core/get-effective-policies txn selectors))

(defn get-policies-by-tier
  "Returns the list of policies whose `tier=<tier>` label
  matches. Bound to a new organization at creation time."
  [txn tier]
  (core/get-policies-by-tier txn tier))

(defn get-tiers
  "Returns the distinct set of tier label values across all
  policies as `[{:tier <name> :description <description>}]`.
  Description is taken from the first policy carrying the
  label."
  [txn]
  (core/get-tiers txn))
