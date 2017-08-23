(defproject podcastmaker "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.2"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.2"]
                 ;; [io.pedestal/pedestal.immutant "0.5.2"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.2"]

                 [ch.qos.logback/logback-classic "1.1.8" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.22"]
                 [org.slf4j/jcl-over-slf4j "1.7.22"]
                 [org.slf4j/log4j-over-slf4j "1.7.22"]
                 [claudio "0.1.3"]
                 [hiccup "1.0.5"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev {:aliases {"run-dev" ["with-profiles" "dev,jetty" "trampoline" "run" "-m" "podcastmaker.server/run-dev"]}
                   :dependencies [ [io.pedestal/pedestal.service-tools "0.5.1"]]
                   :plugins [[ohpauleez/lein-pedestal "0.1.0-beta10"]
                             [lein-cljfmt "0.5.6"]
                             ]
                   :pedestal {;:web-xml "war-resources/WEB-INF/web.xml" ;; use this instead of generating
                              :servlet-name "PodcastMaker"
                              :servlet-display-name "Podcast Maker"
                              :servlet-description "A simple tool for making podcasts"
                              :server-ns "podcastmaker.server"}}
             :test {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]]}
             :jetty {:dependencies [[io.pedestal/pedestal.jetty "0.5.1"]]}
             :uberjar {:aot [podcastmaker.server]}}
  :main ^{:skip-aot true} podcastmaker.server)
