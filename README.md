# JAR files local installation / deployment with Revolt

## deps.edn

``` clojure
{:paths ["resources"]
 :aliases {:dev {:extra-deps  {defunkt/revolt {:mvn/version "1.3.0-SNAPSHOT"}
                               defunkt/revolt-catapulte-task {:mvn/version "0.1.1-SNAPSHOT"}}
                 :main-opts   ["-m" "revolt.bootstrap"]}}}
```

## revolt.edn

``` clojure
:revolt.catapulte.task/deploy {:sign-key "Foo Bar <foo@bar.bazz>"}
```

## using a task with REPL

`clj -A:dev -p rebel`

``` clojure
(require '[revolt.task :as t])
(require '[revolt.catapulte.task :as catapulte])
(t/require-task ::catapulte/deploy)
(t/require-task ::catapulte/install)

;; to install jar to local .m2 repository
(install)

;; to deploy to clojars (note CLOJARS_USER and CLOJARS_PASS environmental variables need to be set)
;; also pom.xml file has to be generated before, eg. by clj -Spom

;; if :sign-key was configured jar will be signed first
(deploy)

;; enforce no signing
(deploy {:sign-key nil})
```

## using a task from command-line

`clj -A:dev -t revolt.catapulte.task/install`

`clj -A:dev -t revolt.catapulte.task/deploy:sign-key=xxxx`
