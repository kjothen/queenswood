(ns com.repldriven.mono.iam-service-account.spec
  (:require [clojure.string :as str]))

; RFC 5322 email validation
(def Email
  [:re {:max 320 :error/message {:en "must be a valid RFC 5322 email address"}}
   "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"])

; It must be 6 to 30 lowercase letters, digits, or hyphens. It must start with
; a letter. Trailing hyphens are prohibited.
(def RFC1035
  [:re
   {:min 6
    :max 30
    :error/message {:en (str/join
                         "."
                         ["must be 6 to 30 lowercase letters, digits, or hyphen"
                          " Must start with a letter"
                          " Trailing hyphens are prohibited"])}}
   #"^[a-z]([-a-z0-9]*[a-z0-9])$"])

(def ProjectId RFC1035)
(def AccountId RFC1035)

; 31 digits
(def UniqueId [:re {:error/message "must be 21 digits"} #"^\d{21}$"])

(def ServiceAccount
  [:map [:unique-id UniqueId] [:name RFC1035] [:project-id ProjectId]
   [:email Email] [:display-name {:max 100} string?]
   [:description {:max 256} string?] [:disabled boolean?]])

(comment
  (require '[malli.core :as m] '[malli.generator :as mg])
  (mg/generate ServiceAccount))
