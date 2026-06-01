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
    [com.repldriven.mono.schemas.company :as company]
    [com.repldriven.mono.schemas.idempotency :as idempotency]
    [com.repldriven.mono.schemas.idv :as idv]
    [com.repldriven.mono.schemas.ledger_accounts :as ledger-accounts]
    [com.repldriven.mono.schemas.memberships :as memberships]
    [com.repldriven.mono.schemas.banks :as banks]
    [com.repldriven.mono.schemas.party :as party]
    [com.repldriven.mono.schemas.payments :as payments]
    [com.repldriven.mono.schemas.person_identification :as
     person-identification]
    [com.repldriven.mono.schemas.payee_check :as payee-check]
    [com.repldriven.mono.schemas.policies :as policies]
    [com.repldriven.mono.schemas.transactions :as transactions]
    [com.repldriven.mono.schemas.types :as types]
    [com.repldriven.mono.schemas.users :as users]
    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schemas.balances BalanceProto$Balance)
    (com.repldriven.mono.schemas.cash_account_products
     CashAccountProductProto$CashAccountProduct
     CashAccountProductProto$GlAccountType
     CashAccountProductProto$GlAccountClass
     CashAccountProductProto$Required
     CashAccountProductProto$IsoCashAccountType
     CashAccountProductProto$SubLedgerKind)
    (com.repldriven.mono.schemas.types ProductTypeProto$ProductType)
    (com.repldriven.mono.schemas.cash_accounts
     CashAccountProto$CashAccount
     CashAccountChangelogProto$CashAccountChangelog)
    (com.repldriven.mono.schemas.company CompanyProto$Company)
    (com.repldriven.mono.schemas.idempotency IdempotencyProto$Idempotency)
    (com.repldriven.mono.schemas.idv IdvProto$Idv
                                     IdvChangelogProto$IdvChangelog)
    (com.repldriven.mono.schemas.ledger_accounts
     LedgerAccountProto$LedgerAccount)
    (com.repldriven.mono.schemas.banks
     BankProto$Bank
     BankProto$BankType
     BankChangelogProto$BankChangelog)
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
     TransactionProto$TransactionLeg)
    (com.repldriven.mono.schemas.users
     UserProto$User
     UserProto$IdentityProvider
     UserProto$UserStatus)
    (com.repldriven.mono.schemas.memberships
     MembershipProto$Membership
     MembershipProto$Role)))

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

(def ^{:doc "Map of GlAccountType label to protobuf int value."}
     gl-account-type->int
  cash-account-products/GlAccountType-label2val)

(defn gl-account-type->pb-enum
  "Convert a gl-account-type keyword to the protobuf enum value, for
  use in FDB index queries.

  Args:
  - gl-account-type: `:gl-account-type-*` keyword."
  [gl-account-type]
  (CashAccountProductProto$GlAccountType/forNumber
   (gl-account-type->int gl-account-type)))

(def ^{:doc "Map of GlAccountClass label to protobuf int value."}
     gl-account-class->int
  cash-account-products/GlAccountClass-label2val)

(defn gl-account-class->pb-enum
  "Convert a gl-account-class keyword to the protobuf enum value, for
  use in FDB index queries.

  Args:
  - gl-account-class: `:gl-account-class-*` keyword."
  [gl-account-class]
  (CashAccountProductProto$GlAccountClass/forNumber
   (gl-account-class->int gl-account-class)))

(def ^{:doc "Map of Required label to protobuf int value."} required->int
  cash-account-products/Required-label2val)

(defn required->pb-enum
  "Convert a required keyword to the protobuf enum value, for use in
  FDB index queries.

  Args:
  - required: `:required-*` keyword."
  [required]
  (CashAccountProductProto$Required/forNumber
   (required->int required)))

(def ^{:doc "Map of IsoCashAccountType label to protobuf int value."}
     iso-cash-account-type->int
  cash-account-products/IsoCashAccountType-label2val)

(defn iso-cash-account-type->pb-enum
  "Convert an iso-cash-account-type keyword to the protobuf enum
  value, for use in FDB index queries.

  Args:
  - iso-cash-account-type: `:iso-cash-account-type-*` keyword."
  [iso-cash-account-type]
  (CashAccountProductProto$IsoCashAccountType/forNumber
   (iso-cash-account-type->int iso-cash-account-type)))

(def ^{:doc "Map of SubLedgerKind label to protobuf int value."}
     sub-ledger-kind->int
  cash-account-products/SubLedgerKind-label2val)

(defn sub-ledger-kind->pb-enum
  "Convert a sub-ledger-kind keyword to the protobuf enum value, for
  use in FDB index queries.

  Args:
  - sub-ledger-kind: `:sub-ledger-kind-*` keyword."
  [sub-ledger-kind]
  (CashAccountProductProto$SubLedgerKind/forNumber
   (sub-ledger-kind->int sub-ledger-kind)))

(def ^{:doc "Map of BankType label to protobuf int value."} bank-type->int
  banks/BankType-label2val)

(defn bank-type->pb-enum
  "Convert a bank-type keyword to the protobuf enum value,
  for use in FDB index queries.

  Args:
  - bank-type: `:bank-type-*` keyword."
  [bank-type]
  (BankProto$BankType/forNumber
   (bank-type->int bank-type)))

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

(def ^{:doc "Parse Company protobuf bytes into a Clojure map."} pb->Company
  company/pb->Company)

