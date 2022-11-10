# mono
This monolithic repository represents a working prototype in clojure of a multi-purpose platform. This project uses the polylith framework (see documentation links below).



## Getting started
* See [Justfile](./Justfile) for a list of useful commands

---

<img src="logo.png" width="30%" alt="Polylith" id="logo">

The Polylith documentation can be found here:

- The [high-level documentation](https://polylith.gitbook.io/polylith)
- The [Polylith Tool documentation](https://github.com/polyfy/polylith)
- The [RealWorld example app documentation](https://github.com/furkan3ayraktar/clojure-polylith-realworld-example-app)

You can also get in touch with the Polylith Team via our [forum](https://polylith.freeflarum.com) or on [Slack](https://clojurians.slack.com/archives/C013B7MQHJQ).



openssl ecparam -name secp521r1 -genkey -param_enc explicit -out test_ecdsa_privkey.pem
openssl ec -in test_ecdsa_privkey.pem -pubout -outform pkcs8 -out test_ecdsa_pubkey.pem
