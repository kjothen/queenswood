(ns com.repldriven.mono.pubsub.pulsar.namespaces
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [com.repldriven.mono.http-client.interface :as http]
            [com.repldriven.mono.log.interface :as log])
  (:import (org.apache.pulsar.client.admin PulsarAdmin Namespaces)))

(defn- configure
  [^PulsarAdmin admin fully-qualified-namespace-name config]
  (let [namespace-url (string/join "/"
                                   [(.getServiceUrl admin) "admin/v2/namespaces"
                                    fully-qualified-namespace-name])]
    (log/info "Configuring namespace:" fully-qualified-namespace-name config)
    (doseq [[method settings] config]
      (tap> [method settings])
      (doseq [[k v] settings]
        (let [url (string/join "/" [namespace-url (name k)])
              body (json/write-str v)
              headers {"Content-Type" "application/json"}
              res (http/request
                   {:method method :url url :headers headers :body body})]
          (log/info res))))))

(defn- create
  [^PulsarAdmin admin fully-qualified-namespace-name & {:keys [config]}]
  (let [^Namespaces namespaces (.namespaces admin)
        tenant-name (first (string/split fully-qualified-namespace-name #"/"))
        namespace-names (.getNamespaces namespaces tenant-name)]
    (when-not (contains? (set namespace-names) fully-qualified-namespace-name)
      (log/info "Creating namespace:" fully-qualified-namespace-name config)
      (.createNamespace namespaces fully-qualified-namespace-name)
      (when (some? config)
        (configure admin fully-qualified-namespace-name config)))))

(defn create-namespaces
  [{:keys [^PulsarAdmin admin namespaces]}]
  (log/info "Ensure pulsar namespaces exist:" namespaces)
  (doseq [{:keys [namespace] :as opts} namespaces]
    (create admin namespace (dissoc opts :namespace))))
