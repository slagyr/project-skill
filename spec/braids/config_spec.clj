(ns braids.config-spec
  (:require [speclj.core :refer :all]
            [braids.config :as config]))

(describe "braids.config"

  (describe "parse-config"
    (it "parses a config EDN string"
      (should= {:braids-home "/custom/path"}
               (config/parse-config "{:braids-home \"/custom/path\"}")))

    (it "applies defaults for missing keys"
      (should= {:braids-home "~/Projects"}
               (config/parse-config "{}")))

    (it "preserves extra keys"
      (should= {:braids-home "~/Projects" :extra "val"}
               (config/parse-config "{:extra \"val\"}"))))

  (describe "serialize-config"
    (it "round-trips through parse"
      (let [cfg {:braids-home "/foo/bar"}]
        (should= cfg (config/parse-config (config/serialize-config cfg)))))))
