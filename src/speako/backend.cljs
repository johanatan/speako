
;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.backend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [speako.schema :as schema]
            [speako.common :refer [format single singularize pluralize]]
            [loom.graph :as graph]
            [loom.attr :as attr]
            [sqlingvo.core :as sql]
            [sqlingvo.node :as db :refer-macros [<? <!?]]
            [cljs.core.async :as async]
            [promesa.core :as p :refer-macros [alet]]
            [camel-snake-kebab.core :refer [->PascalCase ->camelCase ->kebab-case]]
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
                          [node typ name list? required?]))) fields)))

(defn- construct-graph [parsed]
  (let [nodes (set (keys (parsed :objects)))
        unions (set (keys (parsed :unions)))
        object-edges (partial object-edges (clojure.set/union nodes unions))
        edges (remove empty? (mapcat object-edges (parsed :objects)))
        g (apply graph/digraph (concat (map (partial take 2) edges) nodes))
        attrs (map #(let [edge (take 2 %1)] [edge (%1 2) {:list? (%1 3) :required? (%1 4)}]) edges)
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

(defn promisify
  ([query] (promisify identity query))
  ([inner-xfm query] (promisify identity inner-xfm query))
  ([outer-xfm inner-xfm query]
   (chan->promise outer-xfm (async/map inner-xfm [(db/execute (query))]))))

(defn- get-table-names [db]
  (promisify #(map :table_name %1)
             #(sql/select db [:table_name]
                          (sql/from :information_schema.tables)
                          (sql/where '(= :table_schema "public")))))

(defn- association-tables [table-names]
  (filter #(.endsWith % "Associations") table-names))

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

(defn- table->columns [table-name db]
  (let [xfm-type #(condp = %1 "ARRAY" (format "%s[]" (db-array-types-map %2)) %1)]
    (promisify
     #(into {} %)
     #(map (fn [r] [(:column_name r) {:type (xfm-type (:data_type r) (:udt_name r))
                                      :is-nullable? (:is_nullable r)}]) %1)
     #(sql/select db [:column_name :data_type :is_nullable :udt_name]
                  (sql/from :information_schema.columns)
                  (sql/where `(= :table_name ~table-name))))))

(defn- entities [graph]
  (let [nodes (graph/nodes graph)]
    (remove #(attr/attr graph % :union?) nodes)))

(defn entity->table-name [entity-kwd]
  (-> entity-kwd name pluralize ->camelCase))

(defn- tables-exist? [graph db]
  (p/alet [tables (p/await (get-table-names db))
           expected (map entity->table-name (entities graph))
           remaining (clojure.set/difference (set expected) (set tables))]
          (when (not-empty remaining)
            (js/console.error (format "ERROR: Backing tables missing: %s" (into [] remaining))))
          (empty? remaining)))

(defn- run-query [db query-fn]
  (p/alet [db (p/await (chan->promise (db/connect db)))
           res (p/await (query-fn db))
           _ (db/disconnect db)]
          res))

(defn- pprint-query [db query-fn]
  (p/map pprint (run-query db query-fn)))

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
            promises (map
                      (fn [[entity fields]]
                        (p/alet [table-name (entity->table-name entity)
                                 columns-meta (p/await (table->columns table-name db))]
                                (scalar-columns-exist? table-name fields columns-meta db))) entities)
            res (p/await (p/all promises))]
           (every? identity res))))

(defn- table->foreign-keys [table-name db]
  (promisify
   #(map (fn [r] (clojure.set/rename-keys r (into {} (map (fn [k] [k (->kebab-case k)]) (keys r))))) %1)
   #(sql/select db [:tc.constraint_name :tc.table_name :kcu.column_name
                    (sql/as :ccu.table_name :foreign_table_name)
                    (sql/as :ccu.column_name :foreign_column_name)]
                (sql/from (sql/as :information_schema.table_constraints :tc))
                (sql/join (sql/as :information_schema.key_column_usage :kcu)
                          '(on (= :tc.constraint_name :kcu.constraint_name)))
                (sql/join (sql/as :information_schema.constraint_column_usage :ccu)
                          '(on (= :ccu.constraint_name :tc.constraint_name)))
                (sql/where `(and (= :constraint_type "FOREIGN KEY") (= :tc.table_name ~table-name))))))

(defn- extract-relations [graph db]
  (p/alet [entities (entities graph)
           table-names (map #(do [(entity->table-name %1) %1]) entities)
           promises (map (fn [[t e]] (p/map #(do [e %1]) (table->foreign-keys t db))) table-names)
           res (p/await (p/all promises))]
          (into {} res)))

(defrecord Multiplicity [entity field multiplicity required?])
(defrecord Cardinality [left right])

(defn- construct-cardinality [left right fwd-attrs reverse-attrs]
  (let [mult #(cond (nil? %1) :zero (:list? %1) :many :else :one)]
    (Cardinality.
     (Multiplicity. left (:name fwd-attrs) (mult fwd-attrs) (:required? fwd-attrs))
     (Multiplicity. right (:name reverse-attrs) (mult reverse-attrs) (:required? reverse-attrs)))))

(defn- field-cardinalities [graph edge]
  (let [fwd-attrs (attr/attrs graph edge)
        reverse-attrs (attr/attrs graph (vec (reverse edge)))
        constructor (partial construct-cardinality (edge 0) (edge 1))
        attr-map #(if %1 (merge (%1 1) {:name (%1 0)}))]
    (cond
      (and (nil? fwd-attrs) (nil? reverse-attrs))
      (throw (js/Error. (format "Internal error: no attrs for edge: %s" edge)))
      :else
      (map #(construct-cardinality (edge 0) (edge 1) (attr-map (%1 0)) (attr-map (%1 1)))
           (map vector fwd-attrs (or reverse-attrs (repeat (count fwd-attrs) nil)))))))

(defn- unique-edges [graph]
  (map vec (distinct (map #(if (every? (partial = (first %1)) %1) %1 (set %1)) (graph/edges graph)))))

(defn- cardinalities [graph]
  (let [edges (unique-edges graph)]
    (mapcat (partial field-cardinalities graph) edges)))

(defn- find-single-relation [table-name column-name foreign-table-name foreign-column-name relations]
  (single (filter #(and (= (%1 :table-name) table-name)
                        (= (%1 :foreign-table-name) foreign-table-name)
                        (= (%1 :foreign-column-name) foreign-column-name)
                        (= (%1 :column-name) column-name)) relations)))

(defn- get-relation [cardinality relations tables association-tables]
  (let [[lcard rcard] [(cardinality :left) (cardinality :right)]
        [lentity rentity] [(lcard :entity) (rcard :entity)]
        [ltable rtable] [(entity->table-name lentity) (entity->table-name rentity)]
        [lrelations rrelations] [(lentity relations) (rentity relations)]]
    (condp = [(lcard :multiplicity) (rcard :multiplicity)]
      [:one :many] (find-single-relation ltable (format "%sId" (singularize rtable)) rtable "id" lrelations)
      [:many :one] (find-single-relation rtable (format "%sId" (singularize ltable)) ltable "id" rrelations))))
