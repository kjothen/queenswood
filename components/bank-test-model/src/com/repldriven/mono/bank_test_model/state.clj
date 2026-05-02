(ns com.repldriven.mono.bank-test-model.state
  "Initial model state and pure helpers over it. State is a flat map
  of accounts keyed by synthetic account-id (`:acct-0`, `:acct-1`,
  …), a `:next-id` counter the runner uses to allocate, the active
  policy set in model-shape, and a `:now` clock advanced by future
  time-travel commands.

  Why `:acct-<n>` rather than `:acct/<n>` — Clojure's reader rejects
  namespaced keywords whose name starts with a digit (`:acct/0` won't
  parse), and tests are easier to read with literal forms. The
  runner treats these as opaque side-table keys; the format doesn't
  leak.")

(def init-state
  "Empty bank, with the available-balance rule active. The `:policies`
  map mirrors the production policy set in model-shape — a hand-built
  re-implementation, not anything imported from `bank-policy`.

  Accounts live under their owning org (`:org` on each account;
  `:accounts` list on each org) and reference a product (`:product`)
  and a party (`:party`). Products carry a `:status` (`:draft` or
  `:published`); parties carry a `:type` (`:organization` for now;
  `:person` arrives in 6.2c-2 once an IDV bypass exists) and a
  `:status` (`:active` or `:pending`). Accounts can only be opened
  against published products and active parties that belong to the
  account's org."
  {:accounts {}
   :orgs {}
   :products {}
   :parties {}
   :payments {}
   :nis-by-org {}
   :policies {:available {:min 0 :improving? true}}
   :next-id 0
   :next-org-id 0
   :next-product-id 0
   :next-party-id 0
   :next-payment-id 0
   :next-ni-id 0
   ;; Each :accrue-interest / :capitalize-interest call consumes
   ;; the current value and increments — so every interest command
   ;; is on a fresh date and the brick's date-keyed idempotency
   ;; doesn't kick in. Format `YYYYMMDD`.
   :next-interest-date 20260501
   :now 0})

(defn next-id
  "The next synthetic account id, given current state. Pure — does
  not advance the counter."
  [state]
  (keyword (str "acct-" (:next-id state))))

(defn next-org-id
  "The next synthetic org id, given current state. Pure — does not
  advance the counter."
  [state]
  (keyword (str "org-" (:next-org-id state))))

(defn next-product-id
  "The next synthetic product id, given current state. Pure — does
  not advance the counter."
  [state]
  (keyword (str "prod-" (:next-product-id state))))

(defn next-party-id
  "The next synthetic party id, given current state. Pure — does
  not advance the counter."
  [state]
  (keyword (str "party-" (:next-party-id state))))

(defn next-payment-id
  "The next synthetic payment id, given current state. Pure — does
  not advance the counter."
  [state]
  (keyword (str "pmt-" (:next-payment-id state))))

(defn next-ni-id
  "The next synthetic national-identifier marker, given current
  state. Pure — does not advance the counter. The runner translates
  `:ni-N` into a real NI value of `\"ni-N\"`."
  [state]
  (keyword (str "ni-" (:next-ni-id state))))

(defn next-interest-date
  "The next `as-of-date` for an interest command, given current
  state. Pure — does not advance the counter. Returned as `YYYYMMDD`
  to match the wire format the brick expects."
  [state]
  (:next-interest-date state))

(defn known-accounts
  "Accounts the model knows about, as a vector of synthetic ids."
  [state]
  (vec (keys (:accounts state))))

(defn open-accounts
  "Accounts currently in `:open` status, as a vector. Used by
  `:close-account`'s args generator and by any command that should
  refuse to operate on a closed account."
  [state]
  (vec (for [[acct-id a] (:accounts state)
             :when (= :open (:status a))]
         acct-id)))

(defn known-orgs
  "Orgs the model knows about, as a vector of synthetic ids."
  [state]
  (vec (keys (:orgs state))))

(defn latest-version
  "Returns the highest-numbered version map of `prod-id`, or nil
  if the product has none. Versions are appended in order so the
  last entry is always the latest."
  [state prod-id]
  (peek (get-in state [:products prod-id :versions])))

(defn has-published-version?
  "True if `prod-id` has at least one `:published` version. The
  real bank's `published-version` helper picks the highest such
  version-number, so any product with a published version can
  back new accounts."
  [state prod-id]
  (boolean (some (fn [v] (= :published (:status v)))
                 (get-in state [:products prod-id :versions]))))

(defn add-account-options
  "All `[org-id party-id prod-id]` triples eligible for
  `:add-account`: party belongs to org, party is active, prod
  belongs to org, and prod has at least one published version.
  Used by `add-account`'s args generator."
  [state]
  (vec (for [[org-id org] (:orgs state)
             prod-id (:products org)
             party-id (:parties org)
             :when (and (has-published-version? state prod-id)
                        (= :active
                           (get-in state [:parties party-id :status])))]
         [org-id party-id prod-id])))

(defn drafts
  "Product ids whose latest version is currently `:draft`. Used by
  `:publish-product` and `:discard-draft` (both act on the latest
  draft)."
  [state]
  (vec (for [[prod-id _] (:products state)
             :when (= :draft (:status (latest-version state prod-id)))]
         prod-id)))

(defn open-draftable
  "Product ids whose latest version is NOT `:draft` — i.e., where
  `:open-draft` may run because there's no live draft to collide
  with. Includes products whose latest version is `:published` or
  `:discarded`."
  [state]
  (vec (for [[prod-id _] (:products state)
             :let [latest (latest-version state prod-id)]
             :when (and latest (not= :draft (:status latest)))]
         prod-id)))

(defn pending-parties
  "Party ids currently in `:pending` status. Used by
  `activate-party`'s args generator."
  [state]
  (vec (for [[party-id p] (:parties state)
             :when (= :pending (:status p))]
         party-id)))

(defn balance
  "Available balance for `acct`, or 0 if the account is unknown."
  [state acct]
  (get-in state [:accounts acct :available] 0))
