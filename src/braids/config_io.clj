(ns braids.config-io
  "IO functions for reading/writing braids config.edn."
  (:require [babashka.fs :as fs]
            [braids.config :as config]))

(def default-config-path
  (str (System/getProperty "user.home") "/.openclaw/braids/config.edn"))

(defn load-config
  "Load config from path. Returns defaults if file doesn't exist."
  ([] (load-config default-config-path))
  ([path]
   (if (fs/exists? path)
     (config/parse-config (slurp path))
     config/defaults)))

(defn save-config!
  "Write config map to path."
  ([config] (save-config! config default-config-path))
  ([config path]
   (spit path (config/serialize-config config))))

(defn resolve-braids-home
  "Resolve braids-home from config file."
  ([] (resolve-braids-home default-config-path))
  ([config-path]
   (let [config (load-config config-path)]
     (str (fs/expand-home (:braids-home config))))))
