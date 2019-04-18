(ns podcastmaker.home
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
            [podcastmaker.user-data :as ud]
            [podcastmaker.layout :as h]))

(use 'hiccup.core)

(comment
  (def status (atom "not much"))
  (.start (Thread. #(while true (do
                                  (Thread/sleep 1000)
                                  (println "Bo Ya. " (System/currentTimeMillis))
                                  (reset! status (str (System/currentTimeMillis))))))))

(defn list-file [id file]
  (vec
   [:tr
    [:td [:a {:href (str "/content/" id "/" (.getName file))} (.getName file)]]
    [:td (format "%,d" (.length file))]
    [:td [:form {:method "POST" :action (str "/delete/" (.getName file))}
          [:input {:type "submit" :value "Delete"}]]]]))

(defn home-page
  [request]
  (let [host (get-in request [:headers "host"])
        id (ud/id request)
        rss-feed (str "http://" host "/rss/" id)]
    (ud/flash-scrub request (ring-resp/response
                             (h/layout "Home"
                                       (html
                                        (if-not (nil? (get-in request [:session :flash]))
                                          [:div.alert.alert-success (get-in request [:session :flash])])

                                        #_[:script "function loadlink(){$('#status').load('status',function () {$(this).unwrap(); });"
                                           "setInterval(function(){loadlink()}}, 5000);"]

                                        #_[:div "HUH " @status]

                                        [:div
                                         [:div.panel.panel-default
                                          [:div.panel-body
                                           "Your Podcast Subcription/Feed is  "
                                           [:a {:href rss-feed} rss-feed]]]

                                         [:h2 "Podcasts"]
                                         "Sort:&nbsp;&nbsp;" [:a.btn.btn-default.btn-xs {:href "/sort/ascending"} "Ascending"] "&nbsp;" [:a.btn.btn-default.btn-xs {:href "/sort/decending"} "Descending"]
                                         [:br]
                                         [:br]
                                         [:table.table.table-striped
                                          [:tr [:th "Name" [:th "Size"] [:th "Controls"]]]
                                          (map #(list-file id %) (ud/get-podcast-files (ud/id request)))]]

                                        [:br]
                                        [:div.panel-group {:id "accordion"}
                                         [:div.panel.panel-default
                                          [:div.panel-heading
                                           [:h3.panel-title
                                            [:a {:data-toggle "collapse" :data-parent "#accordion" :href "#collapseOne"}
                                             "Add Audio Content"]]]
                                          [:div.panel-collapse.collapse {:id "collapseOne"}
                                           [:div.panel-body
                                            [:div.row
                                             [:form {:method "POST" :action "/add" :enctype "multipart/form-data"}
                                              [:div.col-sm-2
                                               [:label {:for "vidurl"} "Video URL"]]
                                              [:div.col-sm-7
                                               [:input.form-control {:type "text" :name "url" :id "vidurl" :placeholder "https://www.youtube.com/watch?v=ctrjYPPYlI0"}]]
                                              [:div.col-sm-2
                                               [:button.btn.btn-default {:type "submit"} "Add Video"]]]]
                                            [:br]

                                            [:div.row
                                             [:form {:method "POST" :action "/add" :enctype "multipart/form-data"}
                                              [:div.col-sm-2
                                               [:label {:for "file"} "MP3 File"]]
                                              [:div.col-sm-7
                                               [:input.form-control {:multiple "" :type "file" :name "file" :id "file" :placeholder "c:\\jj.mp3"}]]
                                              [:div.col-sm-2
                                               [:button.btn.btn-default {:type "submit"} "Add MP3 File"]]]]

                                            [:br]
                                            [:div.row
                                             [:form {:method "POST" :action "/add" :enctype "multipart/form-data"}
                                              [:div.col-sm-2
                                               [:label {:for "mp3url"} "MP3 URL"]]
                                              [:div.col-sm-7
                                               [:input.form-control {:type "text" :name "mp3url" :id "mp3url"
                                                                     :value "http://traffic.libsyn.com/developeronfire/DeveloperOnFire-258-JeffAtwood.mp3"
                                                                     :placeholder "http://traffic.libsyn.com/developeronfire/DeveloperOnFire-258-JeffAtwood.mp3"}]]
                                              [:div.col-sm-2
                                               [:button.btn.btn-default {:type "submit"} "Add MP3 URL"]]]]]]]]))))))
