(ns revolt.catapulte.gpg
  (:require [revolt.shell]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(defmacro gpg
  [& args]
  `(revolt.shell/sh "gpg" ~@args))

(defn- gpg-detach-sign!
  [sign-key input-file output-file]
  (log/infof "Signing %s with key %s" input-file sign-key)
  (gpg --yes --armour --detach-sign -o output-file -u sign-key input-file)
  output-file)

(defn sign!
  [sign-key jar-file pom-file]
  (-> [jar-file pom-file]
      (cond-> sign-key
        (into [(gpg-detach-sign! sign-key jar-file (str jar-file ".asc"))
               (gpg-detach-sign! sign-key pom-file (str/replace jar-file #"(\.jar|\.war)$" ".pom.asc"))]))))
