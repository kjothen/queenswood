#!/bin/sh
cd "$(dirname "$0")"

rm -f test_rsa*.pem

openssl genrsa -out test_rsa_privkey.pem 2048
openssl rsa -in test_rsa_privkey.pem -pubout -outform pkcs8 -out test_rsa_pubkey.pem

openssl genrsa -out test-2_rsa_privkey.pem 2048
openssl rsa -in test-2_rsa_privkey.pem -pubout -outform pkcs8 -out test-2_rsa_pubkey.pem
