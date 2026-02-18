(ns com.repldriven.mono.blocking-command-api.errors
  (:require
    [com.repldriven.mono.command.interface :as command]))

(defn coercion-ex->command-response
  "Convert a Reitit coercion exception and request to a command-response error body."
  [req category ex]
  (command/req->command-error-response req
                                       category
                                       (select-keys (ex-data ex)
                                                    [:humanized :in])))
