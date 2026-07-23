(ns com.repldriven.queenswood.uk-companies-house-simulator.companies.examples)

;; Fictional demo companies in the Companies House shape, with
;; deliberately impossible postcodes (QZ/ZX) so no one mistakes them for
;; real entities. Numbers and statuses are stable for callers and tests:
;;   SC998137  SIRIUS CYBERNETICS CORPORATION LTD  active     ltd
;;   TY046601  TYRELL CORPORATION (UK) LTD          active     ltd
;;   WY002122  WEYLAND-YUTANI CORP LTD              active     ltd
;;   SC299784  CYBERDYNE SYSTEMS UK LTD             active     ltd  (Scotland)
;;   IN071194  INITECH LTD                          dissolved  ltd

(def SiriusCyberneticsRegisteredOfficeAddress
  {:address_line_1 "42 Improbability Way"
   :locality "London"
   :postal_code "QZ1 9ZX"
   :country "United Kingdom"})

(def SiriusCyberneticsCorp
  {:company_number "SC998137"
   :company_name "SIRIUS CYBERNETICS CORPORATION LTD"
   :company_status "active"
   :type "ltd"
   :jurisdiction "england-wales"
   :date_of_creation "2009-02-11"
   :registered_office_address SiriusCyberneticsRegisteredOfficeAddress})

(def TyrellRegisteredOfficeAddress
  {:address_line_1 "2019 Nexus Plaza"
   :locality "Manchester"
   :postal_code "QZ4 2BT"
   :country "United Kingdom"})

(def TyrellCorp
  {:company_number "TY046601"
   :company_name "TYRELL CORPORATION (UK) LTD"
   :company_status "active"
   :type "ltd"
   :jurisdiction "england-wales"
   :date_of_creation "2016-06-21"
   :registered_office_address TyrellRegisteredOfficeAddress})

(def WeylandYutaniRegisteredOfficeAddress
  {:address_line_1 "1 Cargo Bay Road"
   :locality "Bristol"
   :postal_code "ZX6 1QA"
   :country "United Kingdom"})

(def WeylandYutaniCorp
  {:company_number "WY002122"
   :company_name "WEYLAND-YUTANI CORP LTD"
   :company_status "active"
   :type "ltd"
   :jurisdiction "england-wales"
   :date_of_creation "2004-12-08"
   :registered_office_address WeylandYutaniRegisteredOfficeAddress})

(def CyberdyneRegisteredOfficeAddress
  {:address_line_1 "29 Skynet Street"
   :locality "Edinburgh"
   :postal_code "QZ2 4AD"
   :country "United Kingdom"})

(def CyberdyneSystems
  {:company_number "SC299784"
   :company_name "CYBERDYNE SYSTEMS UK LTD"
   :company_status "active"
   :type "ltd"
   :jurisdiction "scotland"
   :date_of_creation "2018-08-29"
   :registered_office_address CyberdyneRegisteredOfficeAddress})

(def InitechRegisteredOfficeAddress
  {:address_line_1 "Unit 3, Stapler House"
   :locality "Leeds"
   :postal_code "ZX1 4AP"
   :country "United Kingdom"})

(def InitechLtd
  {:company_number "IN071194"
   :company_name "INITECH LTD"
   :company_status "dissolved"
   :type "ltd"
   :jurisdiction "england-wales"
   :date_of_creation "1999-02-19"
   :registered_office_address InitechRegisteredOfficeAddress})

(def Company SiriusCyberneticsCorp)

(def RegisteredOfficeAddress SiriusCyberneticsRegisteredOfficeAddress)

(def ErrorResponse
  {:errors [{:type "ch:service" :error "company-profile-not-found"}]})

(def fixtures
  {(:company_number SiriusCyberneticsCorp) SiriusCyberneticsCorp
   (:company_number TyrellCorp) TyrellCorp
   (:company_number WeylandYutaniCorp) WeylandYutaniCorp
   (:company_number CyberdyneSystems) CyberdyneSystems
   (:company_number InitechLtd) InitechLtd})
