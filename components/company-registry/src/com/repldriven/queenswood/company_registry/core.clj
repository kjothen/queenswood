(ns com.repldriven.queenswood.company-registry.core
  (:require
    [com.repldriven.queenswood.company-registry.store :as store]
    [com.repldriven.queenswood.company-registry.uk-companies-house
     :as uk-companies-house]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :refer [assoc-some]]))

(defn- address->record
  [{:keys [address_line_1 locality postal_code country]}]
  (assoc-some {}
              :address-line-1 address_line_1
              :locality locality
              :postal-code postal_code
              :country country))

(defn- api->company
  [{:keys [company_number company_name company_status type jurisdiction
           date_of_creation registered_office_address]}]
  (assoc-some {:company-number company_number}
              :company-name company_name
              :company-status company_status
              :type type
              :jurisdiction jurisdiction
              :date-of-creation date_of_creation
              :registered-office-address
              (when registered_office_address
                (address->record registered_office_address))))

(defn lookup-company
  [config company-number]
  (let-nom>
    [body (uk-companies-house/fetch-company config company-number)
     company (api->company body)
     _ (store/save-company config company)]
    company))

(defn get-company
  [txn-or-config company-number]
  (store/get-company txn-or-config company-number))
