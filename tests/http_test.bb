(ns pvcli.http-test
  (:require [babashka.http-client :as http]
            [pvcli.assert :as a]
            [pvcli.http :as ph]))

(defn- ok [body]
  {:status 200 :body (if (string? body) body (cheshire.core/generate-string body))})

(defn- err [status body]
  {:status status :body (if (string? body) body (cheshire.core/generate-string body))})

;; GET sends Accept and X-Api-Key
(let [captured (atom nil)]
  (with-redefs [http/request (fn [& args]
                               (reset! captured (first args))
                               (ok {:ok true}))]
    (let [resp (ph/get {:url "http://x" :api-key "secret-abc"} {:path "/foo"})]
      (a/truthy (= 200 (:status resp)) "GET returns 200")
      (a/truthy (= {:ok true} (:body resp)) "GET parses JSON body")
      (a/truthy (= "GET" (:method @captured)) "GET sends GET method")
      (a/truthy (= "http://x/foo" (:url @captured)) "GET builds full URL")
      (a/truthy (= "application/json" (get-in @captured [:headers "Accept"]))
           "GET sends Accept: application/json")
      (a/truthy (= "secret-abc" (get-in @captured [:headers "X-Api-Key"]))
           "GET sends X-Api-Key header"))))

;; POST JSON-encodes body
(let [captured (atom nil)]
  (with-redefs [http/request (fn [& args]
                               (reset! captured (first args))
                               (ok {:id 1}))]
    (let [resp (ph/post {:url "http://x"} {:path "/foo" :body {:a 1}})]
      (a/truthy (= 200 (:status resp)) "POST returns 200")
      (a/truthy (= "POST" (:method @captured)) "POST sends POST method")
      (a/truthy (= "{\"a\":1}" (:body @captured)) "POST serializes map body to JSON")
      (a/truthy (= "application/json" (get-in @captured [:headers "Content-Type"]))
           "POST sets Content-Type: application/json"))))

;; 404 throws with status
(with-redefs [http/request (fn [& _] (err 404 {:error "not found"}))]
  (try
    (ph/get {:url "http://x"} {:path "/missing"})
    (a/truthy false "expected ex-info to be thrown for 404")
    (catch clojure.lang.ExceptionInfo e
      (a/truthy (= 404 (:status (ex-data e))) "ex-data carries :status 404")
      (a/truthy (= "not found" (:error (:body (ex-data e))))
           "ex-data carries parsed error body")
      (a/truthy (re-find #"→ 404" (ex-message e))
                "ex-message mentions the status code (→ 404)"))))

;; 204 returns nil body
(with-redefs [http/request (fn [& _] {:status 204 :body ""})]
  (let [resp (ph/delete {:url "http://x"} {:path "/foo"})]
    (a/truthy (= 204 (:status resp)) "DELETE handles 204")
    (a/truthy (nil? (:body resp)) "DELETE returns nil body for 204")))
