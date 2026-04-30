(ns com.repldriven.mono.bank-policy.limit
  (:require
    [com.repldriven.mono.bank-policy.match :as match]

    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str]))

(defn- normalize-window
  [w]
  (when (keyword? w)
    (let [n (name w)
          prefix "time-window-"]
      (keyword (if (str/starts-with? n prefix) (subs n (count prefix)) n)))))

(defn- aggregate-kind [agg] (match/variant (:kind agg)))

(defn- aggregate-fields [agg] (get-in agg [:kind (aggregate-kind agg)]))

(defn- amount? [v] (and (map? v) (contains? v :currency)))

(defn- num-value [v] (if (amount? v) (:value v) v))

(defn- aggregate-applies?
  [agg request]
  (and (= (:aggregate request) (aggregate-kind agg))
       (= (normalize-window (:window request))
          (normalize-window (:window (aggregate-fields agg))))
       (or (not= :amount (aggregate-kind agg))
           (= (:currency (:value (aggregate-fields agg)))
              (:currency (:value request))))))

(defn- improving?
  "Whether `post` is no worse than `pre` for the given `side`.
  For `:max` bounds smaller is better; for `:min` bounds larger
  is better."
  [side pre post]
  (case side
    :max (<= post pre)
    :min (>= post pre)))

(defn- out-of-bound?
  [side value bound-value]
  (case side
    :max (> value bound-value)
    :min (< value bound-value)))

(defn- bound-violation
  [bound request allow]
  (let [bound-kind (match/variant (:kind bound))
        payload (get-in bound [:kind bound-kind])
        violates (fn [side agg]
                   (when (aggregate-applies? agg request)
                     (let [bound-value (num-value (:value (aggregate-fields
                                                           agg)))
                           post (num-value (:value request))
                           pre (some-> (:pre-value request)
                                       num-value)
                           lenient? (and (= :limit-allow-improving allow)
                                         (some? pre)
                                         (out-of-bound? side pre bound-value)
                                         (improving? side pre post))]
                       (when (and (out-of-bound? side post bound-value)
                                  (not lenient?))
                         (str "Limit " (name bound-kind)
                              " "
                              (name (aggregate-kind agg))
                              "=" (:value (aggregate-fields agg)))))))]
    (case bound-kind
      :max (violates :max (:aggregate payload))
      :min (violates :min (:aggregate payload))
      :range (or (violates :max (:max payload))
                 (violates :min (:min payload)))
      nil)))

(defn check
  "Returns `true` when `policies` impose no violated limit on the
  request `(kind, request)`. Returns an
  `:unauthorized/policy-limit-exceeded` anomaly otherwise.

  `request` shape:
    {:aggregate :count | :amount
     :window    :instant | :daily | :weekly | :monthly | :rolling
     :value     <number-or-amount>     ;; post-state value
     :pre-value <number-or-amount>}    ;; optional, pre-state value

  Decision rule:

  - Disabled policies are skipped.
  - A limit applies when its `:kind` variant equals `kind`, every
    top-level field equals the request's corresponding slot, and —
    if `:filters` is non-empty — at least one filter's set fields
    all agree with the request (unset fields inside a filter do not
    constrain).
  - For applicable limits, the bound's aggregate must match the
    request's `:aggregate` variant and `:window` to be evaluated;
    non-matching aggregates are silently skipped.
  - When the limit's `:allow` is `:limit-allow-improving` and
    `:pre-value` is provided, an out-of-bound `:value` is permitted
    iff the pre state was already out-of-bound and post is no worse
    than pre. Otherwise the bound is enforced strictly.
  - The first violation short-circuits; no matching limits is
    permissive."
  [policies kind request]
  (let [matching (->> policies
                      (filter :enabled)
                      (mapcat :limits)
                      (filter (fn [l] (match/matches? l kind request))))
        violation (some (fn [l]
                          (when-let [msg (bound-violation (:bound l)
                                                          request
                                                          (:allow l))]
                            [l msg]))
                        matching)]
    (if violation
      (let [[limit msg] violation]
        (error/unauthorized :policy/limit-exceeded
                            {:message (or (:reason limit) msg)
                             :kind kind
                             :request request}))
      true)))
