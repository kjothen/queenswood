(ns com.repldriven.mono.blocking-command-api.commands.routes
  (:require
   [com.repldriven.mono.blocking-command-api.commands.handlers :as handlers]))

(defmacro RequestData [data] [:map [:data data]])

(defmacro ResponseData [data] [:map [:data data]])

(def Command [:map [:correlation_id string?] [:type string?] [:id string?]])
(def CommandRequest (RequestData Command))

(def CommandResult [:map [:correlation_id string?] [:type string?] [:id string?]])
(def CommandResponse (ResponseData CommandResult))

(defn routes
  [ctx]
  ["/api" {:interceptors (:interceptors ctx)}
   ["/command"
    {:post {:summary "negotiated request & response (json, edn, transit)"
            :parameters {:body CommandRequest}
            :responses {200 {:body CommandResponse}}
            :handler handlers/create}}]])
