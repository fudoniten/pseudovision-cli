(ns pvcli.cli
  "Top-level CLI dispatch. Picks a service (pv / ts / grout) and forwards
   args to the matching module."
  (:require [clojure.string :as str]
            [pvcli.pv :as pv]
            [pvcli.ts :as ts]
            [pvcli.grout :as grout]))

(def version "0.1.0")

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
   "Global options (accepted anywhere on the command line):\n"
   "  -v, --verbose       Verbose logging to stderr\n"
   "      --config PATH   Path to config.edn\n"
   "  -H, --human         Human-readable output (default: JSON)\n"
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

(def ^:private global-boolean-flags
  "Global boolean flags (and their aliases) that may appear anywhere on the
   command line. `--help`/`--version` are intentionally excluded: they are
   context-sensitive (top-level vs service-level) and handled by the caller
   / service dispatchers."
  {"--human"   :human
   "-H"        :human
   "--verbose" :verbose
   "-v"        :verbose})

(defn split-global-opts
  "Separate global options from the service command line. Recognized global
   boolean flags (--human/-H, --verbose/-v) and `--config PATH` are pulled
   out wherever they appear; everything else — the service, its subcommand,
   and that command's own options and positionals — is returned untouched so
   the service dispatcher can parse it with the right spec.

   This is deliberately hand-rolled rather than delegated to babashka.cli:
   parsing the whole argv against a global spec would swallow service-level
   options (e.g. `--channel`) that are unknown at the top level.

   Returns [global-opts remaining-args]."
  [args]
  (loop [remaining (seq args)
         opts {}
         out []]
    (if (empty? remaining)
      [opts (vec out)]
      (let [a (first remaining)]
        (cond
          (contains? global-boolean-flags a)
          (recur (rest remaining) (assoc opts (global-boolean-flags a) true) out)

          (= a "--config")
          (recur (drop 2 remaining) (assoc opts :config (second remaining)) out)

          (str/starts-with? a "--config=")
          (recur (rest remaining) (assoc opts :config (subs a (count "--config="))) out)

          :else
          (recur (rest remaining) opts (conj out a)))))))

(defn dispatch
  "Pick a service from `args` (the argv with global options already removed)
   and call its dispatch fn. `cfg` is the resolved config; `mode` is
   :json or :human."
  [cfg mode args]
  (let [head (first args)]
    (cond
      (or (nil? head) (contains? #{"--help" "-h" "help"} head))
      (println top-help)

      (not (contains? services head))
      (binding [*out* *err*]
        (println (str "Unknown service: " head))
        (println "Run 'pvcli --help' for the list.")
        (System/exit 2))

      :else
      (let [{:keys [dispatch-fn]} (get services head)]
        (dispatch-fn cfg mode (rest args))))))
