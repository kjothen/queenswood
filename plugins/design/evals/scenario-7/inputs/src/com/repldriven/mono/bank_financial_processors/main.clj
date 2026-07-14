(ns com.repldriven.mono.bank-financial-processors.main
  (:require
    [com.repldriven.mono.bank-payment.system]
    [com.repldriven.mono.bank-transaction.system]
    [com.repldriven.mono.bank-interest.system]
    [com.repldriven.mono.bank-payee-check.system]

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.system.interface :as system])
  (:gen-class))

;; -main omitted for this scenario — bare-require registration only
