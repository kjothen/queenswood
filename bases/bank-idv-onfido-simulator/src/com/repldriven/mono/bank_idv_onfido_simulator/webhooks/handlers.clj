(ns com.repldriven.mono.bank-idv-onfido-simulator.webhooks.handlers
  (:require
    [com.repldriven.mono.utility.interface :refer [uuidv7]]))

(defn list-webhooks
  [_config]
  (fn [request]
    (let [{:keys [state]} request]
      {:status 200 :body {:webhooks (vec (:webhooks @state))}})))

(defn register-webhook
  [_config]
  (fn [request]
    (let [{:keys [state parameters]} request
          {:keys [url]} (:body parameters)
          id (str (uuidv7))
          webhook {:id id :url url}]
      (swap! state update :webhooks conj webhook)
      {:status 201 :body webhook})))

(defn deregister-webhook
  [_config]
  (fn [request]
    (let [{:keys [state parameters]} request
          id (get-in parameters [:path :id])
          existing (some (fn [w] (when (= id (:id w)) w))
                         (:webhooks @state))]
      (if-not existing
        {:status 404
         :body {:title "NOT_FOUND"
                :type "webhook/not-found"
                :status 404
                :detail (str "No webhook with id: " id)}}
        (do (swap! state update
              :webhooks
              (fn [ws] (vec (remove (fn [w] (= id (:id w))) ws))))
            {:status 204 :body nil})))))
