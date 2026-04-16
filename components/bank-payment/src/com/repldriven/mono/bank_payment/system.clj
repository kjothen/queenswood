(ns com.repldriven.mono.bank-payment.system
  (:require
    [com.repldriven.mono.bank-payment.commands :as commands]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->PaymentProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component
                   :internal-account-id nil
                   :bus nil
                   :scheme-payment-command-channel nil}
   :system/instance-schema some?})

(def ^:private event-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->PaymentEventProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component
                   :internal-account-id system/required-component}
   :system/instance-schema some?})

(system/defcomponents :payment
                      {:processor processor :event-processor event-processor})
