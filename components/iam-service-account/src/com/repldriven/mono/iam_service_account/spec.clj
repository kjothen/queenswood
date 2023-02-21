(ns com.repldriven.mono.iam-service-account.spec
  (:refer-clojure :exclude [name])
  (:require [clojure.string :as str]))

;;;; rfc standards
;;;;

(def rfc1035 "[a-z][-a-z0-9]{4,28}[a-z0-9]")
(def rfc1035-desc
  "must be 6 to 30 lowercase letters, digits, or hyphen. Must start with a letter. Trailing hyphens are prohibited")

(def rfc5322 "[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}")
(def rfc5322-desc "must be a valid RFC 5322 email address")

;;;; shared types
;;;;

(def unique-id "\\d{21}")
(def unique-id-desc "must be 21 digits")

;;;; field types
;;;;

(def account-id rfc1035)
(def account-id-desc rfc1035-desc)

(def description ".{256}")
(def description-desc "must be maximum of 256 UTF-8 bytes")

(def display-name-desc "must be maximum of 100 UTF-8 bytes")

(def email rfc5322)
(def email-desc rfc5322-desc)

(def email-or-unique-id (str "(?:" email "|" unique-id ")"))

(def project-id rfc1035)
(def project-id-desc rfc1035-desc)

(def name (str "projects/" project-id "/serviceAccounts/" email-or-unique-id))
(def name-desc
  "must be `projects/{PROJECT_ID}/serviceAccounts/{EMAIL}` or `projects/{PROJECT_ID}/serviceAccounts/{UNIQUE_ID}")

(def project-name (str "projects/" project-id))
(def project-name-desc "must be `projects/{PROJECT_ID}`")

;;;; field specs
;;;;

(defn re-pattern-exact [s] (re-pattern (str "^" s "$")))

(def AccountId
  [:re {:error/message account-id-desc} (re-pattern-exact account-id)])

(def Description [:string {:max 256 :error/message description-desc}])

(def DisplayName [:string {:max 100 :error/message display-name-desc}])

(def Email [:re {:max 320 :error/message email-desc} (re-pattern-exact email)])

(def Name [:re {:error/message name-desc} (re-pattern-exact name)])

(def ProjectId
  [:re {:error/message project-id-desc} (re-pattern-exact project-id)])

(def ProjectName
  [:re {:error/message project-name-desc} (re-pattern-exact project-name)])

(def UniqueId
  [:re {:error/message unique-id-desc} (re-pattern-exact unique-id)])

;;;; specs
;;;;

(def CreateBody
  [:map [:account-id AccountId]
   [:service-account
    [:map [:display-name DisplayName] [:description Description]]]])

(def ServiceAccount
  [:map [:name Name] [:project-id ProjectId] [:unique-id UniqueId]
   [:email Email] [:display-name DisplayName] [:description Description]
   [:disabled boolean?]])

(comment
  (require '[malli.core :as m] '[malli.generator :as mg])
  (mg/generate ServiceAccount)
  (mg/generate CreateBody)
  (mg/generate DisplayName)
  (m/validate DisplayName "sa 123"))
