(ns com.repldriven.mono.bank-interest.domain)

(def ^:private micro-scale 1000000)

(defn- net-balance
  [balance]
  (- (:credit balance 0) (:debit balance 0)))

(defn daily-interest
  [balance interest-rate-bps]
  (let [{:keys [credit-carry]} balance]
    (when-not (zero? interest-rate-bps)
      (let [net (net-balance balance)
            total-micro (+ (* net
                              interest-rate-bps
                              (quot micro-scale 10000))
                           (* credit-carry 365))
            daily-micro (quot total-micro 365)
            whole-units (quot daily-micro micro-scale)
            new-carry (rem daily-micro micro-scale)]
        {:whole-units whole-units :carry new-carry}))))

(defn accrual-idempotency-key
  [account-id as-of-date]
  (str "accrue-" account-id "-" as-of-date))

(defn capitalization-idempotency-key
  [account-id as-of-date]
  (str "capitalize-" account-id "-" as-of-date))

(defn accrual-transaction
  [interest-payable-id account-id currency whole-units
   as-of-date]
  (when-not (zero? whole-units)
    {:idempotency-key (accrual-idempotency-key
                       account-id
                       as-of-date)
     :transaction-type :transaction-type-interest-accrual
     :currency currency
     :reference (str "Daily interest accrual " as-of-date)
     :legs [{:account-id interest-payable-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount whole-units}
            {:account-id account-id
             :balance-type :balance-type-interest-accrued
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount whole-units}]}))

(defn capitalization-transaction
  [interest-payable-id account-id currency balance as-of-date]
  (let [accrued (net-balance balance)]
    (when-not (zero? accrued)
      {:idempotency-key (capitalization-idempotency-key
                         account-id
                         as-of-date)
       :transaction-type
       :transaction-type-interest-capital
       :currency currency
       :reference (str "Monthly interest capitalization "
                       as-of-date)
       :legs [{:account-id interest-payable-id
               :balance-type :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id account-id
               :balance-type :balance-type-interest-paid
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}
              {:account-id account-id
               :balance-type
               :balance-type-interest-accrued
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id interest-payable-id
               :balance-type :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}
              {:account-id account-id
               :balance-type
               :balance-type-interest-paid
               :balance-status :balance-status-posted
               :side :leg-side-debit
               :amount accrued}
              {:account-id account-id
               :balance-type
               :balance-type-default
               :balance-status :balance-status-posted
               :side :leg-side-credit
               :amount accrued}]})))
