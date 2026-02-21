(ns braids.list-io
  (:require [braids.ready-io :as rio]
            [braids.status-io :as sio]
            [braids.list :as list]))

(defn load-and-list
  "Load registry with iteration/worker data and format project list."
  [{:keys [json? session-labels]
    :or {session-labels []}}]
  (let [state-home (rio/resolve-state-home)
        reg (rio/load-registry state-home)
        all-projects (:projects reg)
        configs (into {} (map (fn [{:keys [slug path]}]
                                [slug (rio/load-project-config path)])
                              all-projects))
        iterations (into {} (keep (fn [{:keys [slug path]}]
                                    (when-let [data (sio/load-iteration-data path)]
                                      [slug data]))
                                  (filter #(= :active (:status %)) all-projects)))
        workers (rio/count-workers session-labels)
        ;; Enrich projects with iteration/worker data
        enriched {:projects
                  (mapv (fn [{:keys [slug status priority path] :as proj}]
                          (let [cfg (get configs slug)
                                iter (get iterations slug)
                                worker-count (get workers slug 0)
                                max-w (or (:max-workers cfg) 1)]
                            (cond-> {:slug slug
                                     :status status
                                     :priority priority
                                     :path path
                                     :workers worker-count
                                     :max-workers max-w}
                              iter (assoc :iteration iter))))
                        all-projects)}]
    (if json?
      (list/format-list-json enriched)
      (list/format-list enriched))))
