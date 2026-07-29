(ns com.repldriven.queenswood.api.companies.queries
  (:require
    [com.repldriven.queenswood.api.commands :as commands]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [companies]} dispatchers]
    companies))

(defn lookup
  "Resolve `company-number` against the registry of record. Returns the
  `commands/send` ring response — 200 plus the company body on success.
  Onboarding calls this directly; the reply carries `:registry-id`
  stamped by the adapter, which the caller snapshots onto the bank."
  [request company-number]
  (commands/send (dispatcher request)
                 request
                 "lookup-company"
                 "company"
                 {:company-number company-number}))

(defn lookup-company
  [request]
  (let [{:keys [company-number]} (get-in request [:parameters :path])]
    (lookup request company-number)))
