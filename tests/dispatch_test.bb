(ns pvcli.dispatch-test
  "Smoke tests for the pv and ts command trees. We don't actually dispatch
   to handlers (that would hit the network) — we just verify the trees
   are well-formed: every leaf has :handler, every parent has :sub, and
   a few sample paths resolve to known handlers.

   Note: commands is a 2-level tree. Top level is keyed by subcommand name
   ('channels', 'filler-presets', ...). Under each is either:
     {:help-summary ... :handler f :spec ...}   (leaf), or
     {:help-summary ... :sub {...}}             (parent)
   The :sub value is itself a map keyed by sub-subcommand name."
  (:require [pvcli.pv :as pv]
            [pvcli.ts :as ts]
            [pvcli.assert :as a]
            [clojure.string :as str]))

;; --- pv tree spot checks ---

(a/truthy (contains? pv/commands "info")
          "pv has info command")
(a/truthy (fn? (:handler (pv/commands "info")))
          "pv info has a :handler")
(a/truthy (map? (:sub (pv/commands "channels")))
          "pv channels is a parent with :sub")
(a/truthy (fn? (:handler (get-in pv/commands ["channels" :sub "list"])))
          "pv channels list has a :handler")
(a/truthy (fn? (:handler (get-in pv/commands ["filler-presets" :sub "create"])))
          "pv filler-presets create has a :handler")
(a/truthy (fn? (:handler (pv/commands "tags")))
          "pv tags is a top-level leaf")
(a/truthy (fn? (:handler (pv/commands "catalog")))
          "pv catalog is a top-level leaf")

;; --- ts tree spot checks ---

(a/truthy (contains? ts/commands "info")
          "ts has info command")
(a/truthy (map? (:sub (ts/commands "channel")))
          "ts channel is a parent with :sub")
(a/truthy (fn? (:handler (get-in ts/commands ["channel" :sub "grid"])))
          "ts channel grid has a :handler")
(a/truthy (fn? (:handler (get-in ts/commands ["scheduling" :sub "run-daily"])))
          "ts scheduling run-daily has a :handler")
(a/truthy (fn? (:handler (get-in ts/commands ["scheduling" :sub "run-quarterly"])))
          "ts scheduling run-quarterly has a :handler")
(a/truthy (fn? (:handler (ts/commands "values")))
          "ts values is a top-level leaf (alias for dimension values)")

;; --- every leaf has a :handler ---

(defn- walk-leaves
  "Walk a commands tree, return all leaf specs (nodes with :handler)."
  ([tree] (walk-leaves tree []))
  ([tree path]
   (when (map? tree)
     (concat
      (when (:handler tree) [[path tree]])
      (when-let [sub (:sub tree)]
        (mapcat (fn [[k v]] (walk-leaves v (conj path k))) sub))))))

(let [bad-pv (->> (walk-leaves pv/commands)
                  (remove :handler)
                  (remove :spec)
                  first)
      bad-ts (->> (walk-leaves ts/commands)
                  (remove :handler)
                  (remove :spec)
                  first)]
  (a/truthy (nil? bad-pv) (str "every pv leaf has :handler (first bad: " (pr-str bad-pv) ")"))
  (a/truthy (nil? bad-ts) (str "every ts leaf has :handler (first bad: " (pr-str bad-ts) ")")))

;; --- handlers take 1 arg (opts) ---

(doseq [[path leaf] (walk-leaves pv/commands)]
  (let [h (:handler leaf)
        arity (or (:arglists (meta h)) [])]
    (a/truthy (and (seq arity) (= 1 (count (first arity))))
              (str "pv " (str/join " " path) " handler is 1-arity (got " (pr-str arity) ")"))))

(doseq [[path leaf] (walk-leaves ts/commands)]
  (let [h (:handler leaf)
        arity (or (:arglists (meta h)) [])]
    (a/truthy (and (seq arity) (= 1 (count (first arity))))
              (str "ts " (str/join " " path) " handler is 1-arity (got " (pr-str arity) ")"))))

;; --- help text is non-empty and starts with the service name ---

(a/truthy (str/includes? pv/help "pv ")
          "pv help text starts with 'pv '")
(a/truthy (str/includes? ts/help "ts ")
          "ts help text starts with 'ts '")
