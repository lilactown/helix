(ns helix.analyzer
  (:require [clojure.walk]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [cljs.analyzer.api :as ana]))


;;
;; -- Props
;;

(defn clj->js-obj
  [m & {:keys [kv->prop]
        :or {kv->prop (fn [k v] [(name k) v])}}]
  {:pre [(map? m)]}
  (list* (reduce-kv (fn [form k v]
                      `(~@form ~@(kv->prop k v)))
                    '[cljs.core/js-obj]
                    m)))

#_(clj->props '{:a 1 :b {:foo bar}
                :on-click (fn [e] (js/alert "hi"))})


;;
;; -- Hooks
;;


;; TODO:
;; - Detect custom hooks
;; - Handle re-ordering
;;   - Detect hooks used in let-bindings and add left-hand side to signature
;;   - ???

(defn- find-all
  "Recursively walks a tree structure and finds all elements
  that match `pred`. Returns a vector of results."
  [pred tree]
  (let [results (atom [])]
    (clojure.walk/postwalk
     (fn walker [x]
       (when (pred x)
         (swap! results conj x))
       x)
     tree)
    @results))

(defn hook?
  [x]
  (when (list? x)
    (let [fst (first x)]
      (and (symbol? fst) (string/starts-with? (name fst) "use")))))

(defn find-hooks
  [body]
  (find-all hook? body))


(defn seqable-zip [root]
  (zip/zipper (every-pred (comp not string?)
                          (comp not #(and (sequential? %) (string/starts-with?
                                                           (name (first %))
                                                           "use")))
                          sequential?)
              seq
              (fn [_ c] c)
              root))

(comment
  (def example '[foo (fn foo [x] "foo")
                 bar {:a 1}
                 baz "baz"])

  (def z (seqable-zip example))

  (defn prn-all-nodes [loc]
    (if (zip/end? loc)
      (zip/root loc)
      (do (prn (zip/node loc))
          (recur (zip/next loc)))))

  (prn-all-nodes z)

  (-> z zip/next zip/next
      (zip/edit (fn [node] `(use-callback ~node)))
      (zip/next)
      (zip/next)
      (zip/edit (fn [node] `(use-memo ~node)))
      (zip/next)
      (zip/next)
      (zip/root)
      #_(zip/node)
      (->> (into [])))

  (ana/analyze (ana/empty-env) example))
