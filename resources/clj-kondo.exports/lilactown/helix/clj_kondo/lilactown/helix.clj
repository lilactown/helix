(ns clj-kondo.lilactown.helix
  (:require
    [clj-kondo.hooks-api :as api]))


(defn $
  "Macro analysis for `helix.core/$` & `helix.dom/*`."
  [{:keys [node]}]
  (let [[fn-sym & body]      (-> node :children)
        [component-sym body] (if (api/token-node? (first body))
                               [(first body) (next body)]
                               [nil body])
        [old-props body]     (if (api/map-node? (first body))
                               [(-> body first :children) (next body)]
                               [nil body])
        children             body
        new-props            (when old-props
                               (->> old-props
                                    (map
                                      #(if (cond-> (api/sexpr %) symbol? (= '&))
                                         (api/keyword-node :&)
                                         %))
                                    api/map-node))
        expanded             (api/list-node
                               (list* fn-sym component-sym new-props children))]
    {:node expanded}))


(defn defnc
  "Macro analysis for `helix.core/defnc`."
  [{:keys [node]}]
  (let [[_ component-name & body] (-> node :children)
        render-children           (last body)
        the-rest                  (butlast body)
        [docstring the-rest]      (if (api/string-node? (first the-rest))
                                    [(first the-rest) (rest the-rest)]
                                    [nil the-rest])
        [metadata-map the-rest]   (if (api/map-node? (first the-rest))
                                    [(first the-rest) (rest the-rest)]
                                    [nil the-rest])
        [argvec the-rest]         (if (api/vector-node? (first the-rest))
                                    [(-> the-rest first) (rest the-rest)]
                                    [nil the-rest])
        opts-node                 (when (api/map-node? (first the-rest))
                                    (first the-rest))
        new-opts                  (if opts-node
                                    (-> opts-node
                                        api/sexpr
                                        (assoc :wrap
                                               (api/sexpr
                                                 (api/list-node
                                                   (list* (api/token-node '->)
                                                          (api/token-node
                                                            '(helix.core/fnc [] ""))
                                                          (-> opts-node
                                                              api/sexpr
                                                              :wrap
                                                              api/coerce
                                                              :children)))))
                                        api/coerce)
                                    opts-node)
        expanded                  (api/list-node
                                    (list* (api/token-node 'defn)
                                           component-name
                                           (filter some?
                                                   [docstring metadata-map argvec
                                                    new-opts render-children])))]
    (comment
      (-> node api/sexpr prn)
      (-> expanded api/sexpr prn))
    {:node expanded}))
