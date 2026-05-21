(ns com.repldriven.mono.bank-ui.interface
  "Placeholder interface for the bank-ui brick.

  bank-ui is a JS-only Polylith component: its source lives under
  `src/` as Svelte + CSS and is consumed by sibling bricks via the
  `@queenswood/bank-ui` npm workspace package, not via Clojure
  imports. This namespace exists solely so `poly` recognises the
  brick and reports its npm dependencies in `poly libs`.")
