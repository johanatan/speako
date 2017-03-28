
;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [speako.schema :as schema]
            [speako.common :refer [format pluralize]]
            [loom.graph :as graph]
            [loom.attr :as attr]
            [sqlingvo.core :as sql]
            [sqlingvo.node :as db :refer-macros [<? <!?]]
            [cljs.core.async :as async]
            [promesa.core :as p :refer-macros [alet]]
            [camel-snake-kebab.core :refer [->PascalCase ->camelCase]]
            [clojure.set]))

(defn- consume-types []
  (let [result (atom {:objects {} :unions {} :enums {}})]
    (reify schema/TypeConsumer
      (consume-object [_ typename fields]
        (swap! result assoc-in [:objects (keyword typename)] fields))
      (consume-union [_ typename constituent-types]
        (swap! result assoc-in [:unions (keyword typename)] (map keyword constituent-types)))
      (consume-enum [_ typename constituent-types]
        (swap! result assoc-in [:enums (keyword typename)] constituent-types))
      (finished [_] @result))))

(defn- object-edges [nodes [node fields]]
  (remove nil? (map (fn [[name type list? required?]]
                      (let [typ (keyword type)]
                        (when (nodes typ)
                          [node typ list? required?]))) fields)))

(defn- construct-graph [parsed]
  (let [nodes (set (keys (parsed :objects)))
        unions (set (keys (parsed :unions)))
        object-edges (partial object-edges (clojure.set/union nodes unions))
        edges (remove empty? (mapcat object-edges (parsed :objects)))
        g (apply graph/digraph (concat (map (partial take 2) edges) nodes))
        attrs (mapcat #(let [edge (take 2 %1)] [[edge :list? (%1 2)] [edge :required? (%1 3)]]) edges)
        with-edge-attrs (reduce #(attr/add-attr-to-edges %1 (%2 1) (%2 2) [(%2 0)]) g attrs)
        with-union-attrs (attr/add-attr-to-nodes with-edge-attrs :union? true unions)]
    with-union-attrs))

(defn- chan->promise [channel]
  (p/promise
   (fn [resolve reject]
     (go
       (let [res (<? channel)]
         (resolve res))))))

(defn- get-table-names [db]
  (chan->promise
   (async/map #(map :table_name %1)
              [(db/execute
                (sql/select db [:table_name]
                            (sql/from :information_schema.tables)
                            (sql/where '(= :table_schema "public"))))])))

(defn- get-columns-for-table [db table-name]
  (chan->promise
   (db/execute
    (sql/select db [:column_name :data_type]
                (sql/from :information_schema.columns)
                (sql/where '(= :table_name table-name))))))

(defn- entities [graph]
  (let [nodes (graph/nodes graph)]
    (remove #(attr/attr graph % :union?) nodes)))

(defn- tables-exist? [db graph]
  (p/alet [tables (p/await (get-table-names db))
           expected (map #(-> % name pluralize ->camelCase) (entities graph))
           remaining (clojure.set/difference (set expected) (set tables))]
          (when (not-empty remaining)
            (js/console.error (format "ERROR: Backing tables not found: %s" (into [] remaining))))
          (empty? remaining)))
