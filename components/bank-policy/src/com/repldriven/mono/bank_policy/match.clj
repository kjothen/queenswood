(ns com.repldriven.mono.bank-policy.match
  (:require
    [clojure.string :as str]))

(defn none?
  "Whether `v` is protojure's representation of an unset
  optional proto field: `nil` for a missing message-typed
  field, or a `:*-unknown` keyword for an enum field at its
  proto2 zero default."
  [v]
  (or (nil? v)
      (and (keyword? v)
           (str/ends-with? (name v) "-unknown"))))

(defn filter-match?
  "Whether a single `<Kind>Filter` (cap or limit) matches the
  request. Set fields must equal the request's corresponding
  slot; unset fields do not constrain."
  [filter request]
  (every? (fn [[k v]]
            (or (none? v)
                (= v (get request k))))
          filter))

(defn variant
  "Returns the keyword variant of a single-entry oneof map."
  [m]
  (when (map? m) (first (keys m))))

(defn matches?
  "Whether capability or limit `c` matches the requested `kind`
  and `request`. Top-level fields must equal the corresponding
  request slot; if `:filters` is non-empty, at least one filter
  must agree with the request."
  [c kind request]
  (let [kind->fields (:kind c)]
    (and (= kind (variant kind->fields))
         (let [fields (get kind->fields kind)
               filters (:filters fields)
               other-fields (dissoc fields :filters)]
           (and (every? (fn [[k v]] (= v (get request k))) other-fields)
                (or (empty? filters)
                    (some (fn [f] (filter-match? f request)) filters)))))))
