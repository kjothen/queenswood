(ns com.repldriven.mono.bank-uk-companies-house-simulator.companies.examples)

(def TescoRegisteredOfficeAddress
  {:address_line_1 "Tesco House, Shire Park, Kestrel Way"
   :locality "Welwyn Garden City"
   :postal_code "AL7 1GA"
   :country "United Kingdom"})

(def LlpRegisteredOfficeAddress
  {:address_line_1 "1 Example Square"
   :locality "London"
   :postal_code "EC1A 1AA"
   :country "United Kingdom"})

(def ScottishRegisteredOfficeAddress
  {:address_line_1 "10 Royal Mile"
   :locality "Edinburgh"
   :postal_code "EH1 1RE"
   :country "Scotland"})

(def DissolvedRegisteredOfficeAddress
  {:address_line_1 "Old Office"
   :locality "London"
   :postal_code "EC2A 4NE"
   :country "United Kingdom"})

(def TescoCompany
  {:company_number "00006400"
   :company_name "TESCO PLC"
   :company_status "active"
   :type "plc"
   :jurisdiction "england-wales"
   :date_of_creation "1947-11-27"
   :registered_office_address TescoRegisteredOfficeAddress})

(def LlpCompany
  {:company_number "OC301324"
   :company_name "EXAMPLE LLP"
   :company_status "active"
   :type "llp"
   :jurisdiction "england-wales"
   :date_of_creation "2003-08-12"
   :registered_office_address LlpRegisteredOfficeAddress})

(def ScottishCompany
  {:company_number "SC001234"
   :company_name "EXAMPLE SCOTTISH LTD"
   :company_status "active"
   :type "ltd"
   :jurisdiction "scotland"
   :date_of_creation "1985-04-01"
   :registered_office_address ScottishRegisteredOfficeAddress})

(def DissolvedCompany
  {:company_number "00000001"
   :company_name "DISSOLVED EXAMPLE LIMITED"
   :company_status "dissolved"
   :type "ltd"
   :jurisdiction "england-wales"
   :date_of_creation "1900-01-01"
   :registered_office_address DissolvedRegisteredOfficeAddress})

(def Company TescoCompany)

(def RegisteredOfficeAddress TescoRegisteredOfficeAddress)

(def ErrorResponse
  {:errors [{:type "ch:service" :error "company-profile-not-found"}]})

(def fixtures
  {(:company_number TescoCompany) TescoCompany
   (:company_number LlpCompany) LlpCompany
   (:company_number ScottishCompany) ScottishCompany
   (:company_number DissolvedCompany) DissolvedCompany})
