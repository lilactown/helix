(ns helix.impl.analyzer
  (:require [clojure.walk]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [cljs.env]
            [cljs.analyzer.api :as ana-api]
            [cljs.analyzer :as ana]
            [cljs.tagged-literals])
  (:import [cljs.tagged_literals JSValue]))


(def warning-simple-body ::simple-body)

(def warning-inferred-map-props ::inferred-map-props)

(def warning-invalid-hooks-usage ::invalid-hooks-usage)

(def warning-invalid-hook-name ::invalid-hook-name)

(defn warn [warning-type env extras]
  (ana/warning warning-type env extras))

(defmethod ana/error-message warning-simple-body
  [warning-type {:keys [form] :as info}]
  (format "Got a single symbol %s as a body, expected an expression. Maybe you meant (%s)?"
          (first form) (first form)))

(defmethod ana/error-message warning-inferred-map-props
  [warning-type {:keys [form props-form] :as info}]
  (format "The inferred type of %s is a map. Did you mean to pass it in as props?
Example: ($ %s %s ...)"
          props-form
          (first form)
          '{& props-form}))

(defmethod ana/error-message warning-invalid-hooks-usage
  [warning-type {:keys [form state] :as info}]
  (format "Invalid hooks usage: %s used in %s" form (name state)))

(defmethod ana/error-message warning-invalid-hook-name
  [warning-type {:keys [form] :as info}]
  (format "Invalid hook name defined in %s" form))


(def resolve-var ana/resolve-var)


;;
;; -- Hooks
;;


(defn resolve-local-vars
  "Returns a set of symbols found in `body` that also exist in `env`."
  [env body]
  (let [sym-list (atom #{})]
    (clojure.walk/postwalk
     (fn w [x]
       (cond
         (symbol? x)
         (do (swap! sym-list conj x)
             x)

         (= (type x) JSValue)
         (.-val x)

         :else x))
     body)

    (->> @sym-list
         (map (:locals env))
         (filter (comp not nil?))
         (map :name)
         vec)))


;; TODO:
;; - Handle re-ordering
;;   - Detect hooks used in let-bindings and add left-hand side to signature

(defn hook? [x]
  (boolean
   (and (symbol? x)
        (some #(re-find % (name x))
              [#"^use\-"
               #"^use[A-Z]"]))))


(defn hook-expr?
  [x]
  (when (list? x)
    (hook? (first x))))


(defn find-hooks
  [body]
  (let [f (fn f [matches form]
            (let [form (if (= (type form) JSValue)
                         (.-val form)
                         form)]
              (if (and (seqable? form)
                       (not
                        ;; Ignore quoted forms, e.g. '(use-foo)
                        (and
                         (list? form)
                         (= 'quote (first form)))))
                (cond-> (reduce
                         (fn [acc x]
                           (f acc x))
                         matches
                         form)
                  (hook-expr? form)
                  (conj form))
                matches)))]
    (f
     []
     body)))


(defn inferred-type [env x]
  (cljs.analyzer/infer-tag env (ana-api/no-warn (ana-api/analyze env x))))


(defn ensure-seq [x]
  (if (seqable? x) x (list x)))


(defn invalid-hooks-usage
  ([form] (invalid-hooks-usage
           {:state nil}
           form))
  ([ctx form]
   (cond
     ;; hook symbol passed in as a value
     (and (not (nil? (:state ctx)))
          (hook? form))
     (assoc ctx :form form)

     ;; otherwise, not a hook or not in a bad state, bail early
     (not (and (seqable? form) (seq form)))
     nil

     :else
     (let [hd (first form)]
       (->> (cond
              ;; Ignore quoted forms, e.g. '(use-foo)
              ('#{quote} hd)
              nil
              ;;
              ;; -- Loops
              ;;

              ;; loops that have some initial binding expression that is run
              ;; once, then a loop body
              ('#{for loop doseq dotimes} hd)
              (let [[bindings-expr & body] (rest form)]
                (->> body
                     ;; check body
                     (map #(invalid-hooks-usage
                            (assoc ctx :state :loop)
                            %))
                     ;; handle bindings-expr
                     (concat (map #(invalid-hooks-usage ctx %) bindings-expr))))

              ;; seq operations and other fns that take f as first param
              ('#{reduce reduce-kv map mapv filter filterv trampoline
                  reductions partition-by group-by map-indexed keep mapcat
                  run! keep-indexed remove some iterate} hd)
              ;; TODO make sure we handle `->>`
              (let [[f-form seq-form] (rest form)
                    ;; forms could be either a symbol or an expression
                    f-form (ensure-seq f-form)
                    seq-form (ensure-seq seq-form)]
                (->> f-form
                     ;; f-form
                     (map #(invalid-hooks-usage (assoc ctx :state :loop) %))
                     ;; seq-form
                     (concat (map #(invalid-hooks-usage ctx %) seq-form))))

              ;; ;; lazy seq macros
              ('#{lazy-seq} hd)
              (->> (rest form)
                   (map #(invalid-hooks-usage (assoc ctx :state :loop) %)))

              ;; weird ones
              ;; tree-seq
              ('#{tree-seq} hd)
              (let [[branch?-form children-form root-form] (rest form)
                    branch?-form (ensure-seq branch?-form)
                    children-form (ensure-seq children-form)
                    root-form (ensure-seq root-form)]
                (concat
                 (map #(invalid-hooks-usage (assoc ctx :state :loop) %) branch?-form)
                 (map #(invalid-hooks-usage (assoc ctx :state :loop) %) children-form)
                 (map #(invalid-hooks-usage ctx %) root-form)))
              ;; ('#{juxt})
              ;; ('#{sort-by})
              ;; ('#{repeatedly})
              ;; ('#{amap areduce})

              ;;
              ;; -- Conditionals
              ;;

              ;; conditionals which have a predicate or expression that is
              ;; always run in the first arg
              ('#{if if-not when when-not case and or
                  if-let when-let if-some when-some cond} hd)
              (let [[pred-expr & body] (rest form)]
                (->> body
                     ;; handle branches
                     (map #(invalid-hooks-usage
                            (assoc ctx :state :conditional)
                            %))
                     ;; handle pred/expr
                     (cons (invalid-hooks-usage ctx pred-expr))))

              ;; `cond->` always runs the first expr and the first predicate
              ('#{cond->} hd)
              (let [[expr first-pred & body] (rest form)]
                (->> body
                     ;; handle branches
                     (map #(invalid-hooks-usage
                            (assoc ctx :state :conditional)
                            %))
                     ;; handleexpr
                     (cons (invalid-hooks-usage ctx expr))
                     ;; the first pred is always run, so we can use hooks in it
                     (cons (invalid-hooks-usage ctx first-pred))))

              ;; `condp` always runs the second expr and the first predicate
              ('#{condp} hd)
              (let [[pred expr first-pred & body] (rest form)]
                (->> body
                     ;; handle branches
                     (map #(invalid-hooks-usage
                            (assoc ctx :state :conditional)
                            %))
                     ;; hook should NOT be in `pred` as it could be called multiple times
                     (cons (invalid-hooks-usage (assoc ctx :state :conditional) pred))
                     (cons (invalid-hooks-usage ctx expr))
                     (cons (invalid-hooks-usage ctx first-pred))))

              (and (not (nil? (:state ctx)))
                   (hook-expr? form))
              ;; wrap in a seq since it's expected
              [(assoc ctx :form form)]

              :else (->> (rest form)
                         (map #(invalid-hooks-usage ctx %))))
            (flatten)
            (filter (comp not nil?))
            ;; return nil if empty
            (seq))))))



(defn make-seqable
  [node children]
  ;; ????
  (with-meta
    ;; preserve type of node
    (cond
      (vector? node) (vec children)
      :else children)
    ;; preserve meta
    (meta node)))


(defn seqable-zip
  [root]
  (zip/zipper
   #(and (seqable? %)
         (not (string? %)))
   identity
   make-seqable
   root))


(defn map-forms-with-meta
  [body meta-table ]
  (let [meta-keys (set (keys meta-table))
        body-zip (seqable-zip body)]
    (loop [cur-loc body-zip
           ;; this max-loop count is here because I accidentally blew this up
           ;; too many times w/ an infinite loop
           max-loop 10000]
      (let [node (zip/node cur-loc)
            node-meta (meta node)
            mapped-meta-value (some
                               #(when-some [meta-v (get node-meta %)]
                                  ((get meta-table %)
                                   ;; strip the metadata that triggered this to
                                   ;; avoid an infinite loop!
                                   (vary-meta node dissoc %)
                                   meta-v))
                               meta-keys)
            cur-loc' (if mapped-meta-value
                       (zip/replace cur-loc mapped-meta-value)
                       cur-loc)]
        (cond
          (zip/end? cur-loc') (zip/root cur-loc')

          (= 0 max-loop) (throw (ex-info
                                 "Infinite loop detected!"
                                 {:zipper cur-loc'
                                  :body (zip/root cur-loc')}))

          :else
          (recur (zip/next cur-loc')
                 (dec max-loop)))))))
