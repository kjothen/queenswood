(ns com.repldriven.mono.bank-clearbank-adapter.nonce)

(defonce nonces (atom #{}))

(defn record
  [n]
  (swap! nonces conj n))

(defn seen?
  [n]
  (contains? @nonces n))
