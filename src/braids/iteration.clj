(ns braids.iteration
  (:require [clojure.string :as str]
            [cheshire.core :as json]))

(defn parse-iteration-number [content]
  (when-let [m (re-find #"#\s*Iteration\s+(\S+)" content)]
    (second m)))

(defn parse-iteration-status [content]
  (when-let [m (re-find #"(?i)\*{0,2}Status:\*{0,2}\s*(\w+)" content)]
    (str/lower-case (second m))))

(defn parse-iteration-stories
  "Extract story ids and titles from ITERATION.md content.
   Stories are lines like: - bead-id: Title text"
  [content]
  (let [lines (str/split-lines content)
        in-stories (atom false)
        results (atom [])]
    (doseq [line lines]
      (cond
        (re-matches #"##\s+Stories\s*" line)
        (reset! in-stories true)

        (and @in-stories (re-matches #"##\s+.*" line))
        (reset! in-stories false)

        (and @in-stories (re-matches #"-\s+\S+:.*" line))
        (when-let [m (re-find #"-\s+(\S+):\s+(.*)" line)]
          (swap! results conj {:id (second m) :title (str/trim (nth m 2))}))))
    @results))

(defn annotate-stories
  "Annotate stories with status, priority, and deps from bead data.
   beads is a seq of maps with string keys."
  [stories beads]
  (let [bead-map (into {} (map (fn [b] [(get b "id") b]) beads))]
    (mapv (fn [{:keys [id title]}]
            (if-let [bead (get bead-map id)]
              {:id id
               :title title
               :status (get bead "status" "unknown")
               :priority (get bead "priority")
               :deps (mapv #(get % "depends_on_id")
                           (get bead "dependencies" []))}
              {:id id :title title :status "unknown" :priority nil :deps []}))
          stories)))

(defn completion-stats [stories]
  (let [total (count stories)
        closed (count (filter #(= "closed" (:status %)) stories))]
    {:total total
     :closed closed
     :percent (if (zero? total) 0 (int (* 100 (/ closed total))))}))

(defn- status-icon [status]
  (case status
    "closed" "✓"
    "in_progress" "▶"
    "blocked" "✗"
    "open" "○"
    "?"))

(defn format-iteration
  "Format iteration data for human-readable output."
  [{:keys [number status stories stats]}]
  (let [header (str "Iteration " number " [" status "] — "
                    (:closed stats) "/" (:total stats) " done (" (:percent stats) "%)")
        separator (apply str (repeat (count header) "─"))
        story-lines (map (fn [{:keys [id title status priority deps]}]
                           (let [icon (status-icon status)
                                 p-str (when priority (str " P" priority))
                                 dep-str (when (seq deps)
                                           (str " ← " (str/join ", " deps)))]
                             (str "  " icon " " id ": " title
                                  " [" status (or p-str "") "]"
                                  (or dep-str ""))))
                         stories)]
    (str/join "\n" (concat [header separator] story-lines))))

(defn format-iteration-json
  "Format iteration data as JSON."
  [{:keys [number status stories stats]}]
  (json/generate-string
    {:number number
     :status status
     :stories (mapv (fn [{:keys [id title status priority deps]}]
                      {:id id :title title :status status
                       :priority priority :deps deps})
                    stories)
     :stats stats}))
