(ns com.repldriven.mono.bank-schema.interface
  (:require
    [com.repldriven.mono.schemas.balances :as balances]
    [com.repldriven.mono.schemas.cash_account_products :as
     cash-account-products]
    [com.repldriven.mono.schemas.cash_accounts :as cash-accounts]
    [com.repldriven.mono.schemas.idv :as idv]
    [com.repldriven.mono.schemas.keys :as keys]
    [com.repldriven.mono.schemas.organizations :as organizations]
    [com.repldriven.mono.schemas.party :as party]
    [com.repldriven.mono.schemas.payments :as payments]
    [com.repldriven.mono.schemas.person_identification :as
     person-identification]
    [com.repldriven.mono.schemas.payee_check :as payee-check]
    [com.repldriven.mono.schemas.policies :as policies]
    [com.repldriven.mono.schemas.tiers :as tiers]
    [com.repldriven.mono.schemas.transactions :as transactions]
    [com.repldriven.mono.schemas.types :as types]
    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schemas.balances BalanceProto$Balance)
    (com.repldriven.mono.schemas.cash_account_products
     CashAccountProductProto$CashAccountProduct)
    (com.repldriven.mono.schemas.types ProductTypeProto$ProductType)
    (com.repldriven.mono.schemas.cash_accounts
     CashAccountProto$CashAccount
     CashAccountChangelogProto$CashAccountChangelog)
    (com.repldriven.mono.schemas.idv IdvProto$Idv
                                     IdvChangelogProto$IdvChangelog)
    (com.repldriven.mono.schemas.keys ApiKeyProto$ApiKey)
    (com.repldriven.mono.schemas.organizations
     OrganizationProto$Organization
     OrganizationProto$OrganizationType
     OrganizationChangelogProto$OrganizationChangelog)
    (com.repldriven.mono.schemas.party
     PartyProto$Party
     PartyChangelogProto$PartyChangelog
     PartyNationalIdentifierProto$PartyNationalIdentifier)
    (com.repldriven.mono.schemas.person_identification
     PersonIdentificationProto$PersonIdentification)
    (com.repldriven.mono.schemas.payments
     InboundPaymentProto$InboundPayment
     InternalPaymentProto$InternalPayment
     OutboundPaymentProto$OutboundPayment)
    (com.repldriven.mono.schemas.payee_check
     PayeeCheckProto$PayeeCheck)
    (com.repldriven.mono.schemas.policies
     PolicyProto$Policy
     PolicyProto$PolicyBinding)
    (com.repldriven.mono.schemas.tiers TierProto$Tier)
    (com.repldriven.mono.schemas.transactions
     TransactionProto$Transaction
     TransactionProto$TransactionLeg)))

(def pb->Balance balances/pb->Balance)
(defn Balance->pb [m] (proto/->pb (balances/new-Balance m)))
(defn Balance->java [m] (BalanceProto$Balance/parseFrom (Balance->pb m)))

(def balance-type->int balances/BalanceType-label2val)
(def balance-status->int balances/BalanceStatus-label2val)

(def product-type->int types/ProductType-label2val)
(def int->product-type types/ProductType-val2label)

(defn product-type->pb-enum
  "Converts an product-type keyword to the protobuf
  enum value for use in FDB queries."
  [product-type]
  (ProductTypeProto$ProductType/forNumber
   (product-type->int product-type)))


(def organization-type->int organizations/OrganizationType-label2val)

(defn organization-type->pb-enum
  "Converts an organization-type keyword to the protobuf
  enum value for use in FDB queries."
  [org-type]
  (OrganizationProto$OrganizationType/forNumber
   (organization-type->int org-type)))

(defn pb->CashAccountProduct
  "Wraps the generated converter to strip the proto2 default `\"\"`
  emitted for an unset `optional string valid_from`, so callers see
  `:valid-from` only when it carries a real ISO date."
  [input]
  (let [version (cash-account-products/pb->CashAccountProduct input)]
    (cond-> version
            (not (seq (:valid-from version)))
            (dissoc :valid-from))))

(defn CashAccountProduct->pb
  [m]
  (proto/->pb (cash-account-products/new-CashAccountProduct m)))
(defn CashAccountProduct->java
  [m]
  (CashAccountProductProto$CashAccountProduct/parseFrom
   (CashAccountProduct->pb m)))

(def pb->ApiKey keys/pb->ApiKey)
(defn ApiKey->pb [m] (proto/->pb (keys/new-ApiKey m)))
(defn ApiKey->java [m] (ApiKeyProto$ApiKey/parseFrom (ApiKey->pb m)))

(def pb->Organization organizations/pb->Organization)
(defn Organization->pb [m] (proto/->pb (organizations/new-Organization m)))
(defn Organization->java
  [m]
  (OrganizationProto$Organization/parseFrom (Organization->pb m)))

(def pb->Party party/pb->Party)
(defn Party->pb [m] (proto/->pb (party/new-Party m)))
(defn Party->java [m] (PartyProto$Party/parseFrom (Party->pb m)))

(def pb->PartyNationalIdentifier party/pb->PartyNationalIdentifier)
(defn PartyNationalIdentifier->pb
  [m]
  (proto/->pb (party/new-PartyNationalIdentifier m)))
(defn PartyNationalIdentifier->java
  [m]
  (PartyNationalIdentifierProto$PartyNationalIdentifier/parseFrom
   (PartyNationalIdentifier->pb m)))

(def pb->PersonIdentification person-identification/pb->PersonIdentification)
(defn PersonIdentification->pb
  [m]
  (proto/->pb (person-identification/new-PersonIdentification m)))
