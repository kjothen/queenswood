(ns com.repldriven.mono.bank-clearbank.interface
  (:require
    [com.repldriven.mono.bank-clearbank.webhook.components
     :as webhook.components]))

(def webhook-components-registry webhook.components/webhook-components-registry)

(def webhook-examples-registry webhook.components/webhook-examples-registry)
