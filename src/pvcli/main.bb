(ns pvcli.main
  "Top-level entry. Resolves config, then dispatches to the service handler."
  (:require [babashka.cli :as cli]
            [pvcli.cli :as cli-impl]
            [pvcli.config :as config]
            [pvcli.output :as output]
            [clojure.string :as str]))

(defn- print-err [& xs]
  (binding [*out* *err*]
    (apply println xs)))

(defn- exit [code]
  (System/exit code))

(defn -main [args]
  (try
    (let [{:keys [opts] :as parsed} (cli/parse-args args {:spec cli-impl/global-spec
                                                          :error-fn cli-impl/error-fn})
          mode (if (:human opts) :human :json)
          cfg  (try
                 (config/load opts)
                 (catch clojure.lang.ExceptionInfo e
                   (print-err "Error:" (ex-message e))
                   (exit 2)))]
      (cond
        (:version opts)
        (do (println (str "pvcli " cli-impl/version))
            (exit 0))

        ;; --help at the top level (no service) shows top-level help.
        ;; --help with a service is passed to the service dispatcher,
        ;; which shows the service-specific help.
        (and (:help opts) (empty? (:args parsed)))
        (println cli-impl/top-help)

        (empty? (:args parsed))
        (println cli-impl/top-help)

        :else
        (cli-impl/dispatch cfg mode parsed)))
    (catch clojure.lang.ExceptionInfo e
      (let [status (:status (ex-data e))]
        (print-err "Error:" (ex-message e))
        (exit (if (and status (integer? status)) (if (< status 256) status 1) 1))))
    (catch Exception e
      (print-err "Unexpected error:" (or (ex-message e) (str e)))
      (.printStackTrace e *err*)
      (exit 1))))