(defn Company->pb
  "Serialise a Company map to protobuf bytes.

  Args:
  - m: Company map matching the generated schema."
  [m]
  (proto/->pb (company/new-Company m)))

(defn Company->java
  "Parse a Company map into the generated Java protobuf class.

  Args:
  - m: Company map matching the generated schema."
  [m]
  (CompanyProto$Company/parseFrom (Company->pb m)))

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

(def ^{:doc "Parse Bank protobuf bytes into a Clojure map."} pb->Bank
  banks/pb->Bank)

(defn Bank->pb
  "Serialise a Bank map to protobuf bytes.

  Args:
  - m: Bank map matching the generated schema."
  [m]
  (proto/->pb (banks/new-Bank m)))

(defn Bank->java
  "Parse a Bank map into the generated Java protobuf class.

  Args:
  - m: Bank map matching the generated schema."
  [m]
  (BankProto$Bank/parseFrom (Bank->pb m)))

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

(defn pb->CashAccount
  "Parse CashAccount protobuf bytes into a Clojure map. Strips
  optional string fields that deserialise as the proto2 empty-string
  default (`bban`, `gl-control-account-id`) — GL chart-of-accounts
  rows leave both unset, and downstream read sites use `(when (:bban
  account) ...)` semantics to distinguish customer instruments from
  GL rows."
  [input]
  (let [account (cash-accounts/pb->CashAccount input)]
    (cond-> account
            (= "" (:bban account))
            (dissoc :bban)

            (= "" (:gl-control-account-id account))
            (dissoc :gl-control-account-id))))

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

(defn pb->LedgerAccount
  "Parse LedgerAccount protobuf bytes into a Clojure map, dropping the
  proto2 default `:sub-ledger-kind-unknown` emitted for an unset
  optional `sub_ledger_kind` so callers see `:sub-ledger-kind` only on
  control accounts that carry a real cohort.

  Args:
  - input: protobuf bytes."
  [input]
  (let [account (ledger-accounts/pb->LedgerAccount input)]
    (cond-> account
            (= :sub-ledger-kind-unknown (:sub-ledger-kind account))
            (dissoc :sub-ledger-kind))))

(defn LedgerAccount->pb
  "Serialise a LedgerAccount map to protobuf bytes.

  Args:
  - m: LedgerAccount map matching the generated schema."
  [m]
  (proto/->pb (ledger-accounts/new-LedgerAccount m)))

(defn LedgerAccount->java
  "Parse a LedgerAccount map into the generated Java protobuf class.

  Args:
  - m: LedgerAccount map matching the generated schema."
  [m]
  (LedgerAccountProto$LedgerAccount/parseFrom (LedgerAccount->pb m)))

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

(def ^{:doc "Parse BankChangelog protobuf bytes into a Clojure map."}
     pb->BankChangelog
  banks/pb->BankChangelog)

(defn BankChangelog->pb
  "Serialise a BankChangelog map to protobuf bytes.

  Args:
  - m: BankChangelog map matching the generated schema."
  [m]
  (proto/->pb (banks/new-BankChangelog m)))

(defn BankChangelog->java
  "Parse a BankChangelog map into the generated Java protobuf class.

  Args:
  - m: BankChangelog map matching the generated schema."
  [m]
  (BankChangelogProto$BankChangelog/parseFrom
   (BankChangelog->pb m)))

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

(def ^{:doc "Parse User protobuf bytes into a Clojure map."} pb->User
  users/pb->User)

(defn User->pb
  "Serialise a User map to protobuf bytes.

  Args:
  - m: User map matching the generated schema."
  [m]
  (proto/->pb (users/new-User m)))

(defn User->java
  "Parse a User map into the generated Java protobuf class.

  Args:
  - m: User map matching the generated schema."
  [m]
  (UserProto$User/parseFrom (User->pb m)))

(def ^{:doc "Map of IdentityProvider label to protobuf int value."}
     identity-provider->int
  users/IdentityProvider-label2val)

(defn identity-provider->pb-enum
  "Convert an identity-provider keyword to the protobuf enum value,
  for use in FDB index queries.

  Args:
  - identity-provider: `:identity-provider-*` keyword."
  [identity-provider]
  (UserProto$IdentityProvider/forNumber
   (identity-provider->int identity-provider)))

(def ^{:doc "Map of UserStatus label to protobuf int value."} user-status->int
  users/UserStatus-label2val)

(defn user-status->pb-enum
  "Convert a user-status keyword to the protobuf enum value, for
  use in FDB index queries.

  Args:
  - user-status: `:user-status-*` keyword."
  [user-status]
  (UserProto$UserStatus/forNumber
   (user-status->int user-status)))

(def ^{:doc "Parse Membership protobuf bytes into a Clojure map."}
     pb->Membership
  memberships/pb->Membership)

(defn Membership->pb
  "Serialise a Membership map to protobuf bytes.

  Args:
  - m: Membership map matching the generated schema."
  [m]
  (proto/->pb (memberships/new-Membership m)))

(defn Membership->java
  "Parse a Membership map into the generated Java protobuf class.

  Args:
  - m: Membership map matching the generated schema."
  [m]
  (MembershipProto$Membership/parseFrom (Membership->pb m)))

(def ^{:doc "Map of Membership Role label to protobuf int value."} role->int
  memberships/Role-label2val)

(defn role->pb-enum
  "Convert a membership role keyword to the protobuf enum value,
  for use in FDB index queries.

  Args:
  - role: `:role-*` keyword."
  [role]
  (MembershipProto$Role/forNumber (role->int role)))
