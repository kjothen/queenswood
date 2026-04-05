(ns com.repldriven.mono.bank-clearbank-adapter.system
  (:require
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(defn- register-webhook
  [simulator-url webhook-url {:keys [type path]}]
  (let [url (str webhook-url path)
        res (http/request
             {:method :post
              :url (str simulator-url "/v1/webhooks")
              :headers {"Content-Type" "application/json"}
              :body (json/write-str {:type type :url url})})]
    (log/info "Registered webhook"
              {:type type :url url :status (:status res)})
    res))

(def ^:private registrar
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [simulator-url webhook-url webhooks]} config]
           (log/info "Registering webhooks"
                     {:simulator simulator-url :webhook webhook-url})
           (doseq [webhook webhooks]
             (register-webhook simulator-url webhook-url webhook))
           :registered)))
   :system/config {:simulator-url system/required-component
                   :webhook-url system/required-component
                   :webhooks nil}
   :system/instance-schema some?})

(system/defcomponents :clearbank-adapter {:registrar registrar})
