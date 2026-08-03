# Plan: migrating cash accounts between products

## Context

A cash-account product version applies to accounts opened while it is
in force. Nothing moves an existing account onto different terms.
[PRD: cash-account-products](../prd/cash-account-products.md) records
this as a gap, and records the reason it is not a small one — repricing
existing accounts carries consent and notice requirements.

The scheduler already carries a placeholder for the work. Its task
registry defines `:scheduler-task-kind-account-migration` as a no-op
returning `{:migrated 0}`, and the seeded default jobs run it on a
cadence in every bank. So the trigger exists and fires. What it does
not have is anything to do.

The first shape considered was a flag on the product version: a field
saying "adopt the accounts on my predecessor", read by a job that
inferred its work by finding accounts not on the version in force. It
was drafted and abandoned. Two things rule it out.

**A version has one date, and a migration needs three.** A version's
`effective-from` says when new accounts get the terms. Moving existing
customers onto terms less favourable than the ones they hold requires
telling them in advance, so the date they are notified and the date
they move are both distinct from it, and from each other. A boolean on
the target version can express none of that.

**It cannot say where accounts come from.** A flag on a version can
only ever mean "earlier versions of my own product". The case that
motivates the feature is not only evolving one product line but
splitting one — moving a cohort from one savings product to a different
savings product. Source and target have to be named independently.

## Decision

Model a migration as its own resource, with an explicit source and
target, its own lifecycle and dates, and its own per-account record of
what happened.

Three properties fall out, and each is load-bearing.

**A preview is the only thing the API can do.** Creating and previewing
a migration are ordinary API operations. Performing one is not — the
only thing that commits a migration is the scheduler task, whether it
fires on its date or is forced by hand. No request can move a hundred
thousand accounts. The scheduler already records
`SchedulerTriggerSource` as `scheduled` or `forced`, so a migration run
by hand is distinguishable from one that fired on its date without any
new machinery.

**The preview and the commit are one code path.** A pass that computes
per-account decisions, and a single flag deciding whether it writes the
repin. If the two diverge at all the preview stops being evidence, so
the selection, the eligibility evaluation and the per-account outcome
rows are identical in both, and only the write to the account is
conditional.

**Eligibility is discovered per account, at run time.** A migration is
not rejected up front because some of its accounts do not fit. Each
account is evaluated on its own and the ones that do not qualify are
recorded with a reason. This is what makes a preview worth reading: the
interesting output is not that 9,588 accounts would move, it is that
412 would not, and why.

## What compatibility means

One rule: the source and target must be the same `product_type`. A
savings product migrates to a savings product. Nothing else about the
target has to resemble the source.

Everything else that could differ is an eligibility question, not a
compatibility one, and belongs to the individual account rather than to
the migration. A GBP account being moved to a product that allows only
EUR does not make the migration invalid — it makes that account
ineligible, and every other account in the cohort still moves.

The distinction matters because it decides when a problem surfaces. A
compatibility rule is checked once, at creation, against two records.
An eligibility rule is checked per account, during the pass, against
live state that nothing has frozen.

## When a migration may run

A migration may only run while the target version is in force —
`effective-from` has passed and `effective-to` has not. This is the
same window `active-version` in `cash-account-product-query` uses to
decide what a newly opened account pins to, so an account that
migrates and an account that opens on the same day land on the same
terms.

Runnability is derived from that window every time the job looks, not
decided once and recorded. A migration whose target is not in force
today is not due today, which is a different thing from being dead: the
window it is measured against can move, and the migration moves with
it. Point a migration at something effective today, push that date out
a week, and the migration simply becomes due a week later. Nothing
about it needed changing.

So a closed window should not latch a terminal state. A migration that
can no longer run is one an operator cancels, because the system cannot
tell a target whose dates slipped from one nobody intends to use. What
the job can do is say which of the migrations it holds are not due and
why, so a migration waiting on a date is distinguishable from one
waiting on nothing.

