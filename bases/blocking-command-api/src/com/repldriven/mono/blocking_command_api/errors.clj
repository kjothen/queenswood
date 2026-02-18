(ns com.repldriven.mono.blocking-command-api.errors
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]))

(defn coercion-ex->command-response
  "Convert a Reitit coercion exception and request to a command-response error body."
  [req category ex]
  (command/req->command-response
   req
   (error/fail category (select-keys (ex-data ex) [:humanized :in]))))
