(ns lib.api)

(defn create-account
  [{:strs [customer-id name currency]}]
  (-> (js/fetch "/v1/accounts"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"
                                   "Idempotency-Key" (str (random-uuid))}
                     :body (js/JSON.stringify #js {"customer-id" customer-id
                                                   "name" name
                                                   "currency" currency})})
      (.then (fn [res]
               (-> (.json res)
                   (.then (fn [body]
                            #js {:http-status (.-status res) :body body})))))))
