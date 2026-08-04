# Plan: name upstream unavailability, not the call that hit it

## Context

[ADR-0005](../adr/0005-error-handling-with-anomalies.md) now says an
anomaly category names the problem when someone outside the process can
act on it, and the call site when nobody can. `fdb/transact` was brought
under that rule: a contended write says `:fdb/contention` and a client
gets a 503 telling it to retry, rather than an opaque 500 naming
whichever operation happened to be in flight.

The external adapters have the same defect and it has not been fixed.
Every anomaly they raise names its call site:

- `:company/http` and `:company/parse` —
  `uk-companies-house-adapter/companies_house.clj`
- `:onfido/http` — `onfido-relay/outbound.clj`
- `:clearbank-adapter/unknown-event`,
  `:onfido-adapter/unknown-event`,
  `:uk-companies-house-adapter/process-command`

`:company/http` is the clearest case. It is raised for *any* upstream
status at or above 400 other than 404:

```clojure
(>= (:status res) 400)
(error/fail :company/http
            {:message "Companies House API error"
             :company-number company-number
             :status (:status res)
             :body (:body res)})
```

So the registry being down, the registry rate-limiting us, and our own
malformed request all arrive at a client as the same opaque 500. The
upstream status is already captured in the payload — the information
exists, it just cannot reach `type` or influence the status code,
because the kind does not carry it.

That is the same shape as the FDB case, and the remedy is the same.

## Decision

Raise a distinct, provider-neutral category when the failure is upstream
unavailability rather than a bad request:

```clojure
(cond
 (= 404 (:status res))     (error/reject :company/not-found ...)
 (= 429 (:status res))     (error/fail :company/rate-limited ...)
 (>= (:status res) 500)    (error/fail :company/unavailable ...)
 (>= (:status res) 400)    (error/fail :company/http ...))
```

`:company/unavailable` and `:company/rate-limited` map to 503 in `api`'s
existing `error-status-overrides`, which already carries `:fdb/contention`
and `:fdb/timeout` and is already documented on every operation — 503 is
on all 63, so **no OpenAPI change is needed**.

A connection failure or client-side timeout is the same answer as an
upstream 5xx and should reach `:<domain>/unavailable` too, rather than
falling through to the generic call-site category.

## What stays as it is

Not every upstream failure is actionable, and the rule only moves the
ones that are.

- **`:company/parse`** stays. A malformed provider response is a defect,
  not something a caller can retry into working.
- **`:company/http` stays for the remaining 4xx.** A 400, 401 or 403
  from the provider means our request or our credentials are wrong. The
  caller cannot act on that, so the stable call-site name is right and
  the payload carries the detail.
- **`:company/not-found` stays a rejection.** It already names the
  problem and already maps to 404.
- **The `unknown-event` and `process-command` categories stay.** They
  are internal dispatch failures with no caller-actionable distinction.

## Provider names in categories

`:onfido/http` names a vendor.
[ADR-0020](../adr/0020-providers-are-deployment-facts.md) requires
anomaly kinds to stay provider-neutral even when raised inside a
vendor's adapter, precisely because they surface as the API's RFC 9457
`type`.

The exposure is limited: `onfido-relay` drains an intent outbox in the
background and `api` does not require it, so this category is not
reaching a client today. It is still the wrong name, and it becomes a
live problem the moment a second identity provider consumes the same
channel — which is the arrangement ADR-0020 describes as the way
providers are meant to be swapped.

The domain component is `idv`, so `:idv/unavailable` is the neutral
form. Renaming is mechanical; the value is that it cannot later be
mistaken for a contract that mentions a vendor.

## What this needs

- `uk-companies-house-adapter/companies_house.clj`: split the `>= 400`
  branch, and route connection failures to `:company/unavailable`.
- `onfido-relay/outbound.clj`: same split, and rename `:onfido/http` to
  the `idv`-scoped neutral form.
- `clearbank-relay/outbound.clj`: check for the same shape — it was not
  raising a category of its own at the time of writing, so confirm
  where its HTTP failures surface before changing anything.
- `api/errors.clj`: add the new kinds to `error-status-overrides`.
- Tests: a unit test per branch of the status split, in the style of
  `fdb/transact_test.clj` — the split is a `cond`, so each arm wants
  pinning.
- No OpenAPI change: 503 is already declared on every operation.

## Decisions and risks

**429 may want its own status.** Mapping upstream rate-limiting to 503
tells a caller to back off, which is right, but 429 is the more precise
answer and `:policy/limit-exceeded` already maps to it. The argument for
503 is that the limit is *ours-to-them*, not *theirs-to-us*, so a client
retrying later is correct while a client slowing its own rate is not
necessarily. Worth settling before implementing rather than after.

**Distinguishing "unavailable" from "we are misconfigured" is a
judgement.** A 401 from a provider because a credential expired is, from
the client's point of view, indistinguishable from the provider being
down — both are "not working, not your fault". Keeping 401 as
call-site-named is the conservative choice, since the fix is
operational rather than a retry.

**This widens the actionable-error carve-out.** ADR-0005 describes it as
a closed set. Adding provider unavailability keeps it closed and small,
but a third case would be the point to ask whether the rule wants
restating more generally rather than enumerating.
