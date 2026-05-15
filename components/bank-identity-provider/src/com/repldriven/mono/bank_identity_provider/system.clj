(ns com.repldriven.mono.bank-identity-provider.system
  (:require
    [com.repldriven.mono.bank-identity-provider.store :as store]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (store/->client config)))
   :system/config {:base-url system/required-component
                   :realm system/required-component
                   :admin-client-id system/required-component
                   :admin-client-secret system/required-component}
   :system/instance-schema some?})

(system/defcomponents :identity-provider {:client client})
