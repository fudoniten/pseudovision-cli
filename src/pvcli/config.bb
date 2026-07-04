(ns pvcli.config
  "Configuration loading. Resolves each service's {:url :api-key} from
   (in order): env var → config file → error.

   Resolution order, per service:
     1. PVCLI_<SVC>_URL  / PVCLI_<SVC>_API_KEY  (env)
     2. ~/.config/pvcli/config.edn  :<svc> {:url ... :api-key ...}
     3. (optional) Built-in defaults for the prod cluster

   We never throw away user config; if a key is set in BOTH env and file,
   the env wins. If neither is set, we return the default or error out
   depending on whether the service has a usable default."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def default-config-paths
  "Ordered list of candidate config paths. First one that exists wins.
   HOME-based paths are only included when HOME is set — in a sandbox (e.g.
   `nix flake check`) it may be unset, and (fs/path nil ...) would throw."
  (let [home (System/getenv "HOME")]
    (cond-> []
      home (conj (fs/path home ".config" "pvcli" "config.edn")
                 (fs/path home ".pvcli.edn"))
      :always (conj (fs/path ".pvcli.edn")))))

(def built-in-defaults
  "Fallback URL for each service. Used only when no config file is found
   AND no env override is set. These are the live prod endpoints as of
   2026-07-04. Override via env or config file."
  {:pv    "https://pseudovision.kube.sea.fudo.link"
   :ts    "https://tunarr-scheduler.kube.sea.fudo.link"
   :grout "http://grout.pseudovision.svc.cluster.local:8080"})

(defn- find-config-file
  "Return the first existing path from `default-config-paths`, or nil."
  []
  (some (fn [p] (when (fs/exists? p) p)) default-config-paths))

(defn- read-config
  "Read a config.edn file. Throws with a useful message on parse failure."
  [path]
  (try
    (edn/read-string (slurp path))
    (catch Exception e
      (throw (ex-info (str "Failed to parse config file: " path)
                      {:path path}
                      e)))))

(defn- env-override
  "Return {:url ... :api-key ...} populated from PVCLI_<SVC>_URL and
   PVCLI_<SVC>_API_KEY env vars, or nil if neither is set."
  [service]
  (let [prefix (str "PVCLI_" (str/upper-case (name service)) "_")
        url    (System/getenv (str prefix "URL"))
        key    (System/getenv (str prefix "API_KEY"))]
    (cond
      (and url key)      {:url url :api-key key}
      url                {:url url}
      key                {:api-key key}
      :else              nil)))

(defn- merge-cfg
  "Layer: built-in default < file < env. Later wins."
  [base file env]
  (cond-> base
    file (merge file)
    env  (merge env)))

(defn- resolve-service
  "Resolve a single service's config. Throws if no URL can be determined."
  [service file-config env-config]
  (let [built-in {:url (get built-in-defaults service)}
        merged   (merge-cfg built-in
                            (get file-config service)
                            env-config)]
    (when-not (:url merged)
      (throw (ex-info (str "No URL configured for service: " (name service)
                           ". Set PVCLI_" (str/upper-case (name service))
                           "_URL or add :url to :"
                           (name service) " in your config.edn.")
                      {:service service})))
    merged))

(defn load
  "Load and return a config map of the form:
     {:pv {:url ... :api-key ...}
      :ts {:url ... :api-key ...}
      :grout {:url ... :api-key ...}}

   Options:
     :config  explicit path to a config.edn file (overrides discovery)

   Each service's URL/api-key is resolved independently — you can have a
   config with only :pv, and the others fall back to built-in defaults."
  [{:keys [config]}]
  (let [config-path (or config (find-config-file))
        file-config  (when config-path (read-config config-path))]
    (into {}
          (for [service [:pv :ts :grout]]
            [service (resolve-service service file-config (env-override service))]))))
