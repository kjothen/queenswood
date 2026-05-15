(ns com.repldriven.mono.bank-schema.interface
  "Bank-specific protobuf schema bridge. Wraps the generated
  `com.repldriven.mono.schemas.*` namespaces with EDN-friendly
  converters: `pb->X` parses bytes into a Clojure map, `X->pb`
  serialises a map to bytes, and `X->java` parses bytes into the
  generated Java class. Also exposes enum-label converters used by
  FDB index queries."
  (:require
    [com.repldriven.mono.schemas.balances :as balances]
    [com.repldriven.mono.schemas.cash_account_products :as
     cash-account-products]
    [com.repldriven.mono.schemas.cash_accounts :as cash-accounts]
    [com.repldriven.mono.schemas.idempotency :as idempotency]
    [com.repldriven.mono.schemas.idv :as idv]
    [com.repldriven.mono.schemas.organizations :as organizations]
    [com.repldriven.mono.schemas.party :as party]
    [com.repldriven.mono.schemas.payments :as payments]
    [com.repldriven.mono.schemas.person_identification :as
     person-identification]
    [com.repldriven.mono.schemas.payee_check :as payee-check]
    [com.repldriven.mono.schemas.policies :as policies]
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
    (com.repldriven.mono.schemas.idempotency IdempotencyProto$Idempotency)
    (com.repldriven.mono.schemas.idv IdvProto$Idv
                                     IdvChangelogProto$IdvChangelog)
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
    (com.repldriven.mono.schemas.transactions
     TransactionProto$Transaction
     TransactionProto$TransactionLeg)))

(def ^{:doc "Parse Balance protobuf bytes into a Clojure map."} pb->Balance
  balances/pb->Balance)

(defn Balance->pb
  "Serialise a Balance map to protobuf bytes.

  Args:
  - m: Balance map matching the generated schema."
  [m]
  (proto/->pb (balances/new-Balance m)))

(defn Balance->java
  "Parse a Balance map into the generated Java protobuf class.

  Args:
  - m: Balance map matching the generated schema."
  [m]
  (BalanceProto$Balance/parseFrom (Balance->pb m)))

(def ^{:doc "Map of Balance type label to protobuf int value."}
     balance-type->int
  balances/BalanceType-label2val)

(def ^{:doc "Map of Balance status label to protobuf int value."}
     balance-status->int
  balances/BalanceStatus-label2val)

(def ^{:doc "Map of ProductType label to protobuf int value."} product-type->int
  types/ProductType-label2val)

(def ^{:doc "Map of ProductType protobuf int value to label."} int->product-type
  types/ProductType-val2label)

(defn product-type->pb-enum
  "Convert a product-type keyword to the protobuf enum value, for
  use in FDB index queries.

  Args:
  - product-type: `:product-type-*` keyword."
  [product-type]
  (ProductTypeProto$ProductType/forNumber
   (product-type->int product-type)))

