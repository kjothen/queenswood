(ns com.repldriven.mono.bank-idempotency.system)

;; The brick is currently store-and-interceptor only — no system
;; components to register. Stays here as the conventional `system.clj`
;; load point so `interface.clj` requires it for parity with other
;; bricks; future components (e.g. a sweeper for expired entries)
;; would be added here.
