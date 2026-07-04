(ns pvcli.ts
  "Tunarr Scheduler subcommands. Thin wrappers over /api/* routes in TS.

   Coverage (v0.2 — added against live OpenAPI 2026-07-04):
     info                                    GET /api/version
     channels list                           GET /api/scheduling/channels
     channel grid <slug>                     GET /api/scheduling/channels/{channel}/grid
     channel grids <slug>                    GET /api/scheduling/channels/{channel}/grids
     channel overrides <slug>                GET /api/scheduling/channels/{channel}/overrides
     channel overrides-history <slug>        GET /api/scheduling/channels/{channel}/overrides/history
     channel plan <slug>                     GET /api/scheduling/channels/{channel}/plan
     channel preview <slug>                  GET /api/scheduling/channels/{channel}/preview
     channel guidance <slug>                 GET /api/scheduling/channels/{channel}/guidance
     scheduling run-daily [--channel X]      POST /api/scheduling/daily
     scheduling run-weekly [--channel X]     POST /api/scheduling/weekly
     scheduling run-monthly [--channel X]    POST /api/scheduling/monthly
     scheduling run-quarterly [--channel X]  POST /api/scheduling/quarterly
     dimensions [--name X]                   GET /api/dimensions
     dimensions <name> values                GET /api/dimensions/{dimension}/values
     jobs list                               GET /api/jobs
     jobs get <job-id>                       GET /api/jobs/{job-id}
     strategies current                      GET /api/strategies/current
     media libraries                         GET /api/media/libraries
     bumpers list                            GET /api/bumpers

   Channel identifier note (Pitfall 22 from the ecosystem skill):
   The scheduler uses the display name ('Golden Reels', 'Hua Network')
   as the storage key. The HTTP boundary translates slug→display name
   automatically (PR #96). So 'pcli ts channel grid goldenreels' works
   even though the storage row is keyed by 'Golden Reels'."
  (:require [clojure.string :as str]
            [pvcli.command :as command]
            [pvcli.http :as http]))

;; ============================================================
;; Help text
;; ============================================================

(def help
  (str/join "\n"
            ["ts — Tunarr Scheduler subcommands"
             ""
             "Usage:"
             "  pvcli ts <command> [options] [args]"
             ""
             "Commands:"
             "  info                                       Service info (build, version)"
             "  channels list                              List all scheduled channels"
             "  channel grid <slug>                        Frozen grid for a channel"
             "  channel grids <slug>                       All grids (history) for a channel"
             "  channel overrides <slug>                   Monthly/weekly overrides"
             "  channel overrides-history <slug>           Override change history"
             "  channel plan <slug>                        Live planning doc"
             "  channel preview <slug>                     Preview the next N days"
             "  channel guidance <slug>                    Tunabrain guidance doc"
             "  scheduling run-daily [--channel X]         Trigger daily scheduling"
             "  scheduling run-weekly [--channel X]        Trigger weekly scheduling"
             "  scheduling run-monthly [--channel X]       Trigger monthly overrides"
             "  scheduling run-quarterly [--channel X]     Trigger quarterly grid regen"
             "  dimensions [--name X]                      List dimensions (or one)"
             "  dimensions <name> values                   List values for a dimension"
             "  jobs list                                  List async jobs"
             "  jobs get <job-id>                          Fetch one job"
             "  strategies current                         Show current strategy"
             "  media libraries                            List media libraries"
             "  bumpers list                               List generated bumpers"
             ""
             "Channel identifier:"
             "  <slug> is the lowercase, no-spaces form of the channel name"
             "  (e.g. 'goldenreels' for 'Golden Reels', 'hua' for 'Hua Network')."
             ""
             "Run 'pvcli ts <command> --help' for command-specific options."
             ""]))

;; ============================================================
;; HTTP helpers
;; ============================================================

(defn- svc [cfg] (cfg :ts))

(defn- ok [resp] (:body resp))
;; Handlers return the raw response body. HTTP failures throw ex-info
;; (from pvcli.http) and propagate to pvcli.main, which prints to stderr
;; and exits non-zero. No {:ok :body} envelope — the returned value IS
;; the JSON the user sees.

;; ============================================================
;; Command implementations
;; ============================================================

(defn- info [{:keys [cfg]}]
  (ok (http/get (svc cfg) {:path "/api/version"})))

(defn- channels-list [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/scheduling/channels"})))

(defn- channel-grid [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/grid")}))))

(defn- channel-grids [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/grids")}))))

(defn- channel-overrides [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/overrides")}))))

(defn- channel-overrides-history [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg)
                  {:path (str "/api/scheduling/channels/" slug "/overrides/history")}))))

(defn- channel-plan [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/plan")}))))

(defn- channel-preview [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/preview")}))))

(defn- channel-guidance [{:keys [cfg args]}]
  (let [slug (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/guidance")}))))

(defn- run-scheduling [endpoint opts]
  (let [body (cond-> {}
               (:channel opts) (assoc :channel (:channel opts)))]
    (ok (http/post (svc (:cfg opts)) {:path endpoint :body body}))))

(defn- run-daily    [opts] (run-scheduling "/api/scheduling/daily"    opts))
(defn- run-weekly   [opts] (run-scheduling "/api/scheduling/weekly"   opts))
(defn- run-monthly  [opts] (run-scheduling "/api/scheduling/monthly"  opts))
(defn- run-quarterly [opts] (run-scheduling "/api/scheduling/quarterly" opts))

(defn- dimensions [{:keys [cfg name]}]
  (ok (http/get (svc cfg) {:path (cond-> "/api/dimensions"
                                   name (str "/" name))})))

(defn- dimension-values [{:keys [cfg args]}]
  (let [name (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/dimensions/" name "/values")}))))

(defn- jobs-list [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/jobs"})))

(defn- jobs-get [{:keys [cfg args]}]
  (let [id (first args)]
    (ok (http/get (svc cfg) {:path (str "/api/jobs/" id)}))))

(defn- strategies-current [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/strategies/current"})))

(defn- media-libraries [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/media/libraries"})))

(defn- bumpers-list [opts]
  (ok (http/get (svc (:cfg opts)) {:path "/api/bumpers"})))

(defn- media-recategorize [{:keys [cfg library-id]}]
  (ok (http/post (svc cfg) {:path (str "/api/media/" library-id "/recategorize")})))

;; ============================================================
;; Command tree
;; ============================================================

(def commands
  {"info"
   {:help-summary "Service info (build, version)"
    :spec {}
    :handler info}

   "channels"
   {:help-summary "Scheduled channel commands"
    :sub
    {"list"
     {:help-summary "List all scheduled channels"
      :spec {}
      :handler channels-list}}}

   "channel"
   {:help-summary "Per-channel commands (use channel slug like 'goldenreels')"
    :sub
    {"grid"
     {:help-summary "Frozen grid for a channel"
      :spec {}
      :handler channel-grid}

     "grids"
     {:help-summary "All grids (history) for a channel"
      :spec {}
      :handler channel-grids}

     "overrides"
     {:help-summary "Current monthly/weekly overrides"
      :spec {}
      :handler channel-overrides}

     "overrides-history"
     {:help-summary "Override change history"
      :spec {}
      :handler channel-overrides-history}

     "plan"
     {:help-summary "Live planning doc for a channel"
      :spec {}
      :handler channel-plan}

     "preview"
     {:help-summary "Preview next N days for a channel"
      :spec {}
      :handler channel-preview}

     "guidance"
     {:help-summary "Tunabrain guidance doc for a channel"
      :spec {}
      :handler channel-guidance}}}

   "scheduling"
   {:help-summary "Scheduling pipeline triggers (POST endpoints)"
    :sub
    {"run-daily"
     {:help-summary "Run daily scheduling (horizon extension)"
      :spec {:channel "Optional: limit to one channel (slug, e.g. goldenreels)"}
      :handler run-daily}

     "run-weekly"
     {:help-summary "Run weekly scheduling (full Tunabrain + PV pipeline)"
      :spec {:channel "Optional: limit to one channel (slug, e.g. goldenreels)"}
      :handler run-weekly}

     "run-monthly"
     {:help-summary "Run monthly overrides"
      :spec {:channel "Optional: limit to one channel (slug, e.g. goldenreels)"}
      :handler run-monthly}

     "run-quarterly"
     {:help-summary "Run quarterly grid regeneration"
      :spec {:channel "Optional: limit to one channel (slug, e.g. goldenreels)"}
      :handler run-quarterly}}}

   "dimensions"
   {:help-summary "List dimensions (or one by --name, or values for a dimension)"
    :spec {:name "If set, fetch just this dimension"}
    :handler dimensions}

   "jobs"
   {:help-summary "Async job commands"
    :sub
    {"list" {:help-summary "List jobs"  :spec {} :handler jobs-list}
     "get"  {:help-summary "Fetch one job" :spec {} :handler jobs-get}}}

   "strategies"
   {:help-summary "Strategy commands"
    :sub
    {"current" {:help-summary "Show current strategy"  :spec {} :handler strategies-current}}}

   "media"
   {:help-summary "Media commands (subset of /api/media/*)"
    :sub
    {"libraries"
     {:help-summary "List media libraries"
      :spec {}
      :handler media-libraries}
     "recategorize"
     {:help-summary "Trigger recategorization for a library (POST)"
      :spec {:library-id "Library id (UUID) to recategorize"
             :coerce :string}
      :handler media-recategorize}}}

   "bumpers"
   {:help-summary "Bumper commands"
    :sub
    {"list" {:help-summary "List generated bumpers" :spec {} :handler bumpers-list}}}

   "values"
   {:help-summary "List values for a dimension (alias for 'dimension <name> values')"
    :spec {}
    :handler dimension-values}})

;; ============================================================
;; Dispatch
;; ============================================================

(defn dispatch
  "Route a ts subcommand. `args` is the arg vector AFTER 'ts'.
   `cfg` is the resolved config. `mode` is :json or :human."
  [cfg mode args]
  (command/dispatch "ts" help commands cfg mode args))
