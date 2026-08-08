(ns com.repldriven.queenswood.uk-companies-house-adapter.interface
  "Companies House egress. The lookup arrives as a command on the bus
  and the reply goes back on the response channel, so this adapter has
  no HTTP surface and nothing to call: the namespace exists so an
  aggregator base can register the adapter's system component-kinds by
  requiring one namespace, the same way it reaches every other
  composed base."
  (:require
    [com.repldriven.queenswood.uk-companies-house-adapter.system]))
