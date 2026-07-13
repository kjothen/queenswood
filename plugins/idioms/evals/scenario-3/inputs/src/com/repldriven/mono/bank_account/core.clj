(ns com.repldriven.mono.bank-account.core)

(defn deposit
  "Deposits an amount into the account."
  [account amount]
  (update account :balance + amount))
