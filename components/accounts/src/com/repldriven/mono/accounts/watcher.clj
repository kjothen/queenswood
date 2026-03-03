(ns com.repldriven.mono.accounts.watcher
  (:require
    [com.repldriven.mono.accounts.domain :as domain]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schema.interface :as schema]))

(defn handle-change
  "Transitions the account if applicable, saving directly
  to the store within the process-changelog transaction."
  [store record]
  (let [account (schema/pb->Account record)]
    (when-some [transitioned (domain/transition account)]
      (fdb/save-record store (schema/Account->java transitioned)))))
