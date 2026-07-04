(ns pvcli.http
  "HTTP client wrapper. Adds auth, JSON encode/decode, and a uniform
   error shape to babashka.http-client.

   All three services in the Pseudovision ecosystem speak JSON with
   kebab-case keys. We don't reformat; whatever the service returns,
   we return."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn- auth-headers
  "Build auth headers for a service, if an API key is configured."
  [{:keys [api-key]}]
  (cond-> {}
    (and api-key (seq api-key))
    (assoc "X-Api-Key" api-key)))

(defn- safe-parse
  "Parse JSON. nil for empty bodies, raw string if not valid JSON."
  [body]
  (cond
    (nil? body)        nil
    (str/blank? body)  nil
    :else
    (try (json/parse-string body true)
         (catch Exception _ body))))

(defn- slurp-body
  "babashka.http-client returns :body as a string when :throw is false,
   and as an InputStream when :throw is true. Handle both."
  [body]
  (cond
    (nil? body)     nil
    (string? body)  body
    :else           (slurp body)))

(defn- error-message
  "Pull a human-readable error string out of a parsed body."
  [status parsed]
  (cond
    (map? parsed)     (or (:error parsed) (:message parsed) (str "HTTP " status))
    (string? parsed)  (if (str/blank? parsed) (str "HTTP " status) parsed)
    :else             (str "HTTP " status)))

(defn- throw-http-error
  "Throw ex-info with :status, :url, :method, :body, :raw-body so callers
   can switch on it."
  [{:keys [status body]} url method]
  (let [body-str (slurp-body body)
        parsed   (safe-parse body-str)
        msg      (error-message status parsed)]
    (throw (ex-info (str method " " url " → " status ": " msg)
                    {:status status
                     :url url
                     :method method
                     :body parsed
                     :raw-body body-str}))))

(defn- handle-response
  "Validate status, parse body, return {:status :body :raw}."
  [resp url method]
  (let [status (:status resp)]
    (if (<= 200 status 299)
      ;; safe-parse yields nil for empty bodies (e.g. 204 No Content).
      {:status status :body (safe-parse (slurp-body (:body resp))) :raw resp}
      (throw-http-error resp url method))))

(defn- request*
  "Common request builder. `body` is a map (JSON-encoded) or a string
   (sent as-is, as octet-stream). Returns a parsed response map, or throws
   ex-info on an HTTP error status."
  [method svc {:keys [path body headers query]}]
  (let [url (str (:url svc) path)
        has-body? (some? body)
        base-headers (merge {"Accept" "application/json"}
                            (auth-headers svc)
                            headers)
        body-str (cond
                   (nil? body)     nil
                   (string? body)  body
                   :else           (json/generate-string body))
        full-headers (cond-> base-headers
                       has-body? (assoc "Content-Type"
                                        (if (string? body)
                                          "application/octet-stream"
                                          "application/json")))
        opts (cond-> {:method method
                      :url url
                      :headers full-headers
                      :throw false}
               query    (assoc :query-params query)
               body-str (assoc :body body-str))]
    (handle-response (http/request opts) url method)))

(defn get    [svc opts] (request* "GET"    svc opts))
(defn post   [svc opts] (request* "POST"   svc opts))
(defn put    [svc opts] (request* "PUT"    svc opts))
(defn patch  [svc opts] (request* "PATCH"  svc opts))
(defn delete [svc opts] (request* "DELETE" svc opts))
