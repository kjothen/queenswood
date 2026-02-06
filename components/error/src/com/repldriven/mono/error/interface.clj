(ns com.repldriven.mono.error.interface
  (:require [de.otto.nom.core :as nom]))

;; Predicates
(defn anomaly? [x] (nom/anomaly? x))

;; Creation
(defn fail
  ([category] (nom/fail category))
  ([category message] (nom/fail category message))
  ([category message data] (nom/fail category message data)))

;; Introspection
(defn kind [x] (nom/kind x))

;; Threading macros
(defmacro nom-> [& forms] `(nom/nom-> ~@forms))
(defmacro nom->> [& forms] `(nom/nom->> ~@forms))

;; Let bindings
(defmacro let-nom [bindings & body] `(nom/let-nom ~bindings ~@body))
(defmacro let-nom> [bindings & body] `(nom/let-nom> ~bindings ~@body))

;; Context wrapper
(defmacro with-nom [& body] `(nom/with-nom ~@body))

;; Exception catching
(defmacro try-nom
  "Wraps body in try-catch and converts exceptions to anomalies.

  Usage:
    (try-nom :my-component/operation
             \"Operation description\"
             (risky-operation))

  Returns the result of the body or an anomaly if an exception is thrown."
  [category message & body]
  `(try
     ~@body
     (catch Exception e#
       (fail ~category
             ~message
             {:exception e#
              :message (.getMessage e#)
              :cause (.getCause e#)}))))
