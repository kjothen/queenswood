(ns com.repldriven.mono.bank-account.core)

(defn deposit
  "Deposits an amount into the account named by account-id."
  [system account-id amount]
  (update-in system [:accounts account-id :balance] + amount))
