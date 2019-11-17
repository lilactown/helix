(ns helix.analyzer
  (:require [clojure.walk]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [cljs.env]
            [cljs.analyzer.api :as ana]
            [ilk.core :as ilk]))


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


(defn resolve-local-vars
  "Returns a set of symbols found in `body` that also exist in `env`."
  [env body]
  (let [sym-list (atom #{})]
    (clojure.walk/postwalk
     (fn w [x]
       (if (symbol? x)
         (do (swap! sym-list conj x)
             x)
         x))
     body)

    (->> @sym-list
         (map (:locals env))
         (filter (comp not nil?))
         (map :name)
         vec)))


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


(defn inferred-type [env x]
  (cljs.analyzer/infer-tag env (ana/analyze env x)))


(comment
  (def example '[foo (fn foo [x] "foo")
                 bar {:a 1}
                 baz "baz"])

  (ilk/inferred-type {:a 1})

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


  (let [forms  '[^:meta (fn [x] 1)
                 (fn [x] 1)]
        env cljs.env/*compiler*]
    (for [f forms]
      (cljs.analyzer/infer-tag env (ana/analyze env f))))

  (let [env (ana/empty-env)]
    (for [f example]
      [(meta f)
       (cljs.analyzer/infer-tag env (ana/analyze env f))
       f]))
  )


;; => (nil cljs.core/IMap)
