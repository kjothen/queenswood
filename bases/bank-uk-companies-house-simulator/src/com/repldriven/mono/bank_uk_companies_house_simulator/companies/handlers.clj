(ns com.repldriven.mono.bank-uk-companies-house-simulator.companies.handlers)

(defn get-company
  [_config]
  (fn [request]
    (let [{:keys [state parameters]} request
          company-number (get-in parameters [:path :company_number])
          company (get-in @state [:companies company-number])]
      (if company
        {:status 200 :body company}
        {:status 404
         :body {:errors [{:type "ch:service"
                          :error "company-profile-not-found"}]}}))))
