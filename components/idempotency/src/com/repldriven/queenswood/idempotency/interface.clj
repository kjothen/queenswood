(ns com.repldriven.queenswood.idempotency.interface
  "FDB-backed idempotency cache for the bank API. Caches the response
  for any POST route that requires `Idempotency-Key`, scoped by the
  authenticated principal (bank-id for service-account JWTs,
  `\"admin\"` for the admin bearer). Replays cached 2xx/4xx for
  matching keys; skips 5xx so transient failures can be retried."
  (:require
    com.repldriven.queenswood.idempotency.system

    [com.repldriven.queenswood.idempotency.interceptors :as interceptors]))

(def cache-response
  "Reitit interceptor that protects an idempotent route. Plug into the
  shared `:interceptors` chain after auth and after
  `server/require-idempotency-key`."
  interceptors/cache-response)
