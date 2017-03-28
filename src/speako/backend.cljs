
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
            [cljs.pprint :refer [pprint]]
            [cats.core :as m :include-macros true]
            [cats.builtin]
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

(defn- chan->promise
  ([channel] (chan->promise identity channel))
  ([xfm channel]
   (p/promise
    (fn [resolve reject]
      (go
        (let [res (<? channel)]
          (resolve (xfm res))))))))

(defn- get-table-names [db]
  (chan->promise
   (async/map #(map :table_name %1)
              [(db/execute
                (sql/select db [:table_name]
                            (sql/from :information_schema.tables)
                            (sql/where '(= :table_schema "public"))))])))
(def ^:private db-types-map
  {"ID" {:scalar "integer" :array "_int4"}
   "Boolean" {:scalar "boolean" :array "_bool"}
   "String" {:scalar "character varying" :array "_varchar"}
   "Float" {:scalar "double precision" :array "_float8"}
   "Timestamp" {:scalar "timestamp without time zone" :array nil}
   "Int" {:scalar "integer" :array "_int4"}})

(def ^:private db-scalar-types-map (m/fmap :scalar db-types-map))
(def ^:private db-array-types-map (into {} (map #(do [(%1 :array) (%1 :scalar)]) (vals db-types-map))))
(def ^:private scalar-types (set (keys db-types-map)))

(defn- get-columns-for-table [table-name db]
  (let [xfm-type #(condp = %1 "ARRAY" (format "%s[]" (db-array-types-map %2)) %1)]
    (chan->promise
     #(into {} %)
     (async/map
      #(map (fn [r] [(:column_name r) {:type (xfm-type (:data_type r) (:udt_name r))
                                       :is-nullable? (:is_nullable r)}]) %1)
      [(db/execute (sql/select db [:column_name :data_type :is_nullable :udt_name]
                               (sql/from :information_schema.columns)
                               (sql/where `(= :table_name ~table-name))))]))))

(defn- entities [graph]
  (let [nodes (graph/nodes graph)]
    (remove #(attr/attr graph % :union?) nodes)))

(defn- tables-exist? [graph db]
  (p/alet [tables (p/await (get-table-names db))
           expected (map #(-> % name pluralize ->camelCase) (entities graph))
           remaining (clojure.set/difference (set expected) (set tables))]
          (when (not-empty remaining)
            (js/console.error (format "ERROR: Backing tables missing: %s" (into [] remaining))))
          (empty? remaining)))

(defn- pprint-query [db query-fn]
  (p/map pprint
         (p/alet [db (p/await (chan->promise (db/connect db)))
                  res (p/await (query-fn db))
                  _ (db/disconnect db)]
                 res)))

(defn- scalar-column-exists? [columns-meta [name type list? required?]]
  (let [column-meta (columns-meta name)
        expected-type (format "%s%s" (db-scalar-types-map type) (if list? "[]" ""))]
    (and column-meta
         (= expected-type (:type column-meta))
         (= (if required? "NO" "YES") (:is-nullable? column-meta)))))

(defn- scalar-columns-exist?
  ([table-name fields columns-meta db]
   (let [scalar-fields (filter #(scalar-types (% 1)) fields)
         built-in-fields [["createdAt" "Timestamp" false true]
                          ["updatedAt" "Timestamp" false false]]
         res (map #(do [%1 (scalar-column-exists? columns-meta %1)]) (concat scalar-fields built-in-fields))
         failures (remove second res)]
     (when (not-empty failures)
       (js/console.error (format "ERROR: Backing columms missing or misconfigured for table %s: %s"
                                 table-name (map #(-> % first first) failures))))
     (empty? failures)))
  ([parsed db]
   (p/alet [entities (:objects parsed)
            promises (map #(p/alet [table-name (->camelCase (pluralize (name (%1 0))))
                                    columns-meta (p/await (get-columns-for-table table-name db))]
                                   (scalar-columns-exist? table-name (%1 1) columns-meta db)) entities)
            res (p/await (p/all promises))]
           (every? identity res))))
