(ns revolt.catapulte.task
  (:require [cemerick.pomegranate.aether :as aether]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [revolt.catapulte.gpg :as gpg]
            [revolt.task :refer [create-task make-description Task]]
            [revolt.utils :as utils])
  (:import java.io.FileNotFoundException))

(def default-repo-settings
  {"clojars" {:url "https://clojars.org/repo"
              :checksum :warn
              :username (System/getenv "CLOJARS_USER")
              :password (System/getenv "CLOJARS_PASS")}})

(def artifact-id-tag :xmlns.http%3A%2F%2Fmaven.apache.org%2FPOM%2F4.0.0/artifactId)
(def group-id-tag :xmlns.http%3A%2F%2Fmaven.apache.org%2FPOM%2F4.0.0/groupId)
(def version-tag :xmlns.http%3A%2F%2Fmaven.apache.org%2FPOM%2F4.0.0/version)

;; shamelessly copied from https://github.com/slipset/deps-deploy/blob/master/src/deps_deploy/deps_deploy.clj

(defn extension
  [f]
  (if-let [[_ signed-extension] (re-find #"\.([a-z]+\.asc)$" f)]
    signed-extension
    (if (= "pom.xml" (.getName (io/file f)))
      "pom"
      (last (.split f "\\.")))))

(defn classifier
  "The classifier is be located between the version and extension name of the artifact.
  See http://maven.apache.org/plugins/maven-deploy-plugin/examples/deploying-with-classifiers.html "

  [version f]
  (let [pattern (re-pattern (format "%s-(\\p{Alnum}*)\\.%s" version (extension f)))
        [_ classifier-of] (re-find pattern f)]
    (when (seq classifier-of)
      classifier-of)))

(defn coordinates-from-pom-file
  [pom-file]
  (let [pom (try
              (slurp pom-file)
              (catch FileNotFoundException ex
                (log/error "POM file not found:" pom-file)))
        tmp (some->> pom
                     xml/parse-str
                     :content
                     (remove string?)
                     (keep (fn [{:keys [tag] :as m}]
                             (when (or (= tag artifact-id-tag)
                                       (= tag group-id-tag)
                                       (= tag version-tag))
                               {(keyword (name tag)) (first (:content m))})))
                     (apply merge))]
    (when tmp
      [(symbol (str (:groupId tmp) "/" (:artifactId tmp))) (:version tmp)])))

(defn artifacts
  [version files]
  (into {} (for [f files]
             [[:extension (extension f)
               :classifier (classifier version f)] f])))

(defmethod create-task ::deploy
  [_ opts classpaths target]
  (reify Task
    (invoke [this input ctx]
      (let [options  (merge opts input)
            pom-file (or (:pom-file options) "pom.xml")
            jar-file (or (:jar-file options)
                         (:jar-file ctx))
            coords   (coordinates-from-pom-file pom-file)
            version  (second coords)]

        (System/setProperty "aether.checksums.forSignature" "true")
        (if-not (.exists (io/file jar-file))
          (log/error "Artifact file not found:" jar-file)
          (utils/timed
           (str "DEPLOYING (" (str/join "-" coords) ")")
           (aether/deploy :artifact-map (artifacts version (gpg/sign! (:sign-key options) jar-file pom-file))
                          :repository (or (:repository options) default-repo-settings)
                          :coordinates coords)))
        ctx))
    (describe [this]
      (make-description "Artifact deployer" "Deploys artifact to remote repository (clojars by default)"
                        :jar-file "location of jar file to deploy"
                        :pom-file "location of pom file to deploy"
                        :sign-key "GPG key to use when signing or nil if no signing is needed"
                        :repository "repository setting. clojars settings are used by default with CLOJARS_USER and CLOJARS_PASS env variables."))))

(defmethod create-task ::install
  [_ opts classpaths target]
  (reify Task
    (invoke [this input ctx]
      (let [options  (merge opts input)
            pom-file (or (:pom-file options) "pom.xml")
            jar-file (or (:jar-file options)
                         (:jar-file ctx))
            coords   (coordinates-from-pom-file pom-file)
            version  (second coords)]

        (if-not (and jar-file (.exists (io/file jar-file)))
          (log/error "Artifact file not found:" jar-file)
          (utils/timed
           (str "INSTALLING to local .m2 (" (str/join "-" coords) ")")
           (aether/install :artifact-map (artifacts version [pom-file jar-file])
                           :transfer-listener :stdout
                           :coordinates coords)))
        ctx))
    (describe [this]
      (make-description "Artifact installer" "Installs artifact into local .m2 repository"
                        :jar-file "location of jar file to install"
                        :pom-file "location of pom file to install"))))
