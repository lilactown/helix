(ns clj-kondo.lilactown.helix
  (:require [clj-kondo.hooks-api :as api]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn $
  "Macro analysis for `helix.core/$`."
  [{:keys [node]}]
  (let [[dollar-sym component-sym & body] (-> node :children)
        old-props                         (-> body first :children)
        children                          (-> body rest)
        new-props                         (->> old-props
                                               (map #(if (cond-> (api/sexpr %) symbol? (= '&))
                                                       (api/keyword-node :&)
                                                       %)))
        expanded                          (api/list-node (list*
                                                          dollar-sym
                                                          component-sym
                                                          (api/map-node new-props)
                                                          children))]

    {:node expanded}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
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
        ;; wrap-opts                 (if opts-node  opts-node)
        ;; new-wrap-opts             (if opts-node  wrap-opts)
        new-opts                  (if opts-node
                                    (-> opts-node api/sexpr
                                        (assoc :wrap (api/sexpr (api/list-node
                                                                 (list*
                                                                  (api/token-node '->)
                                                                  (api/token-node '(helix.core/fnc [] ""))
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
    {:node expanded}))
