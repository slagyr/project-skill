(ns braids.config-spec
  (:require [speclj.core :refer :all]
            [braids.config :as config]))

(describe "braids.config"

  (describe "parse-config"
    (it "parses a config EDN string"
      (should= {:braids-home "/custom/path" :orchestrator-channel nil :verbose false}
               (config/parse-config "{:braids-home \"/custom/path\"}")))

    (it "applies defaults for missing keys"
      (should= {:braids-home "~/Projects" :orchestrator-channel nil :verbose false}
               (config/parse-config "{}")))

    (it "preserves extra keys"
      (should= {:braids-home "~/Projects" :orchestrator-channel nil :verbose false :extra "val"}
               (config/parse-config "{:extra \"val\"}")))

    (it "parses verbose flag"
      (should= true (:verbose (config/parse-config "{:verbose true}")))
      (should= false (:verbose (config/parse-config "{:verbose false}")))))

  (describe "serialize-config"
    (it "round-trips through parse"
      (let [cfg {:braids-home "/foo/bar" :orchestrator-channel nil :verbose false}]
        (should= cfg (config/parse-config (config/serialize-config cfg)))))))
