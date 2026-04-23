(ns com.repldriven.mono.bank-api.cash-account-product.routes
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.handlers :as handlers]
    [com.repldriven.mono.bank-api.cash-account-product.queries :as queries]
    [com.repldriven.mono.bank-api.cash-account-product.examples :refer
     [ProductNotFound VersionNotFound NoPublishedVersion NoDraft
      DuplicateItems]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/cash-account-products"
    {:openapi {:tags ["Cash Account Products"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve the latest version of products"
            :openapi {:operationId "RetrieveCashAccountProducts"}
            :responses {200 {:body [:ref "CashAccountProductVersionList"]}}
            :handler queries/list-products}
      :post {:summary "Draft a new product"
             :openapi {:operationId "CreateCashAccountProduct"
                       :requestBody {:required true}}
             :parameters {:body [:ref "DraftCashAccountProductRequest"]}
             :responses {201 {:body [:ref "CashAccountProductVersion"]}
                         409 (ErrorResponse [#'DuplicateItems])}
             :handler handlers/create-product}}]
    ["/{product-id}" {:parameters {:path {:product-id [:ref "ProductId"]}}}
     [""
      {:get {:summary "Retrieve the latest version of a product"
             :openapi {:operationId "RetrieveCashAccountProduct"}
             :responses {200 {:body [:ref "CashAccountProductVersion"]}
                         404 (ErrorResponse [#'ProductNotFound])}
             :handler queries/get-latest-version}}]
     ["/published"
      {:get {:summary "Retrieve the published version of a product"
             :openapi {:operationId "RetrievePublishedCashAccountProduct"}
             :responses {200 {:body [:ref "CashAccountProductVersion"]}
                         404 (ErrorResponse [#'ProductNotFound
                                             #'NoPublishedVersion])}
             :handler queries/get-published-version}}]
     ["/draft"
      {:post {:summary "Create or update the draft version of a product"
              :openapi {:operationId "UpsertCashAccountProductDraft"
                        :requestBody {:required true}}
              :parameters {:body [:ref "DraftCashAccountProductRequest"]}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'ProductNotFound])
                          409 (ErrorResponse [#'DuplicateItems])}
              :handler handlers/upsert-draft}}]
     ["/publish"
      {:post {:summary "Publish the latest product draft"
              :openapi {:operationId "PublishCashAccountProductDraft"}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'ProductNotFound])
                          409 (ErrorResponse [#'NoDraft])}
              :handler handlers/publish}}]
     ["/versions"
      [""
       {:get {:summary "Retrieve all versions of products"
              :openapi {:operationId "RetrieveCashAccountProductVersions"}
              :responses {200 {:body [:ref "CashAccountProductVersionList"]}
                          404 (ErrorResponse [#'ProductNotFound])}
              :handler queries/list-versions}}]
      ["/{version-id}" {:parameters {:path {:version-id [:ref "VersionId"]}}}
       {:get {:summary "Retrieve a product version"
              :openapi {:operationId "RetrieveCashAccountProductVersion"}
              :responses {200 {:body [:ref "CashAccountProductVersion"]}
                          404 (ErrorResponse [#'VersionNotFound])}
              :handler queries/get-version}}]]]]])
