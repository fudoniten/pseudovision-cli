(ns pvcli.test-helpers
  "Shared test utilities. Mock the HTTP client so tests don't hit the
   network."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]))

(defn with-fake-http
  "Run `body` with `babashka.http-client/request` rebound to `fake-fn`,
   which should accept the same args as `request` and return a fake
   response map (with :status and :body keys)."
  [fake-fn body]
  (with-redefs [http/request (fn [& args] (apply fake-fn args))]
    (body)))

(defn ok-response
  "Construct a fake 2xx response map for testing."
  [body]
  {:status 200
   :body (if (string? body) body (json/generate-string body))})

(defn err-response
  "Construct a fake 4xx/5xx response map for testing."
  [status body]
  {:status status
   :body (if (string? body) body (json/generate-string body))})
