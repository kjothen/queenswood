(ns com.repldriven.mono.bank-company-check.core
  (:require
    [com.repldriven.mono.bank-company-check.client :as client]
    [com.repldriven.mono.bank-company-check.store :as store]
    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn- address->record
  [{:keys [address_line_1 locality postal_code country]}]
  (cond-> {}
          address_line_1
          (assoc :address-line-1 address_line_1)
          locality
          (assoc :locality locality)
          postal_code
          (assoc :postal-code postal_code)
          country
          (assoc :country country)))

(defn- api->company
  [{:keys [company_number company_name company_status type jurisdiction
           date_of_creation registered_office_address]}]
  (cond-> {:company-number company_number}
          company_name
          (assoc :company-name company_name)
          company_status
          (assoc :company-status company_status)
          type
          (assoc :type type)
          jurisdiction
          (assoc :jurisdiction jurisdiction)
          date_of_creation
          (assoc :date-of-creation date_of_creation)
          registered_office_address
          (assoc :registered-office-address
                 (address->record registered_office_address))))

(defn check-company
  [config company-number]
  (let-nom>
    [body (client/fetch-company config company-number)
     company (api->company body)
     _ (store/save-company config company)]
    company))
