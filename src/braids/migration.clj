(ns braids.migration
  (:require [clojure.string :as str]
            [braids.project-config :as pc]
            [braids.registry :as registry]))

(defn- parse-md-table
  "Parses a markdown table into a vector of maps. Expects header row, separator, then data rows."
  [lines]
  (when (>= (count lines) 2)
    (let [header (mapv (comp str/lower-case str/trim) (str/split (first lines) #"\|"))
          header (filterv (complement str/blank?) header)
          data-lines (drop 2 lines)] ;; skip header + separator
      (for [line data-lines
            :let [cells (filterv (complement str/blank?) (mapv str/trim (str/split line #"\|")))]
            :when (seq cells)]
        (zipmap (map keyword header) cells)))))

(defn parse-registry-md [md-str]
  (let [lines (str/split-lines md-str)
        table-start (some #(when (str/starts-with? (str/trim (second %)) "| Slug") (first %))
                          (map-indexed vector lines))
        table-lines (when table-start (drop table-start lines))
        table-lines (when table-lines
                      (take-while #(str/starts-with? (str/trim %) "|") table-lines))
        rows (parse-md-table table-lines)]
    {:projects (vec (for [row rows]
                      {:slug (:slug row)
                       :status (keyword (:status row))
                       :priority (keyword (:priority row))
                       :path (:path row)}))}))

(defn- extract-field [lines field-name]
  (some (fn [line]
          (let [trimmed (str/trim line)
                pattern (re-pattern (str "(?i)\\*\\*" field-name ":\\*\\*\\s*(.+)"))]
            (when-let [m (re-find pattern trimmed)]
              (str/trim (second m)))))
        lines))

(defn- extract-title [lines]
  (some (fn [line]
          (when-let [m (re-find #"^#\s+(.+)" (str/trim line))]
            (str/trim (second m))))
        lines))

(defn- parse-notifications-table [md-str]
  (let [lines (str/split-lines md-str)
        ;; Find the Notifications section
        notif-idx (some #(when (re-find #"(?i)^##\s+Notifications" (str/trim (second %)))
                           (first %))
                        (map-indexed vector lines))]
    (when notif-idx
      (let [remaining (drop (inc notif-idx) lines)
            table-lines (drop-while #(not (str/starts-with? (str/trim %) "|")) remaining)
            table-lines (take-while #(str/starts-with? (str/trim %) "|") table-lines)
            rows (parse-md-table table-lines)]
        (reduce (fn [acc row]
                  (let [event (keyword (:event row))
                        notify-str (str/trim (or (:notify row) ""))
                        is-on (str/starts-with? (str/lower-case notify-str) "on")
                        mention (second (re-find #"mention\s+([^\s\)]+)" notify-str))]
                    (-> acc
                        (assoc-in [:notifications event] is-on)
                        (cond-> mention (assoc-in [:mentions event] mention)))))
                {:notifications {} :mentions {}}
                rows)))))

(defn parse-project-md [md-str]
  (let [lines (str/split-lines md-str)
        title (extract-title lines)
        status (some-> (extract-field lines "Status") keyword)
        priority (some-> (extract-field lines "Priority") keyword)
        autonomy (some-> (extract-field lines "Autonomy") keyword)
        checkin (some-> (extract-field lines "Checkin") keyword)
        channel (extract-field lines "Channel")
        max-workers (some-> (extract-field lines "MaxWorkers") parse-long)
        worker-timeout (some-> (extract-field lines "WorkerTimeout") parse-long)
        notif-data (parse-notifications-table md-str)
        base (merge pc/defaults
                    {:name title
                     :status status
                     :priority priority
                     :autonomy autonomy}
                    (when checkin {:checkin checkin})
                    (when channel {:channel channel})
                    (when max-workers {:max-workers max-workers})
                    (when worker-timeout {:worker-timeout worker-timeout}))]
    (if notif-data
      (-> base
          (assoc :notifications (merge pc/default-notifications (:notifications notif-data)))
          (cond-> (seq (:mentions notif-data)) (assoc :notification-mentions (:mentions notif-data))))
      base)))

;; --- Migration planning (pure) ---

(defn plan-migration
  "Plans migration actions. Takes a map with:
   :state-home - path to braids state directory
   :read-file - fn [path] -> string or nil
   :file-exists? - fn [path] -> boolean
   Returns a vector of action maps."
  [{:keys [state-home read-file file-exists?]}]
  (let [registry-edn-path (str state-home "/registry.edn")
        registry-md-path (str state-home "/registry.md")
        actions (atom [])
        ;; Determine registry source
        registry (cond
                   ;; Already migrated
                   (file-exists? registry-edn-path)
                   (let [content (read-file registry-edn-path)]
                     (braids.registry/parse-registry content))

                   ;; Need to migrate
                   (file-exists? registry-md-path)
                   (let [md (read-file registry-md-path)
                         parsed (parse-registry-md md)
                         edn-str (braids.registry/registry->edn-string parsed)]
                     (swap! actions conj {:type :write-registry-edn
                                          :path registry-edn-path
                                          :content edn-str})
                     parsed)

                   :else {:projects []})]
    ;; Plan project.edn migrations
    (doseq [{:keys [slug path]} (:projects registry)]
      (let [edn-path (str path "/.braids/project.edn")
            md-path (str path "/.braids/PROJECT.md")
            legacy-edn-path (str path "/.project/project.edn")
            legacy-md-path (str path "/.project/PROJECT.md")
            root-md-path (str path "/PROJECT.md")]
        (when-not (or (file-exists? edn-path) (file-exists? legacy-edn-path))
          (let [md-source (cond
                            (file-exists? md-path) md-path
                            (file-exists? legacy-md-path) legacy-md-path
                            (file-exists? root-md-path) root-md-path
                            :else nil)]
            (when md-source
              (let [md (read-file md-source)
                    parsed (parse-project-md md)
                    edn-str (pc/project-config->edn-string parsed)]
                (swap! actions conj {:type :write-project-edn
                                     :path edn-path
                                     :slug slug
                                     :content edn-str})))))))
    @actions))

(defn format-migration-report
  "Formats a human-readable report of migration actions."
  [actions]
  (if (empty? actions)
    "Nothing to migrate — all files are already in EDN format."
    (str "Migration plan:\n"
         (str/join "\n"
           (for [{:keys [type path slug]} actions]
             (case type
               :write-registry-edn (str "  ✓ Write registry.edn → " path)
               :write-project-edn (str "  ✓ Write project.edn for " slug " → " path)
               (str "  ? Unknown action: " type))))
         "\n")))