(def ^{:doc "Map of CashAccount AccountType label to protobuf int
  value."}
     account-type->int
  cash-accounts/AccountType-label2val)

(def ^{:doc "Map of OrganizationType label to protobuf int value."}
     organization-type->int
  organizations/OrganizationType-label2val)

(defn organization-type->pb-enum
  "Convert an organization-type keyword to the protobuf enum value,
  for use in FDB index queries.

  Args:
  - org-type: `:organization-type-*` keyword."
  [org-type]
  (OrganizationProto$OrganizationType/forNumber
   (organization-type->int org-type)))

(defn pb->CashAccountProduct
  "Parse CashAccountProduct protobuf bytes into a Clojure map,
  dropping the empty-string default emitted by proto2 for an unset
  optional `valid_from` so callers see `:valid-from` only when it
  carries a real ISO date.

  Args:
  - input: protobuf bytes."
  [input]
  (let [version (cash-account-products/pb->CashAccountProduct input)]
    (cond-> version
            (not (seq (:valid-from version)))
            (dissoc :valid-from))))

(defn CashAccountProduct->pb
  "Serialise a CashAccountProduct map to protobuf bytes.

  Args:
  - m: CashAccountProduct map matching the generated schema."
  [m]
  (proto/->pb (cash-account-products/new-CashAccountProduct m)))

(defn CashAccountProduct->java
  "Parse a CashAccountProduct map into the generated Java protobuf
  class.

  Args:
  - m: CashAccountProduct map matching the generated schema."
  [m]
  (CashAccountProductProto$CashAccountProduct/parseFrom
   (CashAccountProduct->pb m)))

(def ^{:doc "Parse Idempotency protobuf bytes into a Clojure map."}
     pb->Idempotency
  idempotency/pb->Idempotency)

(defn Idempotency->pb
  "Serialise an Idempotency map to protobuf bytes."
  [m]
  (proto/->pb (idempotency/new-Idempotency m)))

(defn Idempotency->java
  "Parse an Idempotency map into the generated Java protobuf class."
  [m]
  (IdempotencyProto$Idempotency/parseFrom (Idempotency->pb m)))

(def ^{:doc "Parse Organization protobuf bytes into a Clojure map."}
     pb->Organization
  organizations/pb->Organization)

(defn Organization->pb
  "Serialise an Organization map to protobuf bytes.

  Args:
  - m: Organization map matching the generated schema."
  [m]
  (proto/->pb (organizations/new-Organization m)))

(defn Organization->java
  "Parse an Organization map into the generated Java protobuf class.

  Args:
  - m: Organization map matching the generated schema."
  [m]
  (OrganizationProto$Organization/parseFrom (Organization->pb m)))

(def ^{:doc "Parse Party protobuf bytes into a Clojure map."} pb->Party
  party/pb->Party)

(defn Party->pb
  "Serialise a Party map to protobuf bytes.

  Args:
  - m: Party map matching the generated schema."
  [m]
  (proto/->pb (party/new-Party m)))

(defn Party->java
  "Parse a Party map into the generated Java protobuf class.

  Args:
  - m: Party map matching the generated schema."
  [m]
  (PartyProto$Party/parseFrom (Party->pb m)))

(def ^{:doc
       "Parse PartyNationalIdentifier protobuf bytes into a
  Clojure map."}
     pb->PartyNationalIdentifier
  party/pb->PartyNationalIdentifier)

(defn PartyNationalIdentifier->pb
  "Serialise a PartyNationalIdentifier map to protobuf bytes.

  Args:
  - m: PartyNationalIdentifier map matching the generated schema."
  [m]
  (proto/->pb (party/new-PartyNationalIdentifier m)))

(defn PartyNationalIdentifier->java
  "Parse a PartyNationalIdentifier map into the generated Java
  protobuf class.

  Args:
  - m: PartyNationalIdentifier map matching the generated schema."
  [m]
  (PartyNationalIdentifierProto$PartyNationalIdentifier/parseFrom
   (PartyNationalIdentifier->pb m)))

(def ^{:doc "Parse PersonIdentification protobuf bytes into a
  Clojure map."}
     pb->PersonIdentification
  person-identification/pb->PersonIdentification)

(defn PersonIdentification->pb
  "Serialise a PersonIdentification map to protobuf bytes.

  Args:
  - m: PersonIdentification map matching the generated schema."
  [m]
  (proto/->pb (person-identification/new-PersonIdentification m)))

(defn PersonIdentification->java
  "Parse a PersonIdentification map into the generated Java
  protobuf class.

  Args:
  - m: PersonIdentification map matching the generated schema."
  [m]
  (PersonIdentificationProto$PersonIdentification/parseFrom
   (PersonIdentification->pb m)))

(def ^{:doc "Parse Idv protobuf bytes into a Clojure map."} pb->Idv idv/pb->Idv)

(defn Idv->pb
  "Serialise an Idv map to protobuf bytes.

  Args:
  - m: Idv map matching the generated schema."
  [m]
  (proto/->pb (idv/new-Idv m)))

(defn Idv->java
  "Parse an Idv map into the generated Java protobuf class.

  Args:
  - m: Idv map matching the generated schema."
  [m]
  (IdvProto$Idv/parseFrom (Idv->pb m)))

(def ^{:doc "Parse CashAccount protobuf bytes into a Clojure map."}
     pb->CashAccount
  cash-accounts/pb->CashAccount)

(defn CashAccount->pb
  "Serialise a CashAccount map to protobuf bytes.

  Args:
  - m: CashAccount map matching the generated schema."
  [m]
  (proto/->pb (cash-accounts/new-CashAccount m)))

(defn CashAccount->java
  "Parse a CashAccount map into the generated Java protobuf class.

  Args:
  - m: CashAccount map matching the generated schema."
  [m]
  (CashAccountProto$CashAccount/parseFrom (CashAccount->pb m)))

(def ^{:doc "Parse CashAccountChangelog protobuf bytes into a
  Clojure map."}
     pb->CashAccountChangelog
  cash-accounts/pb->CashAccountChangelog)

(defn CashAccountChangelog->pb
  "Serialise a CashAccountChangelog map to protobuf bytes.

  Args:
  - m: CashAccountChangelog map matching the generated schema."
  [m]
  (proto/->pb (cash-accounts/new-CashAccountChangelog m)))

(defn CashAccountChangelog->java
  "Parse a CashAccountChangelog map into the generated Java
  protobuf class.

  Args:
  - m: CashAccountChangelog map matching the generated schema."
  [m]
  (CashAccountChangelogProto$CashAccountChangelog/parseFrom
   (CashAccountChangelog->pb m)))

(def ^{:doc "Parse PartyChangelog protobuf bytes into a Clojure
  map."}
     pb->PartyChangelog
  party/pb->PartyChangelog)

(defn PartyChangelog->pb
  "Serialise a PartyChangelog map to protobuf bytes.

  Args:
  - m: PartyChangelog map matching the generated schema."
  [m]
  (proto/->pb (party/new-PartyChangelog m)))

(defn PartyChangelog->java
  "Parse a PartyChangelog map into the generated Java protobuf
  class.

  Args:
  - m: PartyChangelog map matching the generated schema."
  [m]
  (PartyChangelogProto$PartyChangelog/parseFrom (PartyChangelog->pb m)))

(def ^{:doc "Parse IdvChangelog protobuf bytes into a Clojure map."}
     pb->IdvChangelog
  idv/pb->IdvChangelog)

(defn IdvChangelog->pb
  "Serialise an IdvChangelog map to protobuf bytes.

  Args:
  - m: IdvChangelog map matching the generated schema."
  [m]
  (proto/->pb (idv/new-IdvChangelog m)))

(defn IdvChangelog->java
  "Parse an IdvChangelog map into the generated Java protobuf
  class.

  Args:
  - m: IdvChangelog map matching the generated schema."
  [m]
  (IdvChangelogProto$IdvChangelog/parseFrom (IdvChangelog->pb m)))

(def ^{:doc "Parse InboundPayment protobuf bytes into a Clojure
  map."}
     pb->InboundPayment
  payments/pb->InboundPayment)

(defn InboundPayment->pb
  "Serialise an InboundPayment map to protobuf bytes.

  Args:
  - m: InboundPayment map matching the generated schema."
  [m]
  (proto/->pb (payments/new-InboundPayment m)))

(defn InboundPayment->java
  "Parse an InboundPayment map into the generated Java protobuf
  class.

  Args:
  - m: InboundPayment map matching the generated schema."
  [m]
  (InboundPaymentProto$InboundPayment/parseFrom
   (InboundPayment->pb m)))

(def ^{:doc "Parse OutboundPayment protobuf bytes into a Clojure
  map."}
     pb->OutboundPayment
  payments/pb->OutboundPayment)

(defn OutboundPayment->pb
  "Serialise an OutboundPayment map to protobuf bytes.

  Args:
  - m: OutboundPayment map matching the generated schema."
  [m]
  (proto/->pb (payments/new-OutboundPayment m)))

(defn OutboundPayment->java
  "Parse an OutboundPayment map into the generated Java protobuf
  class.

  Args:
  - m: OutboundPayment map matching the generated schema."
  [m]
  (OutboundPaymentProto$OutboundPayment/parseFrom
   (OutboundPayment->pb m)))

(def ^{:doc "Parse InternalPayment protobuf bytes into a Clojure
  map."}
     pb->InternalPayment
  payments/pb->InternalPayment)

(defn InternalPayment->pb
  "Serialise an InternalPayment map to protobuf bytes.

  Args:
  - m: InternalPayment map matching the generated schema."
  [m]
  (proto/->pb (payments/new-InternalPayment m)))

(defn InternalPayment->java
  "Parse an InternalPayment map into the generated Java protobuf
  class.

  Args:
  - m: InternalPayment map matching the generated schema."
  [m]
  (InternalPaymentProto$InternalPayment/parseFrom
   (InternalPayment->pb m)))

(def ^{:doc "Parse Transaction protobuf bytes into a Clojure map."}
     pb->Transaction
  transactions/pb->Transaction)

(defn Transaction->pb
  "Serialise a Transaction map to protobuf bytes.

  Args:
  - m: Transaction map matching the generated schema."
  [m]
  (proto/->pb (transactions/new-Transaction m)))

(defn Transaction->java
  "Parse a Transaction map into the generated Java protobuf class.

  Args:
  - m: Transaction map matching the generated schema."
  [m]
  (TransactionProto$Transaction/parseFrom (Transaction->pb m)))

(def ^{:doc "Parse TransactionLeg protobuf bytes into a Clojure
  map."}
     pb->TransactionLeg
  transactions/pb->TransactionLeg)

(defn TransactionLeg->pb
  "Serialise a TransactionLeg map to protobuf bytes.

  Args:
  - m: TransactionLeg map matching the generated schema."
  [m]
  (proto/->pb (transactions/new-TransactionLeg m)))

(defn TransactionLeg->java
  "Parse a TransactionLeg map into the generated Java protobuf
  class.

  Args:
  - m: TransactionLeg map matching the generated schema."
  [m]
  (TransactionProto$TransactionLeg/parseFrom (TransactionLeg->pb m)))

(def ^{:doc "Parse OrganizationChangelog protobuf bytes into a
  Clojure map."}
     pb->OrganizationChangelog
  organizations/pb->OrganizationChangelog)

(defn OrganizationChangelog->pb
  "Serialise an OrganizationChangelog map to protobuf bytes.

  Args:
  - m: OrganizationChangelog map matching the generated schema."
  [m]
  (proto/->pb (organizations/new-OrganizationChangelog m)))

(defn OrganizationChangelog->java
  "Parse an OrganizationChangelog map into the generated Java
  protobuf class.

  Args:
  - m: OrganizationChangelog map matching the generated schema."
  [m]
  (OrganizationChangelogProto$OrganizationChangelog/parseFrom
   (OrganizationChangelog->pb m)))

(def ^{:doc "Parse PayeeCheck protobuf bytes into a Clojure map."}
     pb->PayeeCheck
  payee-check/pb->PayeeCheck)

(defn PayeeCheck->pb
  "Serialise a PayeeCheck map to protobuf bytes.

  Args:
  - m: PayeeCheck map matching the generated schema."
  [m]
  (proto/->pb (payee-check/new-PayeeCheck m)))

(defn PayeeCheck->java
  "Parse a PayeeCheck map into the generated Java protobuf class.

  Args:
  - m: PayeeCheck map matching the generated schema."
  [m]
  (PayeeCheckProto$PayeeCheck/parseFrom (PayeeCheck->pb m)))

(def ^{:doc "Parse Policy protobuf bytes into a Clojure map."} pb->Policy
  policies/pb->Policy)

(defn Policy->pb
  "Serialise a Policy map to protobuf bytes.

  Args:
  - m: Policy map matching the generated schema."
  [m]
  (proto/->pb (policies/new-Policy m)))

(defn Policy->java
  "Parse a Policy map into the generated Java protobuf class.

  Args:
  - m: Policy map matching the generated schema."
  [m]
  (PolicyProto$Policy/parseFrom (Policy->pb m)))

(def ^{:doc "Parse PolicyBinding protobuf bytes into a Clojure
  map."}
     pb->PolicyBinding
  policies/pb->PolicyBinding)

(defn PolicyBinding->pb
  "Serialise a PolicyBinding map to protobuf bytes.

  Args:
  - m: PolicyBinding map matching the generated schema."
  [m]
  (proto/->pb (policies/new-PolicyBinding m)))

(defn PolicyBinding->java
  "Parse a PolicyBinding map into the generated Java protobuf
  class.

  Args:
  - m: PolicyBinding map matching the generated schema."
  [m]
  (PolicyProto$PolicyBinding/parseFrom (PolicyBinding->pb m)))
