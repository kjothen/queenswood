(ns com.repldriven.mono.bank-api.payee-check.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def PayeeCheckRequest
  {:creditor-name "Arthur Dent"
   :account {:sort-code "040062" :account-number "12345678"}
   :account-type :personal})

(def PayeeCheck
  {:check-id "pyc_01HX8K3N7QJVZR9YTBM2D6F4A1"
   :request {:creditor-name "Arthur Dent"
             :account {:sort-code "040062" :account-number "12345678"}
             :account-type :personal}
   :result {:match-result :close-match
            :actual-name "Jane A Doe"
            :reason-code "PANM"
            :reason "Partial name match"}
   :created-at "2026-04-20T14:23:05.123Z"
   :expires-at "2026-04-20T14:38:05.123Z"})

(def PayeeCheckList
  {:items [PayeeCheck] :links {:next "/v1/payee-checks?page[after]=djE6..."}})

(def registry (examples-registry [#'PayeeCheck #'PayeeCheckList]))
