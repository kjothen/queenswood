(ns ^:eftest/synchronized com.repldriven.mono.iam.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.migrator.interface

    [com.repldriven.mono.iam.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(def ^:private project-name "projects/prj-test")
(def ^:private sa-email "sa-test@prj-test.iam.repldriven.com")
(def ^:private sa-id
  "projects/prj-test/serviceAccounts/sa-test@prj-test.iam.repldriven.com")

(deftest create-service-account-test
  (testing "Creating a service account returns the account details"
    (with-test-system
     [sys "classpath:iam/application-test.yml"]
     (let [db (system/instance sys [:migrator :migrations])]
       (nom-test> [result
                   (SUT/create-service-account db
                                               project-name
                                               "sa-test"
                                               "Test Service Account"
                                               "A test service account") _
                   (is (= sa-id (:id result))) _
                   (is (= "sa-test" (:name result))) _
                   (is (= "prj-test" (:project-id result))) _
                   (is (= sa-email (:email result))) _
                   (is (= "Test Service Account" (:display-name result))) _
                   (is (= "A test service account" (:description result))) _
                   (is (= false (:disabled result)))])))))

(deftest get-service-account-test
  (testing "Getting a service account by name returns the account"
    (with-test-system [sys "classpath:iam/application-test.yml"]
                      (let [db (system/instance sys [:migrator :migrations])]
                        (nom-test> [_
                                    (SUT/create-service-account
                                     db
                                     project-name
                                     "sa-test"
                                     "Test Service Account"
                                     "A test service account") result
                                    (SUT/get-service-account db sa-id) _
                                    (is (= sa-id (:id result))) _
                                    (is (= sa-email (:email result)))])))))

(deftest get-service-account-by-unique-id-test
  (testing "Getting a service account by unique-id returns the account"
    (with-test-system
     [sys "classpath:iam/application-test.yml"]
     (let [db (system/instance sys [:migrator :migrations])]
       (nom-test> [created
                   (SUT/create-service-account db
                                               project-name
                                               "sa-test"
                                               "Test Service Account"
                                               "A test service account") result
                   (SUT/get-service-account
                    db
                    (str "projects/prj-test/serviceAccounts/"
                         (:unique-id created))) _ (is (= sa-id (:id result))) _
                   (is (= sa-email (:email result)))])))))

(deftest list-service-accounts-test
  (testing "Listing service accounts returns accounts for the project"
    (with-test-system
     [sys "classpath:iam/application-test.yml"]
     (let [db (system/instance sys [:migrator :migrations])]
       (nom-test>
        [_
         (SUT/create-service-account db project-name "sa-one" "Account One" "")
         _
         (SUT/create-service-account db project-name "sa-two" "Account Two" "")
         result (SUT/list-service-account db project-name) _
         (is (= 2 (count result)))])))))

(deftest patch-service-account-test
  (testing "Patching a service account updates display name and description"
    (with-test-system
     [sys "classpath:iam/application-test.yml"]
     (let [db (system/instance sys [:migrator :migrations])]
       (nom-test> [_
                   (SUT/create-service-account db
                                               project-name
                                               "sa-test"
                                               "Original Name"
                                               "Original description") _
                   (SUT/patch-service-account db
                                              sa-id
                                              "Updated Name"
                                              "Updated description") result
                   (SUT/get-service-account db sa-id) _
                   (is (= "Updated Name" (:display-name result))) _
                   (is (= "Updated description" (:description result)))])))))

(deftest disable-enable-service-account-test
  (testing "Disabling and enabling a service account updates the disabled flag"
    (with-test-system [sys "classpath:iam/application-test.yml"]
                      (let [db (system/instance sys [:migrator :migrations])]
                        (nom-test> [_
                                    (SUT/create-service-account
                                     db
                                     project-name
                                     "sa-test"
                                     "Test Service Account"
                                     "") _
                                    (SUT/disable-service-account db sa-id)
                                    disabled (SUT/get-service-account db sa-id)
                                    _ (is (= true (:disabled disabled))) _
                                    (SUT/enable-service-account db sa-id)
                                    enabled (SUT/get-service-account db sa-id) _
                                    (is (= false (:disabled enabled)))])))))

(deftest delete-service-account-test
  (testing "Deleting a service account makes it unavailable"
    (with-test-system [sys "classpath:iam/application-test.yml"]
                      (let [db (system/instance sys [:migrator :migrations])]
                        (nom-test>
                         [_
                          (SUT/create-service-account db
                                                      project-name
                                                      "sa-test"
                                                      "Test Service Account"
                                                      "") result
                          (SUT/delete-service-account db sa-id) _
                          (is (= sa-id (:id result))) _
                          (is (nil? (SUT/get-service-account db sa-id)))])))))

(deftest undelete-service-account-test
  (testing "Undeleting a deleted service account makes it available again"
    (with-test-system [sys "classpath:iam/application-test.yml"]
                      (let [db (system/instance sys [:migrator :migrations])]
                        (nom-test> [_
                                    (SUT/create-service-account
                                     db
                                     project-name
                                     "sa-test"
                                     "Test Service Account"
                                     "") _ (SUT/delete-service-account db sa-id)
                                    _ (SUT/undelete-service-account db sa-id)
                                    result (SUT/get-service-account db sa-id) _
                                    (is (= sa-id (:id result)))])))))
