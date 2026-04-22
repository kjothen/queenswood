(ns com.repldriven.mono.bank-api.cash-account-product.routes
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.handlers :as handlers]
    [com.repldriven.mono.bank-api.cash-account-product.queries :as queries]
    [com.repldriven.mono.bank-api.cash-account-product.examples :refer
     [ProductNotFound VersionNotFound NoPublishedVersion NoDraft]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/cash-account-products"
    {:openapi {:tags ["Cash Account Products"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List products (latest version per product)"
            :openapi {:operationId "RetrieveCashAccountProducts"}
            :responses {200 {:body [:ref "CashAccountProductVersionList"]}}
            :handler queries/list-products}
      :post {:summary "Draft a new product"
             :openapi {:operationId "CreateCashAccountProduct"
                       :requestBody {:required true}}
             :parameters {:body [:ref "DraftCashAccountProductRequest"]}
             :responses {201 {:body [:ref "CashAccountProductVersion"]}}
             :handler handlers/create-product}}]
    ["/{product-id}" {:parameters {:path {:product-id [:ref "ProductId"]}}}
     [""
      {:get {:summary "Retrieve latest product version"
             :openapi {:operationId "RetrieveCashAccountProduct"}
             :responses {200 {:body [:ref "CashAccountProductVersion"]}
                         404 (ErrorResponse [#'ProductNotFound])}
             :handler queries/get-latest-version}}]
     ["/published"
      {:get {:summary "Retrieve the current published product version"
             :openapi {:operationId "RetrievePublishedCashAccountProduct"}
             :responses {200 {:body [:ref "CashAccountProductVersion"]}
                         404 (ErrorResponse [#'ProductNotFound
                                             #'NoPublishedVersion])}
             :handler queries/get-published-version}}]
     ["/draft"
      {:post {:summary "Create or update the current draft"
              :openapi {:operationId "UpsertCashAccountProductDraft"
                        :requestBody {:required true}}
              :parameters {:body [:ref "DraftCashAccountProductRequest"]}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'ProductNotFound])}
              :handler handlers/upsert-draft}}]
     ["/publish"
      {:post {:summary "Publish the current draft"
              :openapi {:operationId "PublishCashAccountProductDraft"}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'ProductNotFound])
                          409 (ErrorResponse [#'NoDraft])}
              :handler handlers/publish}}]
     ["/versions"
      [""
       {:get {:summary "List all versions of a product"
              :openapi {:operationId "RetrieveCashAccountProductVersions"}
              :responses {200 {:body [:ref "CashAccountProductVersionList"]}
                          404 (ErrorResponse [#'ProductNotFound])}
              :handler queries/list-versions}}]
      ["/{version-id}" {:parameters {:path {:version-id [:ref "VersionId"]}}}
       {:get {:summary "Retrieve a specific product version"
              :openapi {:operationId "RetrieveCashAccountProductVersion"}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'VersionNotFound])}
              :handler queries/get-version}}]]]]])
