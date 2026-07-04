(ns pvcli.cli
  "Top-level CLI dispatch. Picks a service (pv / ts / grout) and forwards
   args to the matching module."
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [pvcli.config :as config]
            [pvcli.output :as output]
            [pvcli.pv :as pv]
            [pvcli.ts :as ts]
            [pvcli.grout :as grout]))

(def version "0.1.0")

(def global-spec
  "Spec for options that are valid at any level. Service-specific options
   live in the per-service modules."
  {:help    {:alias :h
             :coerce :boolean
             :desc "Show this help"}
   :version {:alias :V
             :coerce :boolean
             :desc "Show version"}
   :human   {:alias :H
             :coerce :boolean
             :desc "Human-readable output (default: JSON)"}
   :verbose {:alias :v
             :coerce :boolean
             :desc "Verbose logging to stderr"}
   :config  {:desc "Path to config.edn (overrides ~/.config/pvcli/config.edn)"}})

(def top-help
  (str
   "pvcli " version " — CLI for the Pseudovision media-automation ecosystem\n"
   "\n"
   "Usage:\n"
   "  pvcli [global-options] <service> <command> [options] [args]\n"
   "\n"
   "Services:\n"
   "  pv       Pseudovision (channels, media, catalog, scheduling, filler)\n"
   "  ts       Tunarr Scheduler (scheduling, channels, plans, strategies)\n"
   "  grout    Grout (filler media store: intake, tags, query, by-hash)\n"
   "\n"
   "Global options:\n"
   "  -v, --verbose       Verbose logging to stderr\n"
   "      --config PATH   Path to config.edn\n"
   "      --human         Human-readable output (default: JSON)\n"
   "  -h, --help          Show this help\n"
   "      --version       Show version\n"
   "\n"
   "Examples:\n"
   "  pvcli --version\n"
   "  pvcli grout media list --channel goldenreels\n"
   "  pvcli pv channels list --human\n"
   "\n"
   "Run 'pvcli <service> --help' for service-specific commands.\n"
   "\n"
   "Configuration:\n"
   "  Defaults: ~/.config/pvcli/config.edn\n"
   "  Overrides: PVCLI_PV_URL, PVCLI_PV_API_KEY, PVCLI_TS_URL, ...\n"))

(def services
  "Map of service name → {help, dispatch-fn}."
  {"pv"    {:help pv/help    :dispatch-fn pv/dispatch}
   "ts"    {:help ts/help    :dispatch-fn ts/dispatch}
   "grout" {:help grout/help :dispatch-fn grout/dispatch}})

(defn error-fn
  "Babashka.cli error callback. Prints the offending message and exits 2."
  [{:keys [msg]}]
  (binding [*out* *err*]
    (println "Error:" msg))
  (System/exit 2))

(defn global-exec
  "Top-level exec. Not normally called — we dispatch manually in
   `dispatch` to keep service selection explicit. This exists so babashka.cli
   can show --help cleanly."
  [_opts]
  (println top-help)
  (System/exit 0))

(defn dispatch
  "Pick a service from `args` and call its dispatch fn. Used by main."
  [cfg mode parsed]
  (let [args (:args parsed)
        opts (:opts parsed)
        head (first args)]
    (cond
      ;; No service given → show top-level help.
      (or (nil? head) (= "--help" head) (= "-h" head) (= "help" head))
      (do (println top-help) (System/exit 0))

      (not (contains? services head))
      (do (binding [*out* *err*]
            (println (str "Unknown service: " head))
            (println "Run 'pvcli --help' for the list."))
          (System/exit 2))

      :else
      (let [{:keys [dispatch-fn help]} (get services head)
            sub-args (rest args)
            sub-mode (if (:human opts) :human mode)]
        ;; If the user just asked for help on a service, print service help.
        (if (some #(= % "--help") sub-args)
          (do (println help) (System/exit 0))
          (dispatch-fn cfg sub-mode sub-args))))))
