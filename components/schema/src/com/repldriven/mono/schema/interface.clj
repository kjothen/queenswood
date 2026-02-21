(ns com.repldriven.mono.schema.interface)

(def command "schemas/command/command.edn")
(def command-avro "schemas/command/command.avsc.json")
(def command-response-avro "schemas/command/command-response.avsc.json")

;; Test-only schemas — available on the classpath in test scope only
(def user-avro "schemas/user/user.avsc.json")
