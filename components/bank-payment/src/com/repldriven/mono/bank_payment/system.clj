(ns com.repldriven.mono.bank-payment.system
  (:require
    [com.repldriven.mono.bank-payment.commands :as commands]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private default-cutoff {:zone "UTC" :hour-of-day 0})

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->PaymentProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component
                   :bus nil
                   :scheme-payment-command-channel nil
                   :business-day-cutoff default-cutoff}
   :system/instance-schema some?})

(def ^:private event-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->PaymentEventProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component
                   :business-day-cutoff default-cutoff}
   :system/instance-schema some?})

(system/defcomponents :payment
                      {:processor processor :event-processor event-processor})
