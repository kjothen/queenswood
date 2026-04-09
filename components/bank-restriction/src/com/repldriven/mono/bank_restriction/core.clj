(ns com.repldriven.mono.bank-restriction.core
  (:require
    [com.repldriven.mono.env.interface :as env]))

(defn- load-resource
  [resource kind]
  (env/config
   (str "classpath:bank-restriction/restrictions/"
        resource
        "/"
        kind
        ".yml")))

(defn restrictions
  [resource]
  {:policies (load-resource resource "policies")
   :limits (load-resource resource "limits")})