(defn PersonIdentification->java
  [m]
  (PersonIdentificationProto$PersonIdentification/parseFrom
   (PersonIdentification->pb m)))

(def pb->Idv idv/pb->Idv)
(defn Idv->pb [m] (proto/->pb (idv/new-Idv m)))
(defn Idv->java [m] (IdvProto$Idv/parseFrom (Idv->pb m)))

(def pb->CashAccount cash-accounts/pb->CashAccount)
(defn CashAccount->pb [m] (proto/->pb (cash-accounts/new-CashAccount m)))
(defn CashAccount->java
  [m]
  (CashAccountProto$CashAccount/parseFrom (CashAccount->pb m)))

;; Changelog bridges

(def pb->CashAccountChangelog cash-accounts/pb->CashAccountChangelog)
(defn CashAccountChangelog->pb
  [m]
  (proto/->pb (cash-accounts/new-CashAccountChangelog m)))
(defn CashAccountChangelog->java
  [m]
  (CashAccountChangelogProto$CashAccountChangelog/parseFrom
   (CashAccountChangelog->pb m)))

(def pb->PartyChangelog party/pb->PartyChangelog)
(defn PartyChangelog->pb [m] (proto/->pb (party/new-PartyChangelog m)))
(defn PartyChangelog->java
  [m]
  (PartyChangelogProto$PartyChangelog/parseFrom (PartyChangelog->pb m)))

(def pb->IdvChangelog idv/pb->IdvChangelog)
(defn IdvChangelog->pb [m] (proto/->pb (idv/new-IdvChangelog m)))
(defn IdvChangelog->java
  [m]
  (IdvChangelogProto$IdvChangelog/parseFrom (IdvChangelog->pb m)))

(def pb->InboundPayment payments/pb->InboundPayment)
(defn InboundPayment->pb
  [m]
  (proto/->pb (payments/new-InboundPayment m)))
(defn InboundPayment->java
  [m]
  (InboundPaymentProto$InboundPayment/parseFrom
   (InboundPayment->pb m)))

(def pb->OutboundPayment payments/pb->OutboundPayment)
(defn OutboundPayment->pb
  [m]
  (proto/->pb (payments/new-OutboundPayment m)))
(defn OutboundPayment->java
  [m]
  (OutboundPaymentProto$OutboundPayment/parseFrom
   (OutboundPayment->pb m)))

(def pb->InternalPayment payments/pb->InternalPayment)
(defn InternalPayment->pb
  [m]
  (proto/->pb (payments/new-InternalPayment m)))
(defn InternalPayment->java
  [m]
  (InternalPaymentProto$InternalPayment/parseFrom
   (InternalPayment->pb m)))

(def pb->Transaction transactions/pb->Transaction)
(defn Transaction->pb [m] (proto/->pb (transactions/new-Transaction m)))
(defn Transaction->java
  [m]
  (TransactionProto$Transaction/parseFrom (Transaction->pb m)))

(def pb->TransactionLeg transactions/pb->TransactionLeg)
(defn TransactionLeg->pb [m] (proto/->pb (transactions/new-TransactionLeg m)))
(defn TransactionLeg->java
  [m]
  (TransactionProto$TransactionLeg/parseFrom (TransactionLeg->pb m)))

(def pb->OrganizationChangelog organizations/pb->OrganizationChangelog)
(defn OrganizationChangelog->pb
  [m]
  (proto/->pb (organizations/new-OrganizationChangelog m)))
(defn OrganizationChangelog->java
  [m]
  (OrganizationChangelogProto$OrganizationChangelog/parseFrom
   (OrganizationChangelog->pb m)))

(defn- unwrap-limit-kind
  "Unwraps the protojure LimitKind oneof — the record's
  :kind field holds the variant map (e.g.
  {:product-type :product-type-settlement}). Callers see
  the variant directly."
  [limit]
  (update limit
          :kind
          (fn [kind-record]
            (when kind-record
              (:kind kind-record)))))

(defn- wrap-limit-kind
  "Wraps a flat kind map into the {:kind ...} shape that
  the protojure LimitKind oneof expects."
  [limit]
  (update limit
          :kind
          (fn [kind]
            (when kind
              {:kind kind}))))

(defn pb->Tier
  [input]
  (let [tier (tiers/pb->Tier input)]
    (update tier :limits (fn [limits] (mapv unwrap-limit-kind limits)))))

(defn Tier->pb
  [m]
  (let [tier (update m :limits (fn [limits] (mapv wrap-limit-kind limits)))]
    (proto/->pb (tiers/new-Tier tier))))

(defn Tier->java
  [m]
  (TierProto$Tier/parseFrom (Tier->pb m)))

(def pb->PayeeCheck payee-check/pb->PayeeCheck)
(defn PayeeCheck->pb
  [m]
  (proto/->pb (payee-check/new-PayeeCheck m)))
(defn PayeeCheck->java
  [m]
  (PayeeCheckProto$PayeeCheck/parseFrom (PayeeCheck->pb m)))

(def pb->Policy policies/pb->Policy)
(defn Policy->pb [m] (proto/->pb (policies/new-Policy m)))
(defn Policy->java [m] (PolicyProto$Policy/parseFrom (Policy->pb m)))

(def pb->PolicyBinding policies/pb->PolicyBinding)
(defn PolicyBinding->pb [m] (proto/->pb (policies/new-PolicyBinding m)))
(defn PolicyBinding->java
  [m]
  (PolicyProto$PolicyBinding/parseFrom (PolicyBinding->pb m)))
