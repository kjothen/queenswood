(ns com.repldriven.mono.bank-company-registry.core
  (:require
    [com.repldriven.mono.bank-company-registry.store :as store]
    [com.repldriven.mono.bank-company-registry.uk-companies-house
     :as uk-companies-house]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :refer [assoc-some]]))

(def default-registry "uk-companies-house")

(def available-registries
  [{:registry-id default-registry :name "UK Companies House"}])

(defn- validate-registry
  [registry-id]
  (when (not= default-registry registry-id)
    (error/reject :company-registry/registry-not-found
                  {:message "Company registry not supported"
                   :registry registry-id})))

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
  [config registry-id company-number]
  (let-nom>
    [_ (validate-registry registry-id)
     body (uk-companies-house/fetch-company config company-number)
     company (api->company body)
     _ (store/save-company config company)]
    company))

(defn get-company
  [txn-or-config registry-id company-number]
  (let-nom>
    [_ (validate-registry registry-id)]
    (store/get-company txn-or-config company-number)))
