(ns com.repldriven.mono.bank-idv-onfido-adapter.system
  (:require
    [com.repldriven.mono.bank-idv-onfido-adapter.commands :as commands]

    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(defn- register-webhook
  [onfido-url adapter-url]
  (let [url (str adapter-url "/webhooks/onfido/check-completed")
        res (http/request
             {:method :post
              :url (str onfido-url "/v3.6/webhooks")
              :headers {"Content-Type" "application/json"}
              :body (json/write-str {:url url})})]
    (log/info "Registered Onfido webhook"
              {:url url :status (:status res)})
    res))

(def ^:private registrar
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [onfido-url adapter-url]} config]
                         (log/info "Registering Onfido webhook"
                                   {:onfido onfido-url :adapter adapter-url})
                         (register-webhook onfido-url adapter-url)
                         :registered)))
   :system/config {:onfido-url system/required-component
                   :adapter-url system/required-component}
   :system/instance-schema some?})

(def ^:private command-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->OnfidoCommandProcessor config)))
   :system/config {:schemas system/required-component
                   :onfido-url system/required-component}
   :system/instance-schema some?})

(system/defcomponents :idv-onfido-adapter
                      {:registrar registrar
                       :command-processor command-processor})
