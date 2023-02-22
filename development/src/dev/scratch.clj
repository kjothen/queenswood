(ns com.repldriven.mono.dev.scratch)

(comment
  (require '[malli.core :as m])
  (def test-customer
    {:age 19
     :yearOfBirth 2003
     :fullName {:firstName "John" :lastName "Doe"}
     :contacts {:emails ["my email"]}
     :tin "1234567890"
     :citizenship "US"
     :governmentIDType "Other"
     :otherGovernmentIDType "Not Blank"})
  (def non-empty-string? (m/schema [:string {:min 1}]))
  (def Contacts [:map [:emails [:sequential {:min 1 :max 1} any?]]])
  (def FullName
    [:map [:firstName non-empty-string?] [:lastName non-empty-string?]
     [:middleName {:optional true} non-empty-string?]])
  (def Age [{:> 18} int?])
  (def YearOfBirth [{:min 1900 :max 2100} int?])
  (def Tin [:re #"^\d{10}$"])
  (def Citizenship [:enum "US" "GB" "RU" "CN"])
  (def GovernmentIDType
    [:and
     [:map [:governmentIDType {:optional false} non-empty-string?]
      [:otherGovernmentIDType {:optional true} non-empty-string?]]
     [:fn
      (fn [{:keys [governmentIDType otherGovernmentIDType]}]
        (or (and (not= "Other" governmentIDType) (nil? otherGovernmentIDType))
            (and (= "Other" governmentIDType)
                 (m/validate non-empty-string? otherGovernmentIDType))))]])
  (defn rule-1 [customer] (m/validate Contacts (:contacts customer)))
  (defn rule-2 [customer] (m/validate FullName (:fullName customer)))
  (defn rule-3 [customer] (m/validate Age (:age customer)))
  (defn rule-4 [customer] (m/validate YearOfBirth (:yearOfBirth customer)))
  (defn rule-5 [customer] (m/validate Tin (:tin customer)))
  (defn rule-6 [customer] (m/validate Citizenship (:citizenship customer)))
  (defn rule-8
    [customer countryToAllowedDocMap]
    (get countryToAllowedDocMap (:citizenship customer)))
  (defn rule-9
    [customer inputRegEx]
    (m/validate [:re inputRegEx] (:tin customer)))
  (defn rule-10
    [{:strs [A B]}]
    (if (and (= 1 A) (m/validate non-empty-string? B)) "PATH 1" "PATH 2"))
  (defn in? [coll x] (some #(= x %) coll))
  (defn rule-16
    [{:keys [geo operation] :as input} data]
    (filter (fn [provider]
              (and (in? (:availableGeographies provider) geo)
                   (in? (:operationsOffered provider) operation)))
            data))
  (rule-16 {:geo "UK" :operation "QES"}
           [{:name "MITEK"
             :availableGeographies ["UK" "DE" "FR"]
             :operationsOffered ["VIDEO_IDENT" "SELFIE" "QES"]}
            {:name "IDNOW"
             :availableGeographies ["UK" "DE"]
             :operationsOffered ["VIDEO_IDENT" "SELFIE"]}
            {:name "ONFIDO"
             :availableGeographies ["UK"]
             :operationsOffered ["SELFIE" "QES"]}])
  (defn rule-17
    [{:keys [score] :as input}]
    (cond (and (> score 0) (<= score 50)) "FAIL"
          (and (> score 50) (<= score 75)) "MANUAL_REVIEW"
          (> score 75) "PASS"
          :else "ERROR"))
  (rule-17 {:score 65}))
