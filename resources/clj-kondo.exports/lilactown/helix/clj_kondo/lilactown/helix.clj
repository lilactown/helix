(ns clj-kondo.lilactown.helix
  (:require
   [clj-kondo.hooks-api :as api]))


(defn $
  "Macro analysis for `helix.core/$` & `helix.dom/$d`."
  [{:keys [node]}]
  (let [[fn-sym & body] (-> node :children)
        [component-sym body] [(first body) (next body)]
        [old-props body] (if (api/map-node? (first body))
                           [(-> body first :children) (next body)]
                           [nil body])
        children body
        new-props (when old-props
                    (->> old-props
                         (map
                          #(if (cond-> (api/sexpr %) symbol? (= '&))
                             (api/keyword-node :&)
                             %))
                         api/map-node))
        expanded (api/list-node
                  (list* fn-sym component-sym new-props children))]
    {:node (with-meta expanded (meta node))}))


(defn dom
  "Macro analysis for `helix.dom/*`."
  [{:keys [node]}]
  (let [[fn-sym & body] (-> node :children)
        [old-props body] (if (api/map-node? (first body))
                           [(-> body first :children) (next body)]
                           [nil body])
        children body
        new-props (when old-props
                    (->> old-props
                         (map
                          #(if (cond-> (api/sexpr %) symbol? (= '&))
                             (api/keyword-node :&)
                             %))
                         api/map-node))
        expanded (api/list-node
                  (list* fn-sym new-props children))]
    {:node (with-meta expanded (meta node))}))


(defn analyze-definition
  "Macro analysis for `helix.core/defnc` and `helix.core/defnc-`."
  [{:keys [node]} definer]
  (let [[_ component-name & body] (-> node :children)
        render-children (last body)
        the-rest (butlast body)
        [docstring the-rest] (if (api/string-node? (first the-rest))
                               [(first the-rest) (rest the-rest)]
                               [nil the-rest])
        [metadata-map the-rest] (if (api/map-node? (first the-rest))
                                  [(first the-rest) (rest the-rest)]
                                  [nil the-rest])
        [argvec the-rest] (if (api/vector-node? (first the-rest))
                            [(-> the-rest first) (rest the-rest)]
                            [nil the-rest])
        opts-node (when (api/map-node? (first the-rest))
                    (first the-rest))
        metadata-map (when metadata-map
                       (-> (api/sexpr metadata-map)
                           (assoc :wrap
                                  (api/sexpr
                                   (api/list-node
                                    (list*
                                     (api/token-node '->)
                                     (api/token-node '(helix.core/fnc [] ""))
                                     (-> (api/sexpr metadata-map)
                                         :wrap
                                         (api/coerce)
                                         :children)))))))
        new-opts (if opts-node
                   (-> (api/sexpr opts-node)
                       (assoc :wrap
                              (api/sexpr
                               (api/list-node
                                (list* (api/token-node '->)
                                       (api/token-node
                                        '(helix.core/fnc [] ""))
                                       (-> (api/sexpr opts-node)
                                           :wrap
                                           (api/coerce)
                                           :children)))))
                       api/coerce
                       (with-meta (meta opts-node)))
                   opts-node)]
    (when (and opts-node (contains? (api/sexpr opts-node) :wrap))
      (prn :opts-node)
      (api/reg-finding! (assoc (meta new-opts)
                               :message ":wrap should be passed to metadata map before args"
                               :type :helix/wrap-after-args)))
    {:node (with-meta
             (api/list-node
              (list*
               (api/token-node definer)
               component-name
               (filter some?
                       [docstring metadata-map
                        (if (and opts-node (get-in (api/sexpr opts-node) [:helix/features :define-factory]))
                          (api/vector-node
                            (concat (:children argvec)
                                    [(api/token-node '&)
                                     (api/token-node '_children)]))
                          argvec)
                        new-opts render-children])))
             (meta node))}))

(defn defnc
  [{:keys [node] :as form}]
  (analyze-definition form 'defn))

(defn defnc-
  [{:keys [node] :as form}]
  (analyze-definition form 'defn-))
