(ns com.repldriven.queenswood.cash-account-migration.domain
  (:require
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.string :as str]))

(defn- ensure-named
  "A migration is read in a list beside others, so it needs a name an
  operator recognises. Rejected here rather than left to the record:
  protojure drops an empty string from the wire, and a proto2 required
  field missing from the bytes fails the Java parse — so a blank name
  would surface as a serialisation error rather than as the validation
  failure it is."
  [name]
  (when (str/blank? name)
    (error/reject :cash-account-migration/name-required
                  {:message "A migration needs a name"})))

(defn- ensure-target-published
  "Accounts may only be moved onto terms the bank has committed to. A
  draft is still being written and a discarded version was abandoned;
  neither is something to hold customers on."
  [target-version]
  (when-not (= :cash-account-product-status-published (:status target-version))
    (error/reject :cash-account-migration/target-not-published
                  {:message "A migration's target version must be published"
                   :version-id (:version-id target-version)
                   :status (:status target-version)})))

(defn- ensure-same-product-type
  "The one compatibility rule. A savings product migrates to a savings
  product. Everything else that could differ between the two — currency,
  rate, balance layout, payment schemes — is settled per account when
  the migration runs, because an account that does not fit should be
  reported and left rather than invalidating the whole cohort."
  [source-version target-version]
  (let [source-type (:product-type source-version)
        target-type (:product-type target-version)]
    (when-not (= source-type target-type)
      (error/reject :cash-account-migration/product-type-mismatch
                    {:message "Source and target must be the same product type"
                     :source-product-type source-type
                     :target-product-type target-type}))))

(defn- ensure-distinct-target
  "A migration onto the version accounts already hold moves nobody, and
  reads as a mistake rather than as a deliberate no-op."
  [source-version-ids target-version-id]
  (when (= [target-version-id] (vec source-version-ids))
    (error/reject :cash-account-migration/target-is-source
                  {:message "A migration's target must differ from its source"
                   :version-id target-version-id})))

(defn- ensure-notice-precedes-move
  "Telling customers after they have moved is not notice. Whether the
  gap between the two is long enough is not decided here — the platform
  records what it was told."
  [notified-on due-on]
  (when (and notified-on due-on (< due-on notified-on))
    (error/reject :cash-account-migration/notice-after-due
                  {:message "Customers must be notified before accounts move"
                   :notified-on notified-on
                   :due-on due-on})))

(defn in-cohort?
  "Whether a migration reaches this account at all. The scan sees every
  account of the source product; a migration that named versions reaches
  only accounts pinned to those.

  Separate from eligibility because it is not a verdict — an account
  outside the cohort was never part of this migration, and recording a
  reason for it would bury the accounts that were."
  [migration account]
  (let [narrowing (:source-version-ids migration)]
    (and (= (:source-product-id migration) (:product-id account))
         (or (empty? narrowing)
             (contains? (set narrowing) (:version-id account))))))

(defn verdict
  "What a run decides about one account in the cohort: `:outcome`, and
  for an ineligible account the `:ineligibility` saying why.

  Eligibility is settled here, per account, rather than up front against
  the migration — a cohort where some accounts do not fit is ordinary,
  and the ones that do still move. The reason is what makes a preview
  worth reading.

  Order is deliberate. An account already on the target is reported as
  such rather than as an oddity, because that is what a re-run sees for
  everything an earlier run moved — it is how the pass stays idempotent
  without tracking what it did."
  [target-version account]
  (let [{:keys [account-status currency version-id]} account
        allowed (set (:allowed-currencies target-version))]
    (cond
     (= (:version-id target-version) version-id)
     {:outcome :cash-account-migration-outcome-ineligible
      :ineligibility :cash-account-migration-ineligibility-already-on-target}

     (not= :cash-account-status-opened account-status)
     {:outcome :cash-account-migration-outcome-ineligible
      :ineligibility :cash-account-migration-ineligibility-account-not-open}

     (not (contains? allowed currency))
     {:outcome :cash-account-migration-outcome-ineligible
      :ineligibility
      :cash-account-migration-ineligibility-currency-not-allowed}

     :else
     {:outcome :cash-account-migration-outcome-eligible})))

(defn new-migration
  "A migration in draft. `source-version` is any version of the source
  product — it is read for the product type only, which every version of
  a product shares. Nothing here moves an account: a draft is a
  statement of intent that previews run against and an operator
  approves."
  [data source-version target-version]
  (let [{:keys [bank-id name source-product-id source-version-ids notified-on
                due-on idempotency-key]}
        data
        now (utility/now)]
    (let-nom>
      [_ (ensure-named name)
       _ (ensure-target-published target-version)
       _ (ensure-same-product-type source-version target-version)
       _ (ensure-distinct-target source-version-ids
                                 (:version-id target-version))
       _ (ensure-notice-precedes-move notified-on due-on)]
      (utility/assoc-some
       (utility/assoc-seq
        {:bank-id bank-id
         :migration-id (utility/generate-id "mig")
         :status :cash-account-migration-status-draft
         :name name
         :source-product-id source-product-id
         :target-product-id (:product-id target-version)
         :target-version-id (:version-id target-version)
         :created-at now
         :updated-at now}
        :source-version-ids
        source-version-ids)
       :notified-on notified-on
       :due-on due-on
       :idempotency-key idempotency-key))))

(defn new-run
  "A run opens as running, and is closed by whatever finishes it. A
  preview and a commit are the same record: `dry-run?` decides only
  whether accounts are written, never what is decided about them."
  [migration business-day dry-run?]
  {:bank-id (:bank-id migration)
   :run-id (str (utility/uuidv7))
   :migration-id (:migration-id migration)
   :status :cash-account-migration-run-status-running
   :dry-run dry-run?
   :business-day business-day
   :started-at (utility/now)})

(defn account-verdict
  "One account's row, as the run saw it. `to-version-id` is set only
  where an account actually moved — a dry run's eligible verdict leaves
  it off, because nothing moved it."
  [run account decision]
  (let [{:keys [outcome ineligibility]} decision]
    (utility/assoc-some
     {:bank-id (:bank-id run)
      :run-id (:run-id run)
      :account-id (:account-id account)
      :migration-id (:migration-id run)
      :outcome outcome
      :created-at (utility/now)}
     :from-version-id (:version-id account)
     :ineligibility ineligibility)))

(defn close-run
  "Closes a run with what it decided. Counts ride on the run because a
  reader wants them before any individual verdict, and totalling every
  row to find them is the expensive way."
  [run tally]
  (utility/assoc-some (assoc run
                             :status
                             :cash-account-migration-run-status-completed
                             :finished-at (utility/now))
                      :accounts-seen (:seen tally)
                      :accounts-moved (:moved tally)
                      :accounts-ineligible (:ineligible tally)
                      :accounts-failed (:failed tally)))

(defn fail-run
  "Closes a run that could not finish. Distinct from a run that finished
  having found every account ineligible — that one decided about them
  all and succeeded."
  [run anomaly]
  (assoc run
         :status :cash-account-migration-run-status-failed
         :finished-at (utility/now)
         :error (str (error/kind anomaly))))
