(ns braids.ready
  (:require [clojure.string :as str]))

(def priority-order {:high 0 :normal 1 :low 2})

(defn ready-beads
  "Pure function: given registry, project configs, ready beads per project,
   and worker counts per project, returns a flat list of ready beads
   annotated with :project slug. Ordered by project priority (high first)."
  [registry configs beads workers]
  (let [active-projects (->> (:projects registry)
                             (filter #(= :active (:status %)))
                             (filter (fn [{:keys [slug]}]
                                       (let [cfg (get configs slug)]
                                         (or (nil? cfg) (= :active (:status cfg))))))
                             (sort-by #(get priority-order (:priority %) 1)))]
    (->> active-projects
         (mapcat (fn [{:keys [slug]}]
                   (let [cfg (get configs slug)
                         max-w (or (:max-workers cfg) 1)
                         current-w (get workers slug 0)]
                     (when (< current-w max-w)
                       (map #(assoc % :project slug) (get beads slug []))))))
         vec)))

(defn format-ready-output
  "Format ready beads for human-readable CLI output."
  [beads]
  (if (empty? beads)
    "No ready beads."
    (str/join "\n"
      (map-indexed
        (fn [i {:keys [project id title priority]}]
          (let [p (if (number? priority) (str "P" priority) (or priority "?"))]
            (format "%d) [%s] %s: %s (%s)" (inc i) p id title project)))
        beads))))
