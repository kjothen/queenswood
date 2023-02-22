(ns com.repldriven.mono.iam.service-account.spec
  (:refer-clojure :exclude [name])
  (:require [clojure.string :as str])
  (:require [com.repldriven.mono.iam.spec :refer :all]))

;;;; field types
;;;;

(def account-id rfc1035)

(def email-address-or-unique-id
  {:re (str "(?:" (:re email-address) "|" (:re unique-id) ")")
   :desc "must be an `{EMAIL_ADDRESS}` or `{UNIQUE_ID}`"})

(def name
  {:re (str "projects/" (:re project-id)
            "/serviceAccounts/" (:re email-address-or-unique-id))
   :desc
   "must be `projects/{PROJECT_ID}/serviceAccounts/{EMAIL_ADDRESS}` or `projects/{PROJECT_ID}/serviceAccounts/{UNIQUE_ID}"})

;;;; field specs
;;;;

(def AccountId
  [:re {:error/message (:desc account-id)} (re-pattern-exact (:re account-id))])

(def EmailAddress
  [:re {:max 320 :error/message (:desc email-address)}
   (re-pattern-exact (:re email-address))])

(def Name [:re {:error/message (:desc name)} (re-pattern-exact (:re name))])

(def EmailAddressOrUniqueId
  [:re {:error/message (:desc email-address-or-unique-id)}
   (re-pattern-exact (:re email-address-or-unique-id))])

;;;; specs
;;;;

(def CreateBody
  [:map [:account-id AccountId]
   [:service-account
    [:map [:display-name DisplayName] [:description Description]]]])

(def ServiceAccount
  [:map [:name Name] [:project-id ProjectId] [:unique-id UniqueId]
   [:email EmailAddress] [:display-name DisplayName] [:description Description]
   [:disabled boolean?]])

(comment
  (require '[malli.core :as m] '[malli.generator :as mg])
  (mg/generate CreateBody)
  (mg/generate ServiceAccount))
