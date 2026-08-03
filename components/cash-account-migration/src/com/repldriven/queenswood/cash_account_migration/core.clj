(ns com.repldriven.queenswood.cash-account-migration.core
  (:require
    [com.repldriven.queenswood.cash-account-migration.domain :as domain]
    [com.repldriven.queenswood.cash-account-migration.store :as store]
    [com.repldriven.queenswood.cash-account-product-query.interface :as
     products]
    [com.repldriven.queenswood.cash-account-query.interface :as accounts]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- source-version
  "Any version of the source product, read for its product type. Every
  version of a product shares one, so which is immaterial — but a
  product with no versions at all is a source that cannot be checked."
  [txn bank-id product-id]
  (let [versions (products/get-versions txn bank-id product-id)]
    (cond
     (error/anomaly? versions)
     versions

     (empty? versions)
     (error/reject :cash-account-migration/source-product-not-found
                   {:message "Source product has no versions"
                    :bank-id bank-id
                    :product-id product-id})

     :else
     (first versions))))

(defn create-migration
  "Author a migration in draft. Reads the source and target inside one
  transaction so the product types they are checked against are the ones
  the migration is written from.

  Retried creates read back rather than authoring a second migration:
  the idempotency key is unique-indexed per bank, and a duplicate would
  otherwise mean two approvals against one set of accounts."
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [{:keys [bank-id source-product-id target-product-id
                   target-version-id idempotency-key]}
           data]
       (let-nom>
         [existing (if idempotency-key
                     (store/find-by-idempotency-key txn
                                                    bank-id
                                                    idempotency-key)
                     nil)]
         (if existing
           existing
           (let-nom>
             [source (source-version txn bank-id source-product-id)
              target (products/get-version txn
                                           bank-id
                                           target-product-id
                                           target-version-id)
              migration (domain/new-migration data source target)
              _ (store/save-migration txn migration)]
             migration)))))))

(defn get-migration
  [txn bank-id migration-id]
  (let-nom>
    [migration (store/load-migration txn bank-id migration-id)]
    (or migration
        (error/reject :cash-account-migration/not-found
                      {:message "Migration not found"
                       :bank-id bank-id
                       :migration-id migration-id}))))

(defn list-migrations
  ([txn bank-id]
   (list-migrations txn bank-id {}))
  ([txn bank-id opts]
   (store/list-migrations txn bank-id opts)))

(def ^:private chunk-size
  "Accounts whose verdicts commit together. A verdict is one small
  record, so this sits well inside FDB's transaction limits while
  spreading the per-transaction cost over a hundred accounts rather than
  paying it for each."
  100)

(defn- flush-verdicts
  "Writes a chunk of verdicts in one transaction and clears it."
  [config state]
  (let [{:keys [chunk]} state]
    (if (empty? chunk)
      state
      (let-nom>
        [_ (store/transact config
                           (fn [txn]
                             (reduce (fn [_ verdict]
                                       (store/save-account-run txn verdict))
                                     nil
                                     chunk)))]
        (assoc state :chunk [])))))

(defn- tally-verdict
  [tally decision]
  (let [outcome (:outcome decision)]
    (cond-> (update tally :seen inc)
            (= :cash-account-migration-outcome-ineligible outcome)
            (update :ineligible inc)

            (= :cash-account-migration-outcome-eligible outcome)
            (update :moved inc))))

(defn- evaluate-accounts
  "Streams the bank's accounts, decides about every one the migration
  reaches, and records the verdict. Returns the tally.

  A dry run and a commit differ only in what happens after the verdict —
  the streaming, the cohort test and the decision are the same code,
  because a preview that ran different logic would be evidence of
  nothing."
  [config migration target-version run]
  (let-nom>
    [state (accounts/reduce-accounts-with-balances
            config
            (:bank-id migration)
            (fn [state {:keys [account]}]
              (if-not (domain/in-cohort? migration account)
                state
                (let [decision (domain/verdict target-version account)
                      state (-> state
                                (update :chunk
                                        conj
                                        (domain/account-verdict run
                                                                account
                                                                decision))
                                (update :tally tally-verdict decision))]
                  (if (< (count (:chunk state)) chunk-size)
                    state
                    (flush-verdicts config state)))))
            {:chunk []
             :tally {:seen 0 :moved 0 :ineligible 0 :failed 0}})
     flushed (flush-verdicts config state)]
    (:tally flushed)))

(defn preview-migration
  "Evaluate a migration without moving anything. Writes a run and a
  verdict per account in the cohort, and returns the closed run.

  Previews may be re-run as often as wanted, including after approval:
  accounts open and close and balances move, so a preview is a forecast
  of what a commit would do rather than a promise, and being able to
  look again is what makes that drift visible."
  [txn bank-id migration-id business-day]
  (let-nom>
    [migration (get-migration txn bank-id migration-id)
     target (products/get-version txn
                                  bank-id
                                  (:target-product-id migration)
                                  (:target-version-id migration))
     run (domain/new-run migration business-day true)
     _ (store/save-run txn run)
     tally (evaluate-accounts txn migration target run)]
    (if (error/anomaly? tally)
      (let-nom> [_ (store/save-run txn (domain/fail-run run tally))] tally)
      (let-nom> [closed (domain/close-run run tally)
                 _ (store/save-run txn closed)]
        closed))))

(defn get-run
  [txn bank-id run-id]
  (let-nom>
    [run (store/load-run txn bank-id run-id)]
    (or run
        (error/reject :cash-account-migration/run-not-found
                      {:message "Migration run not found"
                       :bank-id bank-id
                       :run-id run-id}))))

(defn list-runs
  [txn bank-id migration-id]
  (store/list-runs-by-migration txn bank-id migration-id))

(defn list-run-accounts
  ([txn bank-id run-id]
   (list-run-accounts txn bank-id run-id {}))
  ([txn bank-id run-id opts]
   (store/list-account-runs txn bank-id run-id opts)))
