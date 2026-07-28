(ns com.repldriven.queenswood.api.company-registries.queries
  (:require
    [com.repldriven.queenswood.api.commands :as commands]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [company-registries]} dispatchers]
    company-registries))

(defn lookup
  "Resolve `company-number` against `registry-id`, or the adapter's
  default registry when it is nil. Returns the `commands/send` ring
  response — 200 plus the company body on success. Onboarding calls
  this directly; the reply carries `:registry-id` so the caller does
  not have to know the default."
  [request registry-id company-number]
  (commands/send (dispatcher request)
                 request
                 "lookup-company"
                 "company"
                 {:registry-id registry-id :company-number company-number}))

(defn lookup-company
  [request]
  (let [{:keys [registry-id company-number]} (get-in request
                                                     [:parameters :path])]
    (lookup request registry-id company-number)))
