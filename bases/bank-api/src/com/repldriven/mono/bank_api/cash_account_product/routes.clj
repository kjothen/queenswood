(ns com.repldriven.mono.bank-api.cash-account-product.routes
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.handlers :as handlers]
    [com.repldriven.mono.bank-api.cash-account-product.links :as links]
    [com.repldriven.mono.bank-api.cash-account-product.queries :as queries]
    [com.repldriven.mono.bank-api.cash-account-product.examples :refer
     [ProductNotFound VersionNotFound DraftAlreadyExists VersionImmutable
      DuplicateItems]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]))

(def ^:private list-products-query-schema
  [:map {:closed true} [:page {:optional true} [:ref "PageQuery"]]])

(def ^:private location-header
  {:schema {:type "string"}
   :description "URI of the newly-created draft version"})

(def routes
  [["/cash-account-products"
    {:openapi {:tags ["Cash Account Products"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List products with their version histories inline"
            :openapi {:operationId "ListCashAccountProducts"
                      :parameters shared.parameters/ref-page}
            :parameters {:query list-products-query-schema}
            :responses {200 {:body [:ref "CashAccountProductList"]}}
            :handler queries/list-products}
      :post {:summary "Create a product (returns its initial draft version)"
             :openapi {:operationId "CreateCashAccountProduct"
                       :requestBody {:required true}}
             :parameters {:body [:ref "CashAccountProductRequest"]}
             :responses {201 {:body [:ref "CashAccountProductVersion"]
                              :openapi {:headers {"Location" location-header}
                                        :links links/from-draft}}
                         409 (ErrorResponse [#'DuplicateItems])}
             :handler handlers/create-product}}]
    ["/{product-id}" {:parameters {:path {:product-id [:ref "ProductId"]}}}
     [""
      {:get {:summary "Retrieve a product with its version history inline"
             :openapi {:operationId "RetrieveCashAccountProduct"}
             :responses {200 {:body [:ref "CashAccountProduct"]
                              :openapi {:links links/from-product}}
                         404 (ErrorResponse [#'ProductNotFound])}
             :handler queries/get-product}}]
     ["/versions"
      {:post {:summary "Open a new draft version (requires no existing draft)"
              :openapi {:operationId "OpenCashAccountProductDraft"
                        :requestBody {:required true}}
              :parameters {:body [:ref "CashAccountProductRequest"]}
              :responses {201 {:body [:ref "CashAccountProductVersion"]
                               :openapi {:headers {"Location" location-header}
                                         :links links/from-draft}}
                          404 (ErrorResponse [#'ProductNotFound])
                          409 (ErrorResponse [#'DraftAlreadyExists
                                              #'DuplicateItems])}
              :handler handlers/open-draft}}]
     ["/versions/{version-id}"
      {:parameters {:path {:version-id [:ref "VersionId"]}}}
      [""
       {:get {:summary "Retrieve a specific version"
              :openapi {:operationId "RetrieveCashAccountProductVersion"
                        :headers {"ETag" {:schema {:type "string"}}
                                  "Cache-Control" {:schema {:type "string"}}}}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'VersionNotFound])}
              :handler queries/get-version}
        :put {:summary "Update the draft version (draft state only)"
              :openapi {:operationId "UpdateCashAccountProductDraft"
                        :requestBody {:required true}}
              :parameters {:body [:ref "CashAccountProductRequest"]}
              :responses {200 {:body [:ref "CashAccountProductVersion"]
                               :openapi {:links links/from-draft}}
                          404 (ErrorResponse [#'VersionNotFound])
                          409 (ErrorResponse [#'VersionImmutable
                                              #'DuplicateItems])}
              :handler handlers/update-draft}
        :delete {:summary "Discard the draft version (draft state only)"
                 :openapi {:operationId "DiscardCashAccountProductDraft"}
                 :responses {204 {}
                             404 (ErrorResponse [#'VersionNotFound])
                             409 (ErrorResponse [#'VersionImmutable])}
                 :handler handlers/discard-draft}}]
      ["/publish"
       {:post {:summary "Publish the draft version (draft state only)"
               :openapi {:operationId "PublishCashAccountProductDraft"}
               :responses {200 {:body [:ref "CashAccountProductVersion"]
                                :openapi {:links links/from-published}}
                           404 (ErrorResponse [#'VersionNotFound])
                           409 (ErrorResponse [#'VersionImmutable])}
               :handler handlers/publish-draft}}]]]]])
