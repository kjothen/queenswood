(ns com.repldriven.mono.bank-operational-processors.main
  (:require
    [com.repldriven.mono.bank-bank.system]
    [com.repldriven.mono.bank-party.system]
    [com.repldriven.mono.bank-cash-account.system]
    [com.repldriven.mono.bank-cash-account-product.system]
    [com.repldriven.mono.bank-idv.system]

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

;; -main omitted for this scenario — bare-require registration only
