(ns com.repldriven.mono.bank-policy.core
  (:require
    [com.repldriven.mono.bank-policy.domain :as domain]
    [com.repldriven.mono.bank-policy.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn new-policy
  "Persists a policy. Returns the policy map or anomaly."
  [config data]
  (let [policy (domain/new-policy data)]
    (let-nom> [_ (store/save-policy config policy)]
      policy)))

(defn get-policy
  "Loads a policy by policy-id."
  [txn policy-id]
  (store/get-policy txn policy-id))

(defn get-policies
  "Lists policies with pagination."
  ([txn]
   (store/get-policies txn))
  ([txn opts]
   (store/get-policies txn opts)))

(defn new-binding
  "Persists a policy binding. Returns the binding map or anomaly."
  [config data]
  (let [binding (domain/new-binding data)]
    (let-nom> [_ (store/save-binding config binding)]
      binding)))

(defn get-binding
  "Loads a binding by binding-id."
  [txn binding-id]
  (store/get-binding txn binding-id))

(defn get-bindings
  "Lists policy bindings with pagination."
  ([txn]
   (store/get-bindings txn))
  ([txn opts]
   (store/get-bindings txn opts)))

(defn get-effective-policies
  "Returns the policies effective for the given binding target
  selectors. `selectors` is a map keyed by target id field
  (e.g. `{:organization-id <id> :party-id <id>}`). For now
  always loads policies labelled `tier=platform`; selectors
  will resolve PolicyBindings in a later round."
  [txn _selectors]
  (store/get-policies-by-label txn "tier" "platform"))

(defn get-policies-by-tier
  "Returns the list of policies whose `tier=<tier>` label
  matches. Used at organization-creation time to bind the
  selected tier's policies to the new organization."
  [txn tier]
  (store/get-policies-by-label txn "tier" tier))

(defn get-tiers
  "Returns the distinct set of tier label values across all
  policies as `[{:tier <name> :description <first-policy-desc>}]`.
  The description is taken from the first policy carrying that
  label — sufficient for surfacing to the admin UI; if multiple
  policies share a tier the rest are ignored."
  [txn]
  (let-nom> [{:keys [items]} (store/get-policies txn {:limit 1000})]
    (->> items
         (keep (fn [{:keys [labels description]}]
                 (when-let [tier (get labels "tier")]
                   {:tier tier :description (or description "")})))
         (reduce (fn [acc {:keys [tier] :as t}]
                   (if (some (fn [x] (= (:tier x) tier)) acc)
                     acc
                     (conj acc t)))
                 [])
         vec)))