How far this reaches depends on an open question below — whether a
migration targets a product or a specific version of one. A published
version's effective dates cannot be edited today, so a version-targeted
migration has a fixed window and only its own status can change. A
product-targeted one is measured against whichever version is active,
and that moves without the migration being touched at all.

## The records

Two, following the shape the interest pass already uses — a lifecycle
record for the migration, and one row per account.

**The migration** carries the bank, its own id, the source (a product,
optionally narrowed to particular versions), the target (a product and
version), the selection, its status, the date customers were notified,
the date it becomes due, and the counts of what it moved.

**The account rows** carry one entry per account the pass considered:
the account, the version it was on, the version it was moved to, the
outcome, and — for an account that did not move — the reason. The rows
are written by a preview as well as a commit, which is what makes a
preview inspectable per account rather than a summary. A row records
which run wrote it, so a preview's rows and a commit's rows do not
overwrite each other and can be compared.

Rows also make the pass resumable and chunked for the same reason they
do in interest: an account already moved is skipped on a re-run, and a
failure isolates to its chunk rather than ending the migration.

## Why a preview is a forecast, not a promise

Accounts close. New accounts open on the source product. Balances move,
and a balance-dependent eligibility rule moves with them. A preview run
on Monday and a commit run on Friday will not agree, and no amount of
care makes them.

The honest design accepts this rather than hiding it. A preview can be
re-run as often as wanted, right up to the moment of commit. Approval
attaches to the migration — to its source, target and selection — and
not to any particular preview's numbers. The commit writes its own rows,
so the difference between what was expected and what happened is
readable afterwards rather than assumed away.

## What the scheduler task becomes

The placeholder gains a real work list: migrations that are approved
and due, whose target version is in force. That is a query, not an
inference — the resource is the work item, and there is nothing to
derive from the state of the accounts themselves.

Each migration is one task within the run, so it inherits what a
scheduler run already records: per-task timings, status, and the
processed and failed counts that surface in the job history. A
migration that moves 12,000 accounts and fails 40 of them says so on
the job page without anything further being built.

## Where it sits in the API

A top-level `cash-account-migrations` collection, alongside
`cash-accounts` and `cash-account-products` rather than beneath either.

Not a sub-resource of a product, because a migration names two of them
and neither owns it. Hanging it off the target would assert that the
product receiving accounts owns the fact that another product is losing
them, which is false, and becomes more obviously false when several
products feed one. Hanging it off the source has the same problem
mirrored. It is also the bank-shaped reading: what an operator wants to
ask is which migrations are in flight, not which migrations a given
product has.

```
POST   /v1/cash-account-migrations                     create
GET    /v1/cash-account-migrations                     list
GET    /v1/cash-account-migrations/{id}                read
POST   /v1/cash-account-migrations/{id}/previews       run a preview
GET    /v1/cash-account-migrations/{id}/previews/{pid} what it would do
GET    /v1/cash-account-migrations/{id}/previews/{pid}/accounts
                                                       per-account, with reasons
POST   /v1/cash-account-migrations/{id}/approvals      authorise it
```

A preview is a sub-collection for the same reason a scheduler run is:
creating one under its parent and reading it back by id is already how
`/v1/jobs/{job-id}/runs` works. Approval is a sub-collection rather than
a status field so the authorisation has an identity of its own, which is
what makes it answerable later.

What is absent matters more than what is present. There is no
`POST /v1/cash-account-migrations/{id}/runs`. Committing is
`POST /v1/jobs/{job-id}/runs` against the migration job, so the rule
that only the scheduler moves accounts is visible in the shape of the
API rather than being a convention a reader has to be told. Forcing that
job runs every migration that is due rather than a chosen one — which is
consistent, since it is the same operation the schedule performs, but
worth knowing before someone expects to force just one.

The products API gains a read-side relationship rather than ownership: a
version can carry a count of the accounts on it, and can list the
migrations that target it, without any of them being its sub-resources.

## Whether this generalises, and where the seam is

