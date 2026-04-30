(ns com.repldriven.mono.bank-balance.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- check-capability
  [action balance-type balance-status policies]
  (policy/check-capability policies
                           :balance
                           {:action action
                            :balance-type balance-type
                            :balance-status balance-status}))

(defn- net
  "Returns credit minus debit for a balance, defaulting
  to zero."
  [balance]
  (if balance (- (:credit balance 0) (:debit balance 0)) 0))

(defn- find-balance
  [balances balance-type balance-status]
  (first (filter #(and (= balance-type (:balance-type %))
                       (= balance-status (:balance-status %)))
                 balances)))

(defn posted-balance
  "Returns the net default/posted balance."
  [balances currency]
  (let [b (find-balance balances
                        :balance-type-default
                        :balance-status-posted)]
    {:value (if b (net b) 0)
     :currency currency}))

(defn available-balance
  "Returns the available balance for the given account
  type."
  [product-type balances currency]
  (let [default-posted
        (net (find-balance balances
                           :balance-type-default
                           :balance-status-posted))
        v (case product-type
            (:product-type-current
             :product-type-savings
             :product-type-term-deposit)
            (+ default-posted
               (net (find-balance
                     balances
                     :balance-type-default
                     :balance-status-pending-incoming))
               (net (find-balance
                     balances
                     :balance-type-default
                     :balance-status-pending-outgoing)))

            :product-type-settlement
            (+ default-posted
               (net (find-balance
                     balances
                     :balance-type-interest-payable
                     :balance-status-posted)))

            :product-type-internal
            default-posted

            ;; unknown type — just posted
            default-posted)]
    {:value v :currency currency}))

(defn- apply-leg
  "Applies a single leg to its balance. Debits add to
  `:debit`, credits to `:credit`."
  [balance {:keys [side amount]} policies]
  (let-nom>
    [_ (check-capability :balance-action-apply
                         (:balance-type balance)
                         (:balance-status balance)
                         policies)]
    (let [field (if (= :leg-side-debit side) :debit :credit)]
      (update balance field + amount))))

(defn- find-balance-index
  [balances balance-type balance-status]
  (some (fn [[i b]]
          (when (and (= balance-type (:balance-type b))
                     (= balance-status (:balance-status b)))
            i))
        (map-indexed vector balances)))

(defn- update-balance
  "Finds the balance matching `balance-type`/`balance-status`
  in `balances` and replaces it with `(f balance)`. Returns
  the updated vector, an `f`-supplied anomaly, or a
  `:balance/not-found` rejection."
  [balances balance-type balance-status f]
  (if-let [idx (find-balance-index balances balance-type balance-status)]
    (let-nom> [updated (f (nth balances idx))]
      (assoc balances idx updated))
    (error/reject :balance/not-found
                  {:message "Balance not found"
                   :balance-type balance-type
                   :balance-status balance-status})))

(defn- apply-leg-to-balances
  [balances {:keys [balance-type balance-status] :as leg} policies]
  (update-balance balances
                  balance-type
                  balance-status
                  (fn [b] (apply-leg b leg policies))))

(defn- apply-legs-to-balances
  "Applies each leg in `legs` (which must all target the same
  account) to `balances`. Returns the updated vector or anomaly."
  [balances legs policies]
  (reduce (fn [bs leg]
            (let [r (apply-leg-to-balances bs leg policies)]
              (if (error/anomaly? r) (reduced r) r)))
          balances
          legs))

(defn- check-available
  [pre post transaction-type policies]
  (let [{:keys [product-type currency]} (first post)
        pre-amount (available-balance product-type pre currency)
        post-amount (available-balance product-type post currency)]
    (policy/check-limit policies
                        :balance
                        {:kind {:computed {:name "available"}}
                         :transaction-type transaction-type
                         :aggregate :amount
                         :window :instant
                         :pre-value {:value (:value pre-amount)
                                     :currency currency}
                         :value {:value (:value post-amount)
                                 :currency currency}})))

(defn- changed
  "The entries from `new` whose value differs at the same
  index in `old`."
  [old new]
  (->> (map vector old new)
       (keep (fn [[a b]] (when (not= a b) b)))
       vec))

(defn apply-legs
  "Applies each leg to its target balance (each with the
  `:balance-action-apply` capability check), then runs the
  computed `:available` limit check per affected account.
  `transaction-type` scopes the `:available` limit's
  `transaction-type` filter. `account-balances` is the
  pre-leg snapshot keyed by account-id. Returns a vector of
  the changed balances ready for the caller to persist, or
  an unauthorized anomaly."
  [account-balances legs transaction-type policies]
  (reduce
   (fn [acc [account-id account-legs]]
     (let [pre (get account-balances account-id)
           post (let-nom>
                  [bs (apply-legs-to-balances pre account-legs policies)
                   _ (check-available pre bs transaction-type policies)]
                  bs)]
       (if (error/anomaly? post)
         (reduced post)
         (into acc (changed pre post)))))
   []
   (group-by :account-id legs)))

(defn new-balance
  "Requires the `:balance-action-create` capability. Rejects
  if a balance with the same composite key already exists.
  Credit and debit default to zero if not provided."
  [data exists? policies]
  (let-nom>
    [_ (check-capability :balance-action-create
                         (:balance-type data)
                         (:balance-status data)
                         policies)
     _ (if exists?
         (error/reject :balance/already-exists
                       (merge {:message "Balance already exists"}
                              (select-keys data
                                           [:account-id :balance-type
                                            :currency :balance-status])))
         true)]
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
