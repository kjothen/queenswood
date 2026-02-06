(ns com.repldriven.mono.http-client.core
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [com.repldriven.mono.error.interface :as err]
            [org.httpkit.client :as client]))

(defn request
  "Make an HTTP request. Returns the response map or an anomaly.

  Handles two types of failures as anomalies:
  - Exceptions thrown during the request (:http-client/request-exception)
  - http-kit error responses with {:error ...} (:http-client/request-failed)"
  [opts]
  (err/try-nom :http-client/request-exception
               "HTTP request threw an exception"
               (let [res @(client/request opts)]
                 (if-let [error (:error res)]
                   (err/fail :http-client/request-failed
                             "HTTP request failed"
                             {:opts opts
                              :error error
                              :res res})
                   res))))

(defn request-async
  "Make an async HTTP request. Returns a promise that will contain either
  the response map or an anomaly when dereferenced.

  Handles three types of failures as anomalies:
  - Exceptions starting the request (:http-client/request-exception)
  - http-kit error responses with {:error ...} (:http-client/request-failed)
  - Exceptions in the callback (:http-client/callback-exception)"
  [opts]
  (let [p (promise)]
    (try
      (client/request opts
                      (fn [{:keys [error] :as res}]
                        (try
                          (deliver p
                                   (if error
                                     (err/fail :http-client/request-failed
                                               "HTTP request failed"
                                               {:opts opts
                                                :error error
                                                :res res})
                                     res))
                          (catch Exception e
                            (deliver p
                                     (err/fail :http-client/callback-exception
                                               "Exception in async request callback"
                                               {:opts opts
                                                :exception e
                                                :message (.getMessage e)}))))))
      (catch Exception e
        (deliver p
                 (err/fail :http-client/request-exception
                           "Exception starting async request"
                           {:opts opts
                            :exception e
                            :message (.getMessage e)}))))
    p))

(defn- body->string
  "Convert various body types to string."
  [body]
  (cond
    (nil? body) nil
    (string? body) body
    (instance? java.io.InputStream body) (slurp body)
    (bytes? body) (String. ^bytes body "UTF-8")
    :else (str body)))

(defn res->body
  "Extract and parse response body. Returns the body string or parsed JSON.

  Handles various body types (nil, String, InputStream, byte[]).
  If content-type contains 'json', parses the body as JSON.
  If the response is already an anomaly, passes it through.

  Options:
    :key-fn - Function to transform JSON keys (e.g., keyword for keyword keys)"
  ([res] (res->body res nil))
  ([res opts]
   (cond
     (err/anomaly? res) res

     (nil? res) nil

     :else
     (err/try-nom :http-client/body-parse-failed
                  "Failed to parse response body"
       (when-let [{:keys [body headers]} res]
         (when-let [body-str (body->string body)]
           (let [content-type (:content-type headers)]
             (if (and content-type (str/includes? content-type "json"))
               (json/read-str body-str opts)
               body-str))))))))

(defn res->edn
  "Extract and parse response body as EDN with keyword keys.

  Like res->body but parses JSON with keyword keys for more idiomatic Clojure usage.
  Convenience wrapper around (res->body res {:key-fn keyword})."
  [res]
  (res->body res {:key-fn keyword}))

(comment
  (res->body {:body "{\"a\":1,\"b\":2}"})
  (res->body {:headers {:content-type "application/json"}
              :body "{\"a\":1,\"b\":2}"})
  (res->body {:headers {:content-type "application/json"}
              :body (.getBytes "{\"a\":1,\"b\":2}")})
  (res->body {:headers {:content-type "text/html"}
              :body "<html>...</html>"}))