
;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.core
  (:require [speako.consumer :refer [GraphQLConsumer] :as consumer]
            [speako.schema :as schema]
            [clojure.walk :as walk]
            [cljs.nodejs :as nodejs]
            [speako.common :as common]))

(nodejs/enable-util-print!)

(defn- bail [msg] (fn [& _] (throw (js/Error. (common/format "Not implemented: '%s'." msg)))))

(defn- get-data-resolver [is-js? {:keys [query create modify delete]
                                  :or   {query (bail "query")
                                         create (bail "create")
                                         modify (bail "modify")
                                         delete (bail "delete")}}]
  (let [to-js (if is-js? clj->js identity)
        stringify #(js/JSON.stringify (clj->js %))]
    (reify consumer/DataResolver
      (query [_ typename predicate]
        (common/dbg-print "speako: query: typename: %s, predicate: %s" typename predicate)
        (query typename predicate))
      (create [_ typename inputs]
        (common/dbg-print "speako: create: typename: %s, inputs: %s" typename (stringify inputs))
        (create typename (to-js inputs)))
      (modify [_ typename inputs]
        (common/dbg-print "speako: modify: typename: %s, inputs: %s" typename (stringify inputs))
        (modify typename (to-js inputs)))
      (delete [_ typename id]
        (common/dbg-print "speako: delete: typename: %s, id: %s" typename (stringify id))
        (delete typename id)))))

(defn consume-schema [config schema-filename-or-contents]
  (let [is-js? (object? config)
        config-map (if is-js? (walk/keywordize-keys (js->clj config)) config)
        resolver-methods (select-keys config-map [:query :create :modify :delete])
        user-consumers (or (:consumers (select-keys config-map [:consumers])) [])
        _ (assert (vector? user-consumers) "User supplied consumers must be of type vector.")
        consumer (GraphQLConsumer (get-data-resolver is-js? config-map))
        consumers (into [consumer] user-consumers)]
    (apply (partial schema/load-schema schema-filename-or-contents) consumers)))

(defn ^:export get-schema [config schema-filename-or-contents]
  (-> (consume-schema config schema-filename-or-contents) second first))

(defn ^:export set-debug [debug?]
  (assert (boolean? debug?))
  (reset! common/DEBUG debug?))

(def ^:export getSchema get-schema)
(def ^:export setDebug set-debug)

(defn noop [] nil)
(set! *main-cli-fn* noop)
