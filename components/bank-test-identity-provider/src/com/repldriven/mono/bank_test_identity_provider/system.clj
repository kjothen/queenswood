(ns com.repldriven.mono.bank-test-identity-provider.system
  (:require
    [com.repldriven.mono.bank-test-identity-provider.store :as store]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (store/->client config)))
   :system/config {:issuer nil}
   :system/instance-schema some?})

(system/defcomponents :test-identity-provider {:client client})
