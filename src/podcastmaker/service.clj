(ns podcastmaker.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [io.pedestal.interceptor.chain :as chain]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [ring.util.response :as ring-resp]
            [ring.middleware.session.cookie :as cookie]
            [podcastmaker.login :as login]
            [podcastmaker.home :as home]
            [podcastmaker.rss :as rss]
            [podcastmaker.user-data :as ud]
            [podcastmaker.layout :as h]))

(use 'hiccup.core)

(defn about-page
  [request]
  (ring-resp/response (h/layout "About" (str "Podcast Maker.   Running Clojure "
                                             (clojure-version)
                                             " from "
                                             (route/url-for ::about-page)
                                             " on host " (get-in request [:headers "host"])))))

(defn scrub-name [x]
  (-> x
      (clojure.string/replace #"[^a-zA-Z0-9_ ]" "")
      (clojure.string/replace #"mp3$" ".mp3")))

(defn download-url [id url]
  (println "=-=-=-=-=-=-=-=-=-=-=-=  DOWNOADING =-=-=-=-=-=-=-=-=-=-=-=")
  (let [temp-dir (io/file (str "/tmp/" (System/currentTimeMillis)))]
    (.mkdir temp-dir)
    (println "=-=-=-=-=-=       RUNNING: " "sh/sh" "youtube-dl" "-q" "--extract-audio" "--audio-format" "mp3" url :dir (str temp-dir))
    (sh/sh "youtube-dl" "-q" "--extract-audio" "--audio-format" "mp3" url :dir (str temp-dir))
    (println "=-=-=-=-=-=       Finished with youtube-dl")
    (let [mp3-path (first (.listFiles temp-dir))
          name-scrubbed (scrub-name (.getName mp3-path))]
                                        ; TODO should try/catch and inspect returned map for errors
      (sh/sh "mv" (str mp3-path) (str (io/file (ud/data-dir-file id) name-scrubbed)))
      (.delete mp3-path))))

(defn save-file [id file]
  (let [filename (:filename file)
        tempfile (:tempfile file)
        destfile (io/file (ud/data-dir-file id) filename)]
    (println "=-=-=-=-=- SAVING to " tempfile " to " destfile)
    (io/copy tempfile destfile)))

(defn save-files [id files]
  (doall (map #(save-file id %) files)))

(defn add-page [request]
  (println "#################################  CALLED add-page params:" (:params request))
  (let [url (get (:params request) "url")
        file (get (:params request) "file")
        mp3url (get (:params request) "mp3url")
        id  (get-in request [:session :id])]
    (if-not (empty? url)
      (do
        (future (println (str "&&&&&&&&&&&&&&&&&& STARTING FUTURE: " url))
                (download-url id url)
                (println "FuTURE DONE"))
        (ud/flash-and-redirect-to-home request "The audio is being downloaded.  It should be availble in a minute or two.  Reload to see it added."))
      (do
        (if (vector? file)
          (save-files id file))
        (if (and (not (empty? file)) (not (vector? file)) (not (= 0 (:size file))))
          (do
            (println "##################################### SHOULD SAVE:::  " id)
            (save-file id file)))
        (ring-resp/redirect "/")))))

(defn delete-post [request]
  (let [fname-raw (get-in request [:path-params :file])
        fname-decode (java.net.URLDecoder/decode fname-raw "UTF-8")
        fname (.replaceAll fname-decode "/" "")
        file (io/file (ud/data-dir-file (ud/id request)) fname)]
    (println "fname" fname)
    (.delete file)
    (ring-resp/redirect "/")))

(defn sort-handle [request]
  (let [dir (get-in request [:path-params :direction])
        id (ud/id request)
        ascend-comp  #(.compareTo %2 %1)
        descend-comp  #(.compareTo %1 %2)
        use-comp (if (= dir "ascending") ascend-comp descend-comp)
        sorted (sort-by #(.getName %) use-comp (.listFiles (ud/data-dir-file id)))
        mod-time (System/currentTimeMillis)]
    (doseq [x sorted]
      (let [j (+ mod-time (* 60000 (.indexOf sorted x)))]
        (.setLastModified x j)))
    (ud/flash-and-redirect-to-home request (str "Sorted " dir "!"))))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).

(def check-auth-interceptor
  {:name ::check-auth
   :enter (fn [context]
            (do
              (println "------------------- CALLED bacon.")
              (clojure.pprint/pprint (get-in context [:request :session]))
              (println "------------------- CALLED cheeze.")
              (let [session (get-in context [:request :session])
                    id (:id session)]
                (if (nil? id)
                  (chain/terminate (assoc context :response (ring-resp/redirect "/login")))
                  context))))})

(def session-interceptor (ring-mw/session {:store (cookie/cookie-store)}))

(def common-interceptors [(body-params/body-params) http/html-body
                          session-interceptor check-auth-interceptor])

(def no-check-interceptors [(body-params/body-params) http/html-body
                            session-interceptor])

(def routes #{;;;; Not logged In

              ;; This initial page should be an introduction/sales - perhaps my account?
              ["/" :get (conj common-interceptors `home/home-page)]
              ["/about" :get (conj no-check-interceptors `about-page)]

              ["/content/:user/:content" :get (conj no-check-interceptors `rss/content-page)]
              ["/rss/:user" :get (conj no-check-interceptors `rss/rss-page)]
              ["/login" :get (conj no-check-interceptors `login/login-page)]
              ["/login" :post (conj no-check-interceptors `login/login-attempt)]
              ["/guest" :post (conj no-check-interceptors `login/login-as-guest)]

              ["/register" :get (conj no-check-interceptors `login/register-page)]
              ["/register" :post (conj no-check-interceptors `login/register-attempt)]

              ["/forgot" :get (conj no-check-interceptors `login/forgot-page)]
              ["/forgot" :post (conj no-check-interceptors `login/forgot-attempt)]

              ["/logout" :get (conj no-check-interceptors `login/logout)]

              ;;;; Logged In

              ["/add" :post (conj [(io.pedestal.http.ring-middlewares/multipart-params) session-interceptor `add-page])]
              ["/delete/:file" :post (conj common-interceptors `delete-post)]
              ["/sort/:direction" :get (conj common-interceptors `sort-handle)]})

;; Consumed by podcastmaker.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 3000
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})
