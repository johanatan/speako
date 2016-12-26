
;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.core
  (:require [speako.consumer :refer [GraphQLConsumer] :as consumer]
            [speako.schema :as schema]
            [clojure.walk :as walk]
            [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(defn- bail [msg] (fn [& _] (throw (js/Error. (common/format "Not implemented: '%s'." msg)))))

(defn- get-data-resolver [is-js? {:keys [query create modify delete]
                                  :or   {query (bail "query")
                                         create (bail "create")
                                         modify (bail "modify")
                                         delete (bail "delete")}}]
  (reify consumer/DataResolver
    (query [_ typename predicate] (query typename predicate))
    (create [_ typename inputs] (create typename (if is-js? (clj->js inputs) inputs)))
    (modify [_ typename inputs] (modify typename (if is-js? (clj->js inputs) inputs)))
    (delete [_ typename id] (delete typename id))))

(defn ^:export get-schema [resolver-methods schema-filename-or-contents]
  (let [is-js? (object? resolver-methods)]
    (first (second
            (schema/load-schema
             schema-filename-or-contents
             (GraphQLConsumer
              (get-data-resolver
               is-js?
               (if is-js? (walk/keywordize-keys (js->clj resolver-methods)) resolver-methods))))))))

(defn noop [] nil)
(set! *main-cli-fn* noop)
