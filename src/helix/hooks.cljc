(ns helix.hooks
  #?(:clj (:require [cljs.analyzer.api])
     :cljs (:require
            ["react" :as react]))
  #?(:cljs (:require-macros [helix.hooks])))

#?(:cljs
   (do (def ^:private raw-use-effect react/useEffect)
       (def ^:private raw-use-layout-effect react/useLayoutEffect)
       (def ^:private raw-use-memo react/useMemo)
       (def ^:private raw-use-callback react/useCallback)
       (def ^:private raw-use-imperative-handle react/useImperativeHandle)))





#?(:cljs
   (defn use-state
     "Like `react/useState`, but the update function returned can be used similar
  to `swap!`.

  Example:
  ```
  (let [[state set-state] (use-state {:count 0})]
   ;; ...
   (set-state update :count inc))
  ```"
     [initial]
     (let [[v u] (react/useState initial)
           updater (react/useCallback (fn updater
                                        ([x] (u x))
                                        ([f & xs]
                                         (updater (fn spread-updater [x]
                                                    (apply f x xs)))))
                                      #js [u])]
       [v updater])))


#?(:cljs
   (def use-ref
     "Just react/useRef"
     react/useRef))


#?(:cljs
   (defn use-reducer
     "Just react/useReducer."
     ([reducer init-state]
      (use-reducer reducer init-state js/undefined))
     ([reducer init-state init]
      (react/useReducer
       ;; handle ifn, e.g. multi-methods
       (react/useMemo
        #(if (and (not (fn? reducer)) (ifn? reducer))
           (fn wrap-ifn [state action]
             (reducer state action))
           reducer)
        #js [reducer])
       init-state
       init))))


#?(:cljs
   (def use-context
     "Just react/useContext"
     react/useContext))


;; React `useEffect` expects either a function or undefined to be returned
#?(:cljs
   (defn- wrap-fx [f]
     (fn wrap-fx-return []
       (let [x (f)]
         (if (fn? x)
           x
           js/undefined)))))


#?(:clj
   (defn resolve-vars
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
            (map (partial cljs.analyzer.api/resolve env))
            (filter (comp not nil?))
            (map :name)
            vec))))


(defn simple-body? [body]
  (and (= (count body) 1) (symbol? (first body))))


#?(:clj
   (defn deps-macro-body [env body deps->hook-body]
     (cond
       ;; deps are passed in as a vector
       (vector? (first body)) (deps->hook-body `(cljs.core/array ~@(first body))
                                               (rest body))

       ;; auto deps is passed in
       (= (first body) :auto-deps) (deps->hook-body
                                    `(cljs.core/array ~@(resolve-vars env (rest body)))
                                    (rest body))

       ;; no deps passed in
       true (deps->hook-body body))))


(defmacro use-effect
  [& body]
  (deps-macro-body
   &env body
   (fn
     ([fn-body] `(raw-use-effect (wrap-fx (fn [] ~@fn-body))))
     ([deps fn-body]
      `(raw-use-effect (wrap-fx (fn [] ~@fn-body)) ~deps)))))


#?(:cljs
   ;; we provide a CLJS defn in the case when we need to pass around the hook
   ;; as a value. This will be slower, `:auto-deps` won't work and devtools will
   ;; be harder to read
   (defn use-effect*
     ([f] (react/useEffect (wrap-fx f)))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-effect`; use `use-fx` macro for that"))))
      (react/useEffect (wrap-fx f) (to-array deps)))))


(defmacro use-layout-effect [& body]
  (deps-macro-body
   &env body
   (fn
     ([fn-body] `(raw-use-layout-effect (wrap-fx (fn [] ~@fn-body))))
     ([deps fn-body]
      `(raw-use-layout-effect (wrap-fx (fn [] ~@fn-body)) ~deps)))))


#?(:cljs
   (defn use-layout-effect*
     ([f] (react/useLayoutEffect (wrap-fx f)))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-layout-effect`; use `use-layout-fx` macro for that"))))
      (react/useLayoutEffect (wrap-fx f) (to-array deps)))))


(defmacro use-memo
  [& body]
  (deps-macro-body
   &env body
   (fn
     ([fn-body] `(raw-use-memo (fn [] ~@fn-body)))
     ([deps fn-body] `(raw-use-memo (fn [] ~@fn-body)
                                    ~deps)))))


#?(:cljs
   (defn use-memo*
     ([f] (react/useMemo f))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-memoize`; use `use-memo` macro for that"))))
      (react/useMemo f (to-array deps)))))


(defmacro use-callback
  [& body]
  (deps-macro-body
   &env body
   (fn
     ([fn-body] `(raw-use-callback (fn [] ~@fn-body)))
     ([deps fn-body] `(raw-use-callback (fn [] ~@fn-body)
                                        ~deps)))))

#?(:cljs
   (defn use-callback*
     ([f] (react/useCallback f))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-callback`; use `use-cb` macro for that"))))
      (react/useCallback f (to-array deps)))))


(defmacro use-imperative-handle
  [ref & body]
  (deps-macro-body
   &env body
   (fn
     ([fn-body] `(raw-use-imperative-handle ref (fn [] ~@fn-body)))
     ([deps fn-body] `(raw-use-imperative-handle
                       (fn [] ~@fn-body)
                       ~deps)))))


#?(:cljs
   (defn use-imperative-handle*
     ([ref f] (react/useImperativeHandle ref f))
     ([ref f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-imperative-handle`; use `use-imp-handle` macro for that"))))
      (react/useImperativeHandle ref f (to-array deps)))))


#?(:cljs
   (def use-debug-value
     "just react/useDebugValue"
     react/debugValue))

