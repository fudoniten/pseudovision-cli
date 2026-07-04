(ns pvcli.config-test
  (:require [babashka.fs :as fs]
            [pvcli.assert :as a]
            [pvcli.config :as config]))

(a/truthy (string? (get-in (config/load {}) [:pv :url]))
          "pv url is resolved from built-in default")
(a/truthy (string? (get-in (config/load {}) [:ts :url]))
          "ts url is resolved from built-in default")
(a/truthy (string? (get-in (config/load {}) [:grout :url]))
          "grout url is resolved from built-in default")

;; Config file override
(let [tmp (str (fs/create-temp-file {:prefix "pvcli-cfg" :suffix ".edn"}))]
  (try
    (spit tmp "{:pv {:url \"https://pv.example.com\"}}")
    (with-redefs [config/find-config-file (constantly tmp)]
      (let [cfg (config/load {})]
        (a/truthy (= "https://pv.example.com" (get-in cfg [:pv :url]))
                  "config file overrides built-in default")
        (a/truthy (string? (get-in cfg [:ts :url]))
                  "ts still falls back to built-in default")
        (a/truthy (string? (get-in cfg [:grout :url]))
                  "grout still falls back to built-in default")))
    (finally (fs/delete tmp))))

;; Missing service throws
(let [tmp (str (fs/create-temp-file {:prefix "pvcli-cfg-empty" :suffix ".edn"}))]
  (try
    (spit tmp "{}")
    (with-redefs [config/find-config-file (constantly tmp)
                  config/built-in-defaults {}]
      (try
        (config/load {})
        (a/truthy false "expected ex-info to be thrown when no URL is resolvable")
        (catch clojure.lang.ExceptionInfo e
          (a/truthy (re-find #"No URL configured" (ex-message e))
                    "ex-message explains the missing URL"))))
    (finally (fs/delete tmp))))
