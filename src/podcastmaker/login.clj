
(ns podcastmaker.login
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [ring.util.response :as ring-resp]
            [ring.middleware.session.cookie :as cookie]
            [podcastmaker.user-data :as ud]
            [podcastmaker.layout :as h]))

(import 'java.security.MessageDigest
        'java.math.BigInteger)

(use 'hiccup.core)

(defn login-as [request email]
  (let [account-data-dir (ud/data-dir-file email)]
    (if (.exists account-data-dir)
      (assoc (ring-resp/redirect "/") :session {:id email})
      (do
        (.mkdirs account-data-dir)
        (if (.exists account-data-dir)
          (assoc (ring-resp/redirect "/") :session {:id email})
          (ud/flash-and-redirect-to-login request "Error logging in... unable to access user's data."))))))

(defn login-as-guest [request]
  (login-as request "guest"))

(defn is-login-correct [])

(defn login-attempt  [request]
  (let [email (get-in request [:form-params :email])
        password (get-in request  [:form-params :password])]
    (if (is-login-correct email password)
      (login-as request email)
      (ud/flash-and-redirect-to-login request "email or password is incorrect")
            )))

(defn logout  [request]
  (assoc (ring-resp/redirect "/login") :session nil ))

(defn register-page  [request]
  (ud/flash-scrub request
                  (ring-resp/response
                   (h/layout "Home"
                             (html
                              (if-not (nil? (get-in request [:session :flash]))
                                [:div.alert.alert-danger.alert-auto (get-in request [:session :flash])])

                              [:div.container

                               [:div.row
                                [:div.col-md-4
                                 [:h2 "Register New Account"]
                                 ]
                                ]
                               [:br ]
                               [:form { :method "POST" :action "/register" }
                                [:div.row
                                 [:div.col-md-3

                                  [
                                   :div.form-group
                                   [:label {:for "login-email"} "Email Address"]
                                   [:input.form-control {:id        "login-email" :name "email" :placeholder "user@example.com"
                                                         }]
                                   ]
                                  ]
                                 ]
                                [:div.row
                                 [:div.col-md-3
                                  [
                                   :div.form-group
                                   [:label {:for "password"} "Password"]
                                   [:input.form-control {:type      "password" :name "password" :placeholder "Password"
                                                         }]
                                   [:br]
                                   [:button.btn.btn-default {:type     "submit"}
                                    "Register"]]
                                  ]
                                 ]]])))))

(defn validate-email [email] 
  ;; https://stackoverflow.com/questions/742451/what-is-the-simplest-regular-expression-to-validate-emails-to-not-accept-them-bl
  (re-matches #"(?!.*\.\.)(^[^\.][^@\s]+@[^@\s]+\.[^@\s\.]+$)" email))


(defn md5 [s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn hash-account [email password]
  (let [data-key (ud/read-data-key)]
    (md5 (str data-key "|" email "|" password))))

(defn create-account [email password]
  (let [data-dir-file (ud/data-dir-file email)
        hashed-password (hash-account email password)
        ]
    (.mkdirs data-dir-file)
    (spit (io/file data-dir-file ".pmpass") hashed-password ) 
    ))

(defn is-login-correct [email password]
  (let [data-dir-file (ud/data-dir-file email)]
        (if (not (.exists data-dir-file))
          false
          (= (hash-account email password)
                 (slurp (io/file data-dir-file ".pmpass"))))))

(defn is-existing-account [email]
  (.exists (ud/data-dir-file email)))

(defn register-attempt  [request]
  (let [email (get-in request [:form-params :email])
        password (get-in request  [:form-params :password])]
    (cond
      (not (validate-email email)) (ud/flash-and-redirect request "/register" "Invalid Email Address" )
      (empty? password) (ud/flash-and-redirect request "/register" "Password is required" )
      (is-existing-account email)  (ud/flash-and-redirect request "/register" "Account already exists")
      :else (do
              (create-account email password)
              (login-as request email)))))

;;  (ring-resp/response
;;   (h/layout "Home" (html [:div "Forget with " email " and " password ])))))
;;  (ud/flash-and-redirect request "/register" "Username Already in Use" ))

(defn forgot-page  [request]
  (clojure.pprint/pprint request)
  (ring-resp/response
   (h/layout "Home" (html [:div "Forget with " (pr-str (:form-params request)) ]))))

(defn forgot-attempt  [request]
  (clojure.pprint/pprint request)
  (ring-resp/response
   (h/layout "Home" (html [:div "Forget with " (pr-str (:form-params request)) ]))))


(defn login-page  [request]
  (ud/flash-scrub request
                  (ring-resp/response
                   (h/layout "Home"
                             (html
                              [:div.container
                               (if-not (nil? (get-in request [:session :flash]))
                                 [:div.alert.alert-danger.alert-auto (get-in request [:session :flash])])

                               [:div.row
                                [:div.col-md-3
                                 [:h2 "Login"]

                                 [:form { :method "POST" :action "/login" }
                                  [
                                   :div.form-group
                                   [:label {:for "login-email"} "Email Address"]
                                   [:input.form-control {:id        "login-email" :name "email" :placeholder "user@example.com"
                                                         }]

                                   [
                                    :div.form-group
                                    [:label {:for "password"} "Password"]
                                    [:input.form-control {:type      "password" :name "password" :placeholder "Password"
                                                          }]
                                    [:br]
                                    [:button.btn.btn-default {:type     "submit"}
                                     "Login"]]
                                   ]
                                  ]]

                                [:div.col-md-2]
                                
                                [:div.col-md-3
                                 [:h2 "Guest"]

                                 [:form { :method "POST" :action "/guest" }
                                  [:input {:type "hidden" :name "username" :value "guest" }]
                                  [:div "The guest account is frequently reset."]
                                  [:br]

                                  [:button.btn.btn-default {:type     "submit"} "Login as Guest"]

                                  [:br]
                                  [:br]
                                  [:a { :href "/forgot"} "Forgot Password?" ]
                                  [:br]
                                  [:br]
                                  [:a { :href "/register"} "Register New User" ]

                                  ]]]])))))


