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
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [pvcli.http :as http]
            [pvcli.output :as output]))

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

(defn- safe-call
  "Run a thunk that makes an HTTP call, return its body on success, or
   `{:error ...}` map on failure."
  [thunk]
  (try
    {:ok true :body (thunk)}
    (catch clojure.lang.ExceptionInfo e
      {:ok false
       :status (:status (ex-data e))
       :error (ex-message e)})))

;; ============================================================
;; Command implementations
;; ============================================================

(defn- info [{:keys [cfg]}]
  (safe-call
    #(ok (http/get (svc cfg) {:path "/api/version"}))))

(defn- channels-list [opts]
  (safe-call
    #(ok (http/get (svc (:cfg opts)) {:path "/api/scheduling/channels"}))))

(defn- channel-grid [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/grid")})))))

(defn- channel-grids [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/grids")})))))

(defn- channel-overrides [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/overrides")})))))

(defn- channel-overrides-history [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg)
                     {:path (str "/api/scheduling/channels/" slug "/overrides/history")})))))

(defn- channel-plan [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/plan")})))))

(defn- channel-preview [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/preview")})))))

(defn- channel-guidance [{:keys [cfg args]}]
  (let [slug (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/scheduling/channels/" slug "/guidance")})))))

(defn- run-scheduling [endpoint opts]
  (let [body (cond-> {}
               (:channel opts) (assoc :channel (:channel opts)))]
    (safe-call
      #(ok (http/post (svc (:cfg opts)) {:path endpoint :body body})))))

(defn- run-daily    [opts] (run-scheduling "/api/scheduling/daily"    opts))
(defn- run-weekly   [opts] (run-scheduling "/api/scheduling/weekly"   opts))
(defn- run-monthly  [opts] (run-scheduling "/api/scheduling/monthly"  opts))
(defn- run-quarterly [opts] (run-scheduling "/api/scheduling/quarterly" opts))

(defn- dimensions [{:keys [cfg name]}]
  (safe-call
    #(ok (http/get (svc cfg) {:path (cond-> "/api/dimensions"
                                          name (str "/" name))}))))

(defn- dimension-values [{:keys [cfg args]}]
  (let [name (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/dimensions/" name "/values")})))))

(defn- jobs-list [opts]
  (safe-call
    #(ok (http/get (svc (:cfg opts)) {:path "/api/jobs"}))))

(defn- jobs-get [{:keys [cfg args]}]
  (let [id (first args)]
    (safe-call
      #(ok (http/get (svc cfg) {:path (str "/api/jobs/" id)})))))

(defn- strategies-current [opts]
  (safe-call
    #(ok (http/get (svc (:cfg opts)) {:path "/api/strategies/current"}))))

(defn- media-libraries [opts]
  (safe-call
    #(ok (http/get (svc (:cfg opts)) {:path "/api/media/libraries"}))))

(defn- bumpers-list [opts]
  (safe-call
    #(ok (http/get (svc (:cfg opts)) {:path "/api/bumpers"}))))

(defn- media-recategorize [{:keys [cfg library-id]}]
  (safe-call
    #(ok (http/post (svc cfg) {:path (str "/api/media/" library-id "/recategorize")}))))

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

(defn- show-help []
  (println help)
  (System/exit 0))

(defn- find-leaf
  "Walk the command tree. Returns [leaf-spec path-taken] or nil.
   A 'leaf' is any node with a :handler. If the user types a subcommand
   prefix (e.g. 'ts channels') without going all the way to a leaf,
   we return nil and let the dispatcher show the parent help or error."
  [args]
  (loop [m {:sub commands}
         r args
         acc []]
    (cond
      (empty? r)
      (when (:handler m) [m acc])

      (and (:handler m) (not (contains? (:sub m) (first r))))
      ;; current node is a leaf and next arg isn't a subcommand — stop
      ;; walking. Caller derives positional args from `r` vs acc.
      [m acc]

      (contains? (:sub m) (first r))
      (recur (get-in m [:sub (first r)]) (rest r) (conj acc (first r)))

      :else
      nil)))
(defn- print-leaf-help [path leaf]
  (println (str "ts " (str/join " " path) " — " (:help-summary leaf)))
  (when (seq (:spec leaf))
    (println)
    (println "Options:")
    (println (cli/format-opts {:spec (:spec leaf)}))))

(defn dispatch
  "Route a ts subcommand. `args` is the arg vector AFTER 'ts'.
   `cfg` is the resolved service config. `mode` is :json or :human."
  [cfg mode args]
  (cond
    (or (empty? args)
        (and (= 1 (count args))
             (contains? #{"--help" "-h" "help"} (first args))))
    (show-help)

    (and (= 2 (count args))
         (contains? #{"--help" "-h" "help"} (second args)))
    (let [path-args (take 1 args)
          result (find-leaf path-args)]
      (if result
        (print-leaf-help path-args (first result))
        (do (binding [*out* *err*]
              (println (str "Unknown ts subcommand: " (first args))))
            (println help)
            (System/exit 2))))

    :else
    (let [result (find-leaf args)]
      (if-not result
        (do (binding [*out* *err*]
              (println (str "Unknown or incomplete ts command: "
                            (str/join " " args)))
              (println "Run 'pvcli ts --help' for the list."))
            (System/exit 2))
        (let [[leaf path-taken] result
              path-len (count path-taken)
              leaf-args (vec (drop path-len args))
              parsed (try
                       (cli/parse-args leaf-args
                                       {:exec-fn (fn [_]
                                                   (print-leaf-help path-taken leaf)
                                                   (System/exit 0))
                                        :spec (:spec leaf)
                                        :error-fn (fn [m]
                                                    (binding [*out* *err*]
                                                      (println "Error:" (:msg m)))
                                                    (System/exit 2))})
                       (catch clojure.lang.ExceptionInfo e
                         (binding [*out* *err*]
                           (println "Error:" (ex-message e)))
                         (System/exit 2)))]
          (output/emit-and-print!
            ((:handler leaf) (assoc parsed :cfg cfg))
            mode))))))
