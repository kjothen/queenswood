(ns com.repldriven.mono.bank-test-model.accounts)

(defn deposit
  [account amount]
  (update account :balance + amount))

;; TODO: close-account — pure, mirrors the production rule below
