(ns pvcli.main
  "Top-level entry. Resolves config, then dispatches to the service handler.

   Exit codes:
     0  success
     1  runtime / HTTP error (the request reached a service and failed)
     2  usage / config error (bad service, bad command, unresolvable config)"
  (:require [pvcli.cli :as cli-impl]
            [pvcli.config :as config]))

(defn- print-err [& xs]
  (binding [*out* *err*]
    (apply println xs)))

(defn run
  "Do the work and return an integer exit code. Does not call System/exit
   itself, so it is testable; `-main` wraps it. Service dispatchers may still
   call System/exit on their own help/error paths — those are not on the
   success path exercised by tests."
  [args]
  (try
    (let [[gopts service-args] (cli-impl/split-global-opts args)
          mode (if (:human gopts) :human :json)
          head (first service-args)]
      (cond
        (contains? #{"--version" "-V"} head)
        (do (println (str "pvcli " cli-impl/version)) 0)

        (or (nil? head) (contains? #{"--help" "-h" "help"} head))
        (do (println cli-impl/top-help) 0)

        :else
        (let [cfg (config/load gopts)]
          (cli-impl/dispatch cfg mode service-args)
          0)))
    (catch clojure.lang.ExceptionInfo e
      (print-err "Error:" (ex-message e))
      ;; HTTP errors carry :status → exit 1; config/usage errors → exit 2.
      (if (:status (ex-data e)) 1 2))
    (catch Exception e
      (print-err "Unexpected error:" (or (ex-message e) (str e)))
      (when (System/getenv "PVCLI_DEBUG")
        (.printStackTrace e *err*))
      1)))

(defn -main [args]
  (System/exit (run args)))
