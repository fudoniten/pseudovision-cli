(ns pvcli.assert
  "Tiny assertion helper. Each `truthy` records pass/fail in a shared counter.
   No deps, no framework. The runner at tests/run.bb prints the summary."
  (:require [clojure.string :as str]))

(def state (atom {:pass 0 :fail 0 :errors []}))

(defn- record-fail! [msg]
  (swap! state update :fail inc)
  (swap! state update :errors conj {:kind :fail :msg msg}))

(defn truthy
  "Assert that form is truthy. msg is recorded on failure."
  [form msg]
  (try
    (let [v (if (or (list? form) (symbol? form)) (eval form) form)]
      (if v
        (swap! state update :pass inc)
        (record-fail! (str "FAIL: " msg " — got " (pr-str v)))))
    (catch Exception e
      (record-fail! (str "EXCEPTION: " msg " — " (ex-message e))))))

(defn throws
  "Assert that evaluating form throws."
  [form msg]
  (try
    (let [v (if (or (list? form) (symbol? form)) (eval form) form)]
      (record-fail! (str "expected exception, got " (pr-str v) " — " msg)))
    (catch Exception _
      (swap! state update :pass inc))))

(defn summary [] @state)

(defn clear! []
  (clojure.core/reset! state {:pass 0 :fail 0 :errors []}))
