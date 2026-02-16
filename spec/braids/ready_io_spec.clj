(ns braids.ready-io-spec
  (:require [speclj.core :refer :all]
            [braids.ready-io :as rio]))

(describe "braids.ready-io"

  (describe "count-workers"

    (it "returns empty map for no labels"
      (should= {} (rio/count-workers [])))

    (it "counts workers per project slug"
      (should= {"proj" 2 "other" 1}
               (rio/count-workers ["project:proj:bead-1"
                                   "project:proj:bead-2"
                                   "project:other:bead-3"])))

    (it "ignores non-project labels"
      (should= {"proj" 1}
               (rio/count-workers ["project:proj:bead-1"
                                   "agent:main"
                                   "cron:something"])))

    (it "handles project: prefix with just slug"
      (should= {"proj" 1}
               (rio/count-workers ["project:proj"])))))
