;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aleph.ring-test
  (:use
    [clojure test])
  (:require
    [aleph.netty :as netty]
    [aleph.http :as http]
    [clj-http.client :as client])
  (:import
    [java.util.concurrent
     Executors]))

(netty/leak-detector-level! :paranoid)

(def ex (Executors/newFixedThreadPool 8))

(defn create-url
  ([path]
     (create-url 8080 path))
  ([port path]
     (create-url "http" "localhost" port path))
  ([scheme host port path]
     (str scheme "://" host ":" port path)))

(defn request
  ([& {:as options}]
     (let [options (merge
                     {:url (create-url "/")
                      :method :get}
                     options)]
       (->> (client/request
              {:request-method (:method options)
               :url (:url options)
               :headers (:headers options)
               :body (:body options)})
         :body
         read-string))))

(defn get-request-value [request keys]
  (reduce
    #(or (get %1 %2) (get %1 (keyword %2)))
    request
    keys))

(defn request-callback [keys]
  (fn [request]
    {:status 200
     :headers {"content-type" "text/plan"}
     :body (prn-str (get-request-value request keys))}))

(defmacro with-server [keys & body]
  `(let [server# (http/start-server (request-callback ~keys) {:port 8080, :executor ex})]
     (try
       ~@body
       (finally
         (.close ^java.io.Closeable server#)))))

;;;

(deftest test-request-method
  (with-server [:request-method]
    (doseq [method [:get :post :put :delete]]
      (is (= method (request :method method))))))

(deftest test-scheme
  (with-server [:scheme]
    (is (= :http (request)))))

(deftest test-uri
  (with-server [:uri]
    (doseq [uri ["/a" "/a/b" "/a/b/c/"]]
      (is (= uri (request :url (create-url uri)))))))

(deftest test-query-string
  (with-server [:query-string]
    (doseq [[k v] {"/" nil
                   "/a" nil
                   "/a?a=b" "a=b"
                   "/a?a=b&c=d" "a=b&c=d"}]
      (is (= v (request :url (create-url k)))))))

(deftest test-server-name
  (with-server [:server-name]
    (is (.startsWith ^String (request) "localhost"))))

(deftest test-server-port
  (with-server [:server-port]
    (is (= 8080 (request)))))

(deftest test-remote-addr
  (with-server [:remote-addr]
    (is (= "127.0.0.1" (request)))))
