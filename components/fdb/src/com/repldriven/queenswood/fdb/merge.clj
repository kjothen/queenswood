(ns com.repldriven.queenswood.fdb.merge
  (:require
    [com.repldriven.queenswood.fdb.record :as record]
    [com.repldriven.queenswood.fdb.scan :as scan]

    [com.repldriven.mono.error.interface :as error]))

(defn- join-key
  "The element two stores are paired on: the first of the key past the
  prefix. A store with one row per key has that element alone as its
  cursor, so the two shapes have to be handled together."
  [entry]
  (let [k (:key entry)]
    (if (sequential? k) (first k) k)))

(defn- fetch
  "One page of a side, in its own transaction. A merge outlives any
  single transaction — FDB caps them at five seconds — so each refill
  opens a new one rather than holding a cursor open across the scan."
  [config {:keys [store prefix limit]} after]
  (record/transact
   config
   (fn [txn]
     (scan/scan-entries (record/open txn store)
                        (cond-> {:prefix prefix :limit limit}
                                after
                                (assoc :after after))))
   :fdb/merge-scan
   "Failed to read a page during merge scan"))

(defn- side [spec] {:spec spec :buffer [] :after nil :more? true})

(defn- fill
  "Guarantees the side has a buffered entry unless its store is spent.
  Returns the side, or an anomaly."
  [config {:keys [spec buffer after more?] :as s}]
  (if (or (seq buffer) (not more?))
    s
    (let [page (fetch config spec after)]
      (if (error/anomaly? page)
        page
        (assoc s
               :buffer (vec (:entries page))
               :after (:after page)
               ;; `scan` sets :after only when rows remain beyond the
               ;; page, so its absence is the end of the store.
               :more? (some? (:after page)))))))

(defn- take-group
  "Pulls every entry sharing `k` off the front of a side, refilling as
  it goes so a group split across a page boundary still comes back
  whole. Returns `[side entries]`, or an anomaly."
  [config s k]
  (loop [s s
         acc []]
    (let [s (fill config s)]
      (if (error/anomaly? s)
        s
        (let [head (first (:buffer s))]
          (if (and head (= k (join-key head)))
            (recur (update s :buffer subvec 1) (conj acc head))
            [s acc]))))))

(defn merge-scan
  "Pairs two stores on the first key element past their prefixes,
  reducing over the pairs in key order.

  Both sides are scanned ascending and buffered independently, so the
  stores' differing row counts per key need no alignment — each cursor
  advances at its own rate.

  This is an outer join. A key present in one store and not the other
  is still delivered, with the absent side empty, because the two cases
  that produces are things a caller has to decide about rather than
  have decided for it: an account with no balances has earned nothing
  but was still considered, and a balance whose account has gone is a
  fact worth noticing rather than skipping silently.

  It is not a consistent snapshot. The sides refill in separate
  transactions, so a record written mid-scan can appear on one side and
  not the other.

  Args:
  - config: map with :record-db and :record-store.
  - opts: `{:left {:store :prefix :limit} :right {...}}`.
  - f: reducing fn of `[acc {:keys [key left right]}]`, where `left`
    and `right` are that key's records as bytes. May return `reduced`
    to stop early; an anomaly ends the scan and propagates.
  - init: initial accumulator.

  Returns the accumulator, or an anomaly."
  [config {:keys [left right]} f init]
  (loop [l (side left)
         r (side right)
         acc init]
    ;; Each step resolves to either an anomaly, a `reduced`, or
    ;; `[l r acc]` to continue with — kept out of `recur`'s way so the
    ;; short-circuiting stays readable.
    (let [step
          (let [l (fill config l)]
            (if (error/anomaly? l)
              l
              (let [r (fill config r)]
                (if (error/anomaly? r)
                  r
                  (let [lk (some-> (first (:buffer l))
                                   join-key)
                        rk (some-> (first (:buffer r))
                                   join-key)]
                    (if (and (nil? lk) (nil? rk))
                      (reduced acc)
                      (let [k (cond (nil? lk)
                                    rk
                                    (nil? rk)
                                    lk
                                    (neg? (compare lk rk))
                                    lk
                                    :else
                                    rk)
                            lres (if (= k lk)
                                   (take-group config l k)
                                   [l []])]
                        (if (error/anomaly? lres)
                          lres
                          (let [rres (if (= k rk)
                                       (take-group config r k)
                                       [r []])]
                            (if (error/anomaly? rres)
                              rres
                              (let [[l lg] lres
                                    [r rg] rres
                                    acc (f acc
                                           {:key k
                                            :left (mapv :record lg)
                                            :right (mapv :record rg)})]
                                (cond
                                 (error/anomaly? acc)
                                 acc

                                 (reduced? acc)
                                 acc

                                 :else
                                 [l r acc]))))))))))))]
      (cond
       (error/anomaly? step)
       step

       (reduced? step)
       @step

       :else
       (recur (first step) (second step) (nth step 2))))))
