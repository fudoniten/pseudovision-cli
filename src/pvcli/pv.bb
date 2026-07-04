(ns pvcli.pv
  "Pseudovision subcommands. Thin wrappers over /api/* routes in PV.

   Coverage (against live OpenAPI, 2026-07-04):
     info                            GET /api/version
     channels list [--channel X]     GET /api/channels
     channels get <id>               GET /api/channels/{id}
     filler-presets list             GET /api/filler-presets
     filler-presets get <id>         GET /api/filler-presets/{id}
     filler-presets create --body J  POST /api/filler-presets
     media libraries                 GET /api/media/libraries
     jobs list                       GET /api/jobs
     jobs get <job-id>               GET /api/jobs/{job-id}
     schedules list                  GET /api/schedules
     tags                            GET /api/tags
     catalog [--channel X] [--tag T] GET /api/catalog/aggregate

   Conventions:
   - Each command's handler returns a JSON-serialisable value.
   - The dispatch layer handles printing + error formatting.
   - Auth headers are set by pvcli.http based on cfg."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [pvcli.command :as command]
            [pvcli.http :as http]))

;; ============================================================
;; Help text
;; ============================================================

(def help
  (str/join "\n"
            ["pv — Pseudovision subcommands"
             ""
             "Usage:"
             "  pvcli pv <command> [options] [args]"
             ""
             "Commands:"
             "  info                            Service info (build, version)"
             "  channels list [--channel X]     List all channels"
             "  channels get <id-or-number>     Fetch one channel"
             "  filler-presets list             List all filler presets"
             "  filler-presets get <id>         Fetch one filler preset"
             "  filler-presets create --body J  Create a filler preset (POST)"
             "  media libraries                 List media libraries"
             "  jobs list                       List async jobs"
             "  jobs get <job-id>               Fetch one job by id"
             "  schedules list                  List schedules"
             "  tags                            List all tags"
             "  catalog [--channel X]           Aggregated catalog"
             ""
             "Run 'pvcli pv <command> --help' for command-specific options."
             ""]))

;; ============================================================
;; HTTP helpers
;; ============================================================

(defn- svc [cfg] (cfg :pv))

(defn- ok [resp] (:body resp))
;; Handlers return the raw response body. HTTP failures throw ex-info
;; (from pvcli.http) and propagate up to pvcli.main, which prints the
;; message to stderr and exits non-zero. We deliberately do NOT wrap the
;; result in an {:ok :body} envelope — the value a handler returns IS the
;; JSON the user sees, so `pvcli pv channels list | jq '.[]'` works.

;; ============================================================
;; Command implementations
;; ============================================================

(defn- info [{:keys [cfg]}]
  (ok (http/get (svc cfg) {:path "/api/version"})))

(defn- channels-list [{:keys [cfg channel]}]
  (ok (http/get (svc cfg)
                {:path "/api/channels"
                 :query (cond-> {} channel (assoc :channel channel))})))

(defn- channels-get [{:keys [cfg args]}]
  (let [id (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/channels/" id)}))))

(defn- filler-presets-list [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/filler-presets"})))

(defn- filler-presets-get [{:keys [cfg args]}]
  (let [id (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/filler-presets/" id)}))))

(defn- filler-presets-create [{:keys [cfg body]}]
  ;; --body is a JSON string; parse it to a map so pvcli.http sends it as
  ;; application/json (a raw string body would go out as octet-stream).
  (ok (http/post (svc cfg)
                 {:path "/api/filler-presets"
                  :body (when body (json/parse-string body true))})))

(defn- media-libraries [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/media/libraries"})))

(defn- jobs-list [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/jobs"})))

(defn- jobs-get [{:keys [cfg args]}]
  (let [id (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/jobs/" id)}))))

(defn- schedules-list [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/schedules"})))

(defn- tags [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/tags"})))

(defn- catalog [{:keys [cfg channel tag limit]}]
  (ok (http/get (svc cfg)
                {:path "/api/catalog/aggregate"
                 :query (cond-> {}
                          channel (assoc :channel channel)
                          tag     (assoc :tag tag)
                          limit   (assoc :limit limit))})))

;; ============================================================
;; Command tree
;; ============================================================
;; Each entry is either a leaf {:help :summary :spec :handler}
;; or a parent {:help :summary :sub {name entry ...}}.

(def commands
  {"info"
   {:help-summary "Service info (build, version)"
    :spec {}
    :handler info}

   "channels"
   {:help-summary "Channel commands"
    :sub
    {"list"
     {:help-summary "List all channels"
      :spec {:channel "Filter by channel name (e.g. goldenreels)"}
      :handler channels-list}

     "get"
     {:help-summary "Fetch one channel by id or number"
      :spec {}
      :handler channels-get}}}

   "filler-presets"
   {:help-summary "Filler preset commands"
    :sub
    {"list"
     {:help-summary "List all filler presets"
      :spec {}
      :handler filler-presets-list}

     "get"
     {:help-summary "Fetch one filler preset by id"
      :spec {}
      :handler filler-presets-get}

     "create"
     {:help-summary "Create a filler preset (POST body as JSON string)"
      :spec {:body "JSON body to POST as the new preset, e.g. {\"name\":\"kids\",\"grout_tags\":[\"kids\",\"daytime\"]}"}
      :handler filler-presets-create}}}

   "media"
   {:help-summary "Media commands"
    :sub
    {"libraries"
     {:help-summary "List media libraries"
      :spec {}
      :handler media-libraries}}}

   "jobs"
   {:help-summary "Async job commands"
    :sub
    {"list"
     {:help-summary "List jobs"
      :spec {}
      :handler jobs-list}

     "get"
     {:help-summary "Fetch one job by id"
      :spec {}
      :handler jobs-get}}}

   "schedules"
   {:help-summary "Schedule commands"
    :sub
    {"list"
     {:help-summary "List schedules"
      :spec {}
      :handler schedules-list}}}

   "tags"
   {:help-summary "List all tags"
    :spec {}
    :handler tags}

   "catalog"
   {:help-summary "Aggregated catalog (?channel=X&tag=Y)"
    :spec {:channel "Filter by channel name"
           :tag     "Filter by tag"
           :limit   {:coerce :long
                     :default 100
                     :desc "Max items to return"}}
    :handler catalog}})

;; ============================================================
;; Dispatch
;; ============================================================

(defn dispatch
  "Route a pv subcommand. `args` is the arg vector AFTER 'pv'.
   `cfg` is the resolved config. `mode` is :json or :human."
  [cfg mode args]
  (command/dispatch "pv" help commands cfg mode args))
