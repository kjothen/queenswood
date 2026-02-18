(ns com.repldriven.mono.processor.specs.account-lifecycle)

(def specs
  {"open-account" [:map ["account-id" :string] ["name" :string]
                   ["currency" :string]]
   "close-account" [:map ["account-id" :string]]
   "reopen-account" [:map ["account-id" :string]]
   "suspend-account" [:map ["account-id" :string]]
   "unsuspend-account" [:map ["account-id" :string]]
   "archive-account" [:map ["account-id" :string]]})
