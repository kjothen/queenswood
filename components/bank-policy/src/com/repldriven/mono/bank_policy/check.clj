(ns com.repldriven.mono.bank-policy.check
  (:require
    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str]))

(defn- wildcard?
  [v]
  (or (nil? v)
      (and (keyword? v)
           (str/ends-with? (name v) "-unknown"))))

(defn- filter-match?
  [filter-v request-v]
  (or (wildcard? filter-v)
      (= filter-v request-v)))

(defn- variant
  [m]
  (when (map? m) (first (keys m))))

(defn- matches?
  [c kind request]
  (let [kind-map (:kind c)]
    (and (= kind (variant kind-map))
         (let [filter-fields (get kind-map kind)]
           (every? (fn [[k v]] (filter-match? v (get request k)))
                   filter-fields)))))

(defn check-capability
  "Returns `true` when the requested capability `(kind, request)` is
  allowed by `policies`. Returns an `:unauthorized/policy-denied`
  anomaly otherwise.

  Disabled policies are skipped. Among capabilities whose `:kind`
  variant equals `kind` and whose populated filter slots agree with
  the corresponding slots in `request` (treating `nil` and protojure
  `:*-unknown` sentinels as wildcards), `:effect-deny` wins; otherwise
  at least one `:effect-allow` is required."
  [policies kind request]
  (let [matching (->> policies
                      (filter :enabled)
                      (mapcat :capabilities)
                      (filter (fn [c] (matches? c kind request))))
        denies (filter (fn [c] (= :effect-deny (:effect c))) matching)
        allows (filter (fn [c] (= :effect-allow (:effect c))) matching)]
    (cond
     (seq denies)
     (error/unauthorized :policy/denied
                         {:message (or (:reason (first denies))
                                       "Capability explicitly denied")
                          :kind kind
                          :request request})

     (seq allows)
     true

     :else
     (error/unauthorized :policy/denied
                         {:message "No matching allow capability"
                          :kind kind
                          :request request}))))

(defn- normalize-window
  [w]
  (when (keyword? w)
    (let [n (name w)
          prefix "time-window-"]
      (keyword (if (str/starts-with? n prefix) (subs n (count prefix)) n)))))

(defn- aggregate-kind [agg] (variant (:kind agg)))

(defn- aggregate-fields [agg] (get-in agg [:kind (aggregate-kind agg)]))

(defn- aggregate-applies?
  [agg request]
  (and (= (:aggregate request) (aggregate-kind agg))
       (= (normalize-window (:window request))
          (normalize-window (:window (aggregate-fields agg))))))

(defn- bound-violation
  [bound request]
  (let [bound-kind (variant (:kind bound))
        payload (get-in bound [:kind bound-kind])
        violates (fn [agg op]
                   (when (aggregate-applies? agg request)
                     (let [bound-value (:value (aggregate-fields agg))]
                       (when (op (:value request) bound-value)
                         (str "Limit " (name bound-kind)
                              " "
                              (name (aggregate-kind agg))
                              "=" bound-value)))))]
    (case bound-kind
      :max (violates (:aggregate payload) >)
      :min (violates (:aggregate payload) <)
      :range (or (violates (:max payload) >)
                 (violates (:min payload) <))
      nil)))

(defn check-limit
  "Returns `true` when `policies` impose no violated limit on the
  request `(kind, request)`. Returns an
  `:unauthorized/policy-limit-exceeded` anomaly otherwise.

  `request` shape:
    {:aggregate :count | :amount
     :window    :instant | :daily | :weekly | :monthly | :rolling
     :value     <number>}

  Decision rule:

  - Disabled policies are skipped.
  - A limit applies when its `:kind` variant equals `kind` and
    every populated kind filter slot agrees with the request
    (`nil` and `:*-unknown` are wildcards).
  - For applicable limits, the bound's aggregate must match the
    request's `:aggregate` variant and `:window` to be evaluated;
    non-matching aggregates are silently skipped.
  - The first violation short-circuits; no matching limits is
    permissive."
  [policies kind request]
  (let [matching (->> policies
                      (filter :enabled)
                      (mapcat :limits)
                      (filter (fn [l] (matches? l kind request))))
        violation (some (fn [l]
                          (when-let [msg (bound-violation (:bound l) request)]
                            [l msg]))
                        matching)]
    (if violation
      (let [[limit msg] violation]
        (error/unauthorized :policy/limit-exceeded
                            {:message (or (:reason limit) msg)
                             :kind kind
                             :request request}))
      true)))
