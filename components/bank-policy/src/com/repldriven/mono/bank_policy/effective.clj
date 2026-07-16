(ns com.repldriven.mono.bank-policy.effective
  (:require
    [com.repldriven.mono.bank-policy.domain :as domain]
    [com.repldriven.mono.bank-policy.match :as match]))

;; Resolve a bank's effective policies into the single decision that
;; evaluation would reach, tagging each survivor with the policy that
;; decided it. Mirrors the evaluators: capabilities are deny-wins (see
;; capability/check), limits are conjunctive so the binding one is the
;; most restrictive (see limit/check). Pure functions over policy maps.

(defn- origin
  [policy]
  {:tier (get-in policy [:labels "tier"])
   :policy-id (:policy-id policy)
   :name (:name policy)})

(defn- domain-of [m] (match/variant (:kind m)))

(defn- arm [m] (get (:kind m) (domain-of m)))

;; A scope signature that's stable regardless of filter ordering, so
;; same-scope entries group together and differently-scoped ones (a
;; savings allow vs a current deny) stay distinct.
(defn- filters-sig
  [m]
  (set (map (fn [f] (into (sorted-map) f)) (:filters (arm m)))))

;; ---------------------------------------------------------------------------
;; Capabilities — deny-wins per [domain action scope].

(defn- cap-key
  [c]
  [(domain-of c) (:action (arm c)) (filters-sig c)])

(defn- pick-cap
  "Deny-wins: a deny in the group is the decision, else an allow."
  [caps]
  (or (first (filter (fn [c] (= :effect-deny (:effect c))) caps))
      (first (filter (fn [c] (= :effect-allow (:effect c))) caps))
      (first caps)))

(defn- resolve-caps
  [policies]
  (->> (for [p (filter domain/live? policies)
             c (:capabilities p)]
         (assoc c :origin (origin p)))
       (group-by cap-key)
       vals
       (map pick-cap)
       (sort-by (fn [c] [(str (domain-of c)) (str (:action (arm c)))]))
       vec))

;; ---------------------------------------------------------------------------
;; Limits — most restrictive per [domain scope bound-kind aggregate window
;; ccy].

(defn- bound-kind [l] (match/variant (get-in l [:bound :kind])))

(defn- bound-aggregate
  "The aggregate to compare for tightness. `:max`/`:min` carry one;
  `:range` is compared on its upper side."
  [l]
  (let [bk (bound-kind l)]
    (case bk
      (:max :min) (get-in l [:bound :kind bk :aggregate])
      :range (get-in l [:bound :kind :range :max])
      nil)))

(defn- agg-kind [agg] (match/variant (:kind agg)))

(defn- agg-fields [agg] (get-in agg [:kind (agg-kind agg)]))

(defn- magnitude
  "Numeric magnitude of an aggregate's value: a bare number for
  `:count`, the minor amount for `:amount`."
  [agg]
  (let [fields (agg-fields agg)
        v (:value fields)]
    (if (map? v) (:value v) v)))

(defn- currency [agg] (get-in (agg-fields agg) [:value :currency]))

(defn- limit-key
  [l]
  (let [agg (bound-aggregate l)]
    [(domain-of l) (filters-sig l) (bound-kind l)
     (agg-kind agg) (:window (agg-fields agg)) (currency agg)]))

(defn- pick-limit
  "Most restrictive within a same-dimension group: smallest ceiling
  for `:max`, largest floor for `:min`; otherwise keep the first."
  [limits]
  (let [bk (bound-kind (first limits))
        mag (fn [l] (magnitude (bound-aggregate l)))]
    (case bk
      :max (apply min-key mag limits)
      :min (apply max-key mag limits)
      (first limits))))

(defn- resolve-limits
  [policies]
  (->> (for [p (filter domain/live? policies)
             l (:limits p)]
         (assoc l :origin (origin p)))
       (group-by limit-key)
       vals
       (map pick-limit)
       (sort-by (fn [l] (str (domain-of l))))
       vec))

;; ---------------------------------------------------------------------------

(defn resolve-effective
  "Collapse effective `policies` into the resolved decision set:
  `{:capabilities [...] :limits [...]}`, one survivor per scope, each
  carrying an `:origin {:tier :policy-id :name}`."
  [policies]
  {:capabilities (resolve-caps policies)
   :limits (resolve-limits policies)})
