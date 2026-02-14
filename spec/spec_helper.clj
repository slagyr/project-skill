(ns spec-helper
  "Lightweight speclj-inspired test framework for Babashka."
  (:require [clojure.string :as str]))

(def ^:dynamic *contexts* [])
(def results (atom {:pass 0 :fail 0 :failures []}))

(defn- full-context [desc]
  (str/join " " (conj *contexts* desc)))

(defn pass! [desc]
  (swap! results update :pass inc)
  (println (str "  ✓ " (full-context desc))))

(defn fail! [desc & [detail]]
  (let [full (full-context desc)]
    (swap! results update :fail inc)
    (swap! results update :failures conj (cond-> full detail (str " — " detail)))
    (println (str "  ✗ " full (when detail (str " — " detail))))))

(defmacro describe [ctx & body]
  `(do
     (println (str "\n" ~ctx))
     (binding [*contexts* (conj *contexts* ~ctx)]
       ~@body)))

(defmacro context [ctx & body]
  `(do
     (println (str "  ▸ " ~ctx))
     (binding [*contexts* (conj *contexts* ~ctx)]
       ~@body)))

(defmacro it [desc & body]
  `(try
     (if (do ~@body)
       (pass! ~desc)
       (fail! ~desc))
     (catch Exception e#
       (fail! ~desc (str "Exception: " (.getMessage e#))))))

(defn should [v] (boolean v))
(defn should-not [v] (not v))
(defn should= [expected actual]
  (when (not= expected actual)
    (throw (ex-info (str "Expected " (pr-str expected) " but got " (pr-str actual)) {})))
  true)
(defn should-contain [needle haystack]
  (when-not (str/includes? (str haystack) (str needle))
    (throw (ex-info (str "Expected to find " (pr-str needle) " in text") {})))
  true)
(defn should-match [pattern text]
  (when-not (re-find (if (string? pattern) (re-pattern pattern) pattern) (str text))
    (throw (ex-info (str "Expected " (pr-str text) " to match " (pr-str pattern)) {})))
  true)

(defn summary []
  (let [{:keys [pass fail failures]} @results]
    (println (str "\n==============================="))
    (println (str "Results: " pass " passed, " fail " failed"))
    (when (seq failures)
      (println "\nFailed:")
      (doseq [f failures] (println (str "  - " f))))
    fail))

(defn run-and-exit []
  (let [fail-count (summary)]
    (System/exit (if (zero? fail-count) 0 1))))
