(ns pvcli.e2e-test
  "End-to-end tests that drive pvcli.main/run with a mocked HTTP client.
   These cover the full path argv → global-opt split → service dispatch →
   arg parsing → handler → output, which the tree-shape tests in
   dispatch_test.bb do not exercise. `run` returns an exit code instead of
   calling System/exit, so it is safe to call in-process."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [pvcli.assert :as a]
            [pvcli.main :as main]))

;; Happy path: output is the raw JSON body, NOT an {:ok :body} envelope.
(let [captured (atom nil)]
  (with-redefs [http/request (fn [req]
                               (reset! captured req)
                               {:status 200
                                :body (json/generate-string [{:id 1 :name "alpha"}])})]
    (let [out  (with-out-str (main/run ["pv" "channels" "list"]))
          data (json/parse-string (str/trim out) true)]
      (a/truthy (= [{:id 1 :name "alpha"}] data)
                "pv channels list emits the raw body (no :ok/:body envelope)")
      (a/truthy (= "GET" (:method @captured)) "issued a GET")
      (a/truthy (str/ends-with? (:url @captured) "/api/channels")
                "hit /api/channels"))))

;; Options after the subcommand reach the handler (regression: the top-level
;; parser used to swallow --channel, and handlers read it from the wrong key).
(let [captured (atom nil)]
  (with-redefs [http/request (fn [req] (reset! captured req) {:status 200 :body "[]"})]
    (main/run ["pv" "catalog" "--channel" "goldenreels"])
    (a/truthy (= "goldenreels" (get-in @captured [:query-params :channel]))
              "pv catalog --channel reaches the request query")))

;; Positional args reach the handler regardless of babashka.cli cmds/args split.
(let [captured (atom nil)]
  (with-redefs [http/request (fn [req] (reset! captured req) {:status 200 :body "{}"})]
    (main/run ["pv" "channels" "get" "goldenreels"])
    (a/truthy (str/ends-with? (:url @captured) "/api/channels/goldenreels")
              "pv channels get <id> puts the id in the path")))

;; A trailing --human switches output mode (global flags parsed anywhere).
(with-redefs [http/request (fn [_]
                             {:status 200
                              :body (json/generate-string [{:id 1 :name "alpha"}])})]
  (let [out (with-out-str (main/run ["pv" "channels" "list" "--human"]))]
    (a/truthy (re-find #"id\s+name" out) "--human trailing produces a table header")
    (a/truthy (not (str/starts-with? (str/trim out) "["))
              "--human output is not raw JSON")))

;; HTTP errors go to stderr and produce a non-zero exit code.
(with-redefs [http/request (fn [_] {:status 404 :body (json/generate-string {:error "nope"})})]
  (let [err  (java.io.StringWriter.)
        code (binding [*err* err] (main/run ["pv" "channels" "list"]))
        errs (str err)]
    (a/truthy (= 1 code) "HTTP error yields exit code 1")
    (a/truthy (str/includes? errs "Error:") "error message goes to stderr")
    (a/truthy (str/includes? errs "404") "error message includes the status code")))
