(ns com.repldriven.mono.bank-api.restriction.queries
  (:require
    [com.repldriven.mono.bank-restriction.interface
     :as restriction]))

(defn get-organization-policies
  [_request]
  {:status 200
   :body (:policies
          (restriction/default-customer-organization-restrictions))})

(defn get-organization-limits
  [_request]
  {:status 200
   :body (:limits
          (restriction/default-customer-organization-restrictions))})

(defn get-cash-account-product-policies
  [_request]
  {:status 200
   :body (:policies
          (restriction/default-cash-account-product-restrictions))})

(defn get-cash-account-product-limits
  [_request]
  {:status 200
   :body (:limits
          (restriction/default-cash-account-product-restrictions))})

(defn get-cash-account-policies
  [_request]
  {:status 200
   :body (:policies
          (restriction/default-cash-account-restrictions))})

(defn get-cash-account-limits
  [_request]
  {:status 200
   :body (:limits
          (restriction/default-cash-account-restrictions))})

(defn get-party-policies
  [_request]
  {:status 200
   :body (:policies
          (restriction/default-person-party-restrictions))})

(defn get-party-limits
  [_request]
  {:status 200
   :body (:limits
          (restriction/default-person-party-restrictions))})
