(ns com.repldriven.mono.bank-policy.core
  (:require
    [com.repldriven.mono.bank-policy.domain :as domain]
    [com.repldriven.mono.bank-policy.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn new-policy
  [config data]
  (let [policy (domain/new-policy data)]
    (let-nom> [_ (store/save-policy config policy)]
      policy)))

(defn get-policy
  [txn policy-id]
  (store/get-policy txn policy-id))

(defn get-policies
  ([txn]
   (store/get-policies txn))
  ([txn opts]
   (store/get-policies txn opts)))

(defn new-binding
  [config data]
  (let [binding (domain/new-binding data)]
    (let-nom> [_ (store/save-binding config binding)]
      binding)))

(defn get-binding
  [txn binding-id]
  (store/get-binding txn binding-id))

(defn get-bindings
  ([txn]
   (store/get-bindings txn))
  ([txn opts]
   (store/get-bindings txn opts)))

(defn- bound-policies
  "Returns the policies bound (via PolicyBinding records) to the
  organization in `selectors`, or `[]` when the selector carries no
  organization."
  [txn selectors]
  (if-let [org-id (:organization-id selectors)]
    (let-nom> [bindings (store/get-bindings-for-organization txn org-id)]
      (reduce (fn [acc b]
                (let [p (store/get-policy txn (:policy-id b))]
                  (if (map? p) (conj acc p) (reduced p))))
              []
              bindings))
    []))

(defn get-effective-policies
  [txn selectors]
  (let-nom> [platform (store/get-policies-by-label txn "tier" "platform")
             bound (bound-policies txn selectors)]
    (vec (concat platform bound))))

(defn get-policies-by-tier
  [txn tier]
  (store/get-policies-by-label txn "tier" tier))

(defn get-tiers
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
