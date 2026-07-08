(ns com.repldriven.mono.bank-cash-account-product.system
  (:require
    [com.repldriven.mono.bank-cash-account-product.commands :as commands]
    [com.repldriven.mono.bank-cash-account-product.core :as core]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private seed-template
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [record-db record-store template]} config]
                         (core/new-template {:record-db record-db
                                             :record-store record-store}
                                            template))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (commands/->CashAccountProductProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :cash-account-product-templates
                      {:template seed-template :processor processor})