Account migration is not the only bulk change of this shape, so it is
worth being deliberate about what gets built once and what gets built
for accounts.

Three adjacent cases already have a claim on it. Deriving balance-bucket
layouts from product type is named as a direction in the product
requirements, and moving existing accounts onto derived layouts is a
cohort operation with a per-account outcome. Backfilling a balance an
account should have but does not is the same operation again — the
accrual pass already logs the case where an account carries a non-zero
rate and no accrued balance to put it in, and nothing today can fix that
fleet-wide. Reissuing payment addresses, which `rotate-address` does one
account at a time, becomes this when a bank changes sort code or
clearing arrangement and every account it holds needs new ones.

Retiring a product is the pair to migration rather than another
instance: close a product to new business, then move whoever is left.

One thing that shares the word and none of the concern is the migrator,
which applies FDB record metadata at deploy time. Schema migration and
cohort migration are different problems, and the vocabulary should keep
them apart. A bank changing tier is different again — it rewrites one
record, and wants none of this.

The generalisation, then, is not "migration". Two separable things live
in this plan and only one of them is common.

**The bulk pass is already general.** Scanning a bank's accounts in
chunks, deciding per record, writing, recording a row per record,
reporting counts, and skipping what is already done on a re-run — that
exists twice for interest and is extracted. The scan takes the
per-account function off its context and knows nothing about interest,
so a migration is a third pass configuration beside accrual and
capitalisation rather than new machinery. This half generalised without
anybody deciding to generalise it, which is the good case.

**The approval envelope is not general, and should not be made so.**
Preview, notice, approval and scheduler-only commit exist because a
change is adverse and visible to a customer. Interest accrual wants none
of it. A balance-layout backfill wants the preview and no notice,
because nobody outside the bank can see the change. Reissuing addresses
wants the whole thing.

So: build account migration concretely, on the existing scan, and treat
the envelope as the thing to watch. If address reissue arrives wanting
the same preview-notice-approve-commit sequence, that is the moment to
lift it out — with two real instances to shape it rather than one and a
guess.

## Open questions

**How the cohort is selected.** A stored query (currency, balance band,
party segment) is more useful and re-evaluates at run time. An explicit
list resolved at creation is far easier to defend, because the set that
moved is the set that was approved rather than whatever the query
matched on the day. The two can be combined — resolve a query at
creation and freeze the result — at the cost of a cohort that goes
stale between approval and commit.

**Whether the target is a product or a version of one.** Naming a
version fixes exactly what accounts land on, and a published version's
effective dates cannot be edited, so the window the migration is
measured against is fixed too. Naming a product instead means the
migration follows whichever version is active — accounts always land on
current terms, and a later version published before the migration runs
changes what they get without the migration being touched. That is
either the useful behaviour or the dangerous one, depending on whether
the thing being approved is "these accounts move to these terms" or
"these accounts move onto whatever is current". It also decides how
much the runnable window can shift underneath an approved migration.

**Whether a migration is bank-scoped or platform-authored.** A bank
moving its own customers between its own products is an ordinary
bank-scoped resource. A platform-initiated migration is closer to
policy, and would need a different authorisation story.

**What notice actually requires.** This plan assumes a migration
carries a notified date and a due date, and that the gap between them
is the bank's concern rather than the platform's. Whether the platform
should enforce a minimum, refuse to run a migration whose notice is too
short, or merely record what it was told, is unsettled.

**Whether the abandoned flag returns as a convenience.** A "bring
existing accounts along" option when publishing a version could mint a
migration with a default notice period. That is sugar over the
resource, not an alternative to it, and it should not be built until
the resource exists.

## Reference

- [PRD: cash-account-products](../prd/cash-account-products.md) —
  records repricing existing accounts as a gap, with its consent and
  notice requirements
- [TDD: cash-account-products](../tdd/cash-account-products.md) —
  the version lifecycle and effective-dating this builds on
- [Plan: accrue interest as one batch pass](interest-batch-pass.md) —
  the chunked-pass and per-account-row shape reused here
