(ns pvcli.assert
  "Tiny assertion helper. Each assertion records pass/fail in a shared
   counter; the runner at tests/run.bb owns the lifecycle (clear!, summary,
   exit). No deps, no framework.

   `truthy`/`throws` are macros so the asserted form is evaluated lazily
   inside the assertion (a plain function would evaluate it at the call
   site, which makes `throws` impossible and mis-handles collection-valued
   results)."
  (:require [clojure.string :as str]))

(def state (atom {:pass 0 :fail 0 :errors []}))

(defn record-pass! []
  (swap! state update :pass inc))

(defn record-fail! [msg]
  (swap! state update :fail inc)
  (swap! state update :errors conj {:kind :fail :msg msg}))

(defmacro truthy
  "Assert that `form` evaluates to a truthy value. `msg` is recorded on
   failure (or when evaluating `form` throws)."
  [form msg]
  `(try
     (let [v# ~form]
       (if v#
         (record-pass!)
         (record-fail! (str "FAIL: " ~msg " — got " (pr-str v#)))))
     (catch Exception e#
       (record-fail! (str "EXCEPTION: " ~msg " — " (ex-message e#))))))

(defmacro throws
  "Assert that evaluating `form` throws."
  [form msg]
  `(try
     (let [v# ~form]
       (record-fail! (str "expected exception, got " (pr-str v#) " — " ~msg)))
     (catch Exception _#
       (record-pass!))))

(defn summary [] @state)

(defn clear! []
  (reset! state {:pass 0 :fail 0 :errors []}))
