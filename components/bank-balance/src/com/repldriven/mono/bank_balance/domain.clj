(ns com.repldriven.mono.bank-balance.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- ensure-product-type
  [data]
  (when-not (:product-type data)
    (error/reject :balance/missing-product-type
                  (merge {:message "A balance must carry a product-type"}
                         (select-keys data
                                      [:account-id :balance-type
                                       :currency :balance-status])))))

(defn- ensure-new-balance
  [data exists?]
  (when exists?
    (error/reject :balance/already-exists
                  (merge {:message "Balance already exists"}
                         (select-keys data
                                      [:account-id :balance-type
                                       :currency :balance-status])))))

(defn- check-capability
  [action balance-type balance-status policies]
  (policy/check-capability policies
                           :balance
                           {:action action
                            :balance-type balance-type
                            :balance-status balance-status}))

(defn- net
  [balance]
  (if balance (- (:credit balance 0) (:debit balance 0)) 0))

(defmulti ^:private posted? (fn [b] [(:balance-type b) (:balance-status b)]))

(defmethod posted? [:balance-type-default :balance-status-posted] [_] true)

(defmethod posted? :default [_] false)

(defmulti ^:private available?
  (fn [b] [(= :product-type-general-ledger (:product-type b)) (:balance-type b)
           (:balance-status b)]))

(defmethod available? [false :balance-type-default :balance-status-posted]
  [_]
  true)

(defmethod available? [false :balance-type-default
                       :balance-status-pending-outgoing]
  [_]
  true)

(defmethod available? :default [_] false)

(defn net-balance
  [balances currency pred-fn]
  {:value (->> balances
               (filter pred-fn)
               (map net)
               (reduce + 0))
   :currency currency})

(defn posted-balance
  [balances currency]
  (net-balance balances currency posted?))

(defn available-balance
  [balances currency]
  (net-balance balances currency available?))

(defn trial-balance
  "Aggregate account-level posted balances into a per-currency trial
  balance — total debits vs total credits, plus the account count — one
  block per currency. `entries` is a collection of
  `{:currency :normal-side :value}`, where `:normal-side` is `:debit` or
  `:credit` (the account's normal side) and `:value` is the
  credit-positive posted net (credit − debit). A debit-normal account
  contributes its magnitude (− value) to debits; a credit-normal account
  contributes + value to credits. Σdebit equals Σcredit within a currency
  exactly when its books balance; currencies never sum together."
  [entries]
  (->> entries
       (group-by :currency)
       (mapv (fn [[currency es]]
               (reduce (fn [block {:keys [normal-side value]}]
                         (let [debit? (= :debit normal-side)]
                           (-> block
                               (update :accounts inc)
                               (update (if debit? :debit :credit)
                                       +
                                       (if debit? (- value) value)))))
                       {:currency currency :debit 0 :credit 0 :accounts 0}
                       es)))))

(defn- find-balance-index
  [balances balance-type balance-status]
  (some (fn [[i b]]
          (when (and (= balance-type (:balance-type b))
                     (= balance-status (:balance-status b)))
            i))
        (map-indexed vector balances)))

(defn- update-balance
  [balances balance-type balance-status f]
  (if-let [idx (find-balance-index balances balance-type balance-status)]
    (let-nom> [updated (f (nth balances idx))]
      (assoc balances idx updated))
    (error/reject :balance/not-found
                  {:message "Balance not found"
                   :balance-type balance-type
                   :balance-status balance-status})))

(defn- apply-leg
  [balance leg policies]
  (let [{:keys [balance-type balance-status]} balance
        {:keys [side amount]} leg]
    (let-nom>
      [_ (check-capability :balance-action-apply
                           balance-type
                           balance-status
                           policies)]
      (let [field (if (= :leg-side-debit side) :debit :credit)]
        (update balance field + amount)))))

(defn- new-zero-balance
  "A fresh zero balance for a leg whose bucket doesn't exist yet — posting
  to a (balance-type, balance-status) opens it (e.g. the first time funds
  are reserved into pending-outgoing). A leg without a product-type is a
  ledger-account (GL) leg, matching how GL balances are seeded."
  [leg]
  (let [now (utility/now)]
    {:account-id (:account-id leg)
     :product-type (or (:product-type leg) :product-type-general-ledger)
     :balance-type (:balance-type leg)
     :balance-status (:balance-status leg)
     :currency (:currency leg)
     :credit 0
     :debit 0
     :credit-carry 0
     :created-at now
     :updated-at now}))

(defn- apply-leg-to-balances
  [balances leg policies]
  (let [{:keys [balance-type balance-status]} leg]
    (if (find-balance-index balances balance-type balance-status)
      (update-balance balances
                      balance-type
                      balance-status
                      (fn [balance] (apply-leg balance leg policies)))
      ;; Open the bucket on demand: a posting to a not-yet-existing
      ;; (balance-type, balance-status) creates it, then applies the leg.
      (let-nom> [opened (apply-leg (new-zero-balance leg) leg policies)]
        (conj balances opened)))))

(defn- apply-legs-to-balances
  [balances legs policies]
  (reduce (fn [bs leg]
            (let [result (apply-leg-to-balances bs leg policies)]
              (if (error/anomaly? result) (reduced result) result)))
          balances
          legs))

(defn- check-available
  [pre post transaction-type policies]
  (let [{:keys [currency]} (first post)
        pre-amount (available-balance pre currency)
        post-amount (available-balance post currency)]
    (policy/check-limit policies
                        :balance
                        {:kind {:computed {:name "available"}}
                         :transaction-type transaction-type
                         :aggregate :amount
                         :window :time-window-instant
                         :pre-value {:value (:value pre-amount)
                                     :currency currency}
                         :value {:value (:value post-amount)
                                 :currency currency}})))

(defn- changed
  "Balances in `new` that were added or modified versus `old`, matched by
  (balance-type, balance-status) — so a freshly-opened bucket (appended,
  with no positional counterpart in `old`) is still captured."
  [old new]
  (let [old-by (into {}
                     (map (fn [b] [[(:balance-type b) (:balance-status b)] b]))
                     old)]
    (filterv (fn [b]
               (not= b (get old-by [(:balance-type b) (:balance-status b)])))
             new)))

(defn apply-legs
  [account-balances legs transaction-type policies]
  (reduce
   (fn [acc [account-id account-legs]]
     (let [pre (get account-balances account-id)
           post (let-nom>
                  [balances (apply-legs-to-balances pre account-legs policies)
                   _ (check-available pre balances transaction-type policies)]
                  balances)]
       (if (error/anomaly? post)
         (reduced post)
         (into acc (changed pre post)))))
   []
   (group-by :account-id legs)))

(defn new-balance
  [data exists? policies]
  (let-nom>
    [_ (check-capability :balance-action-create
                         (:balance-type data)
                         (:balance-status data)
                         policies)
     _ (ensure-product-type data)
     _ (ensure-new-balance data exists?)]
    (let [{:keys [account-id product-type balance-type balance-status currency]}
          data
          now (utility/now)]
      {:account-id account-id
       :product-type product-type
       :balance-type balance-type
       :balance-status balance-status
       :currency currency
       :credit 0
       :debit 0
       :credit-carry 0
       :created-at now
       :updated-at now})))
