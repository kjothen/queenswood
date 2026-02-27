(ns com.repldriven.mono.processor.protocol)

(defprotocol Processor
  (process [this command]))
