(ns helix.hooks
  #?(:clj (:require [helix.impl.analyzer :as hana])
     :cljs (:require
             ["react" :as react]
             [goog.object :as gobj]))
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
                                      ;; `u` is guaranteed to be stable so we elide it
                                      #js [])]
       [v updater])))


#?(:cljs
   (defn use-ref
     "Just like react/useRef. Supports accessing the \"current\" property via
  dereference (@) and updating the \"current\" property via `reset!` and `swap!`"
     [x]
     (let [ref (react/useRef nil)]
       (when (nil? (.-current ^js ref))
         (set! (.-current ^js ref)
               (specify! #js {:current x}
                 IDeref
                 (-deref [this]
                   (.-current ^js this))

                 IReset
                 (-reset! [this v]
                   (gobj/set this "current" v))

                 ISwap
                 (-swap!
                   ([this f]
                    (gobj/set this "current" (f (.-current ^js this))))
                   ([this f a]
                    (gobj/set this "current" (f (.-current ^js this) a)))
                   ([this f a b]
                    (gobj/set this "current" (f (.-current ^js this) a b)))
                   ([this f a b xs]
                    (gobj/set this "current" (apply f (.-current ^js this) a b xs)))))))
       (.-current ^js ref))))


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


(defn simple-body? [body]
  (and (= (count body) 1) (symbol? (first body))))


#?(:clj
   (defn deps-macro-body [env deps body deps->hook-body]
     (cond
       ;;
       ;; Warn on typical errors writing body
       ;;

       ;; a single symbol
       (and (= (count body) 1) (symbol? (first body)))
       (do (hana/warn hana/warning-simple-body env {:form body})
           nil)


       ;;
       ;; Different variations of deps
       ;;

       ;; deps are passed in as a vector
       (vector? deps) (deps->hook-body `(cljs.core/array ~@deps)
                                       body)

       ;; auto deps is passed in
       (= deps :auto-deps) (deps->hook-body
                            `(cljs.core/array ~@(hana/resolve-local-vars env body))
                            body)

       ;; always fire it (don't pass any deps in to hook)
       (= deps :always) (deps->hook-body body)

       ;; pass an empty array for things that should only run once
       (= deps :once) (deps->hook-body '(cljs.core/array) body)

       :else (deps->hook-body `(determine-deps ~deps) body)))

   :cljs (defn determine-deps [deps]
           (case deps
             :once (array)
             :always js/undefined
             :auto-deps (throw (js/Error. "Cannot use :auto-deps outside of macro."))
             (to-array deps))))


(defmacro use-effect
  [deps & body]
  (deps-macro-body
   &env deps body
   (fn
     ([fn-body] `^clj-nil (raw-use-effect (wrap-fx (fn [] ~@fn-body))))
     ([deps fn-body]
      `^clj-nil (raw-use-effect (wrap-fx (fn [] ~@fn-body)) ~deps)))))


#?(:cljs
   ;; we provide a CLJS defn in the case when we need to pass around the hook
   ;; as a value. This will be slower, `:auto-deps` won't work and devtools will
   ;; be harder to read
   (defn use-effect*
     ([f] (react/useEffect (wrap-fx f)))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-effect*`; use `use-effect` macro for that"))))
      (react/useEffect (wrap-fx f) (to-array deps)))))


(defmacro use-layout-effect [deps & body]
  (deps-macro-body
   &env deps body
   (fn
     ([fn-body] `^clj-nil (raw-use-layout-effect (wrap-fx (fn [] ~@fn-body))))
     ([deps fn-body]
      `^clj-nil (raw-use-layout-effect (wrap-fx (fn [] ~@fn-body)) ~deps)))))


#?(:cljs
   (defn use-layout-effect*
     ([f] (react/useLayoutEffect (wrap-fx f)))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-layout-effect*`; use `use-layout-effect` macro for that"))))
      (react/useLayoutEffect (wrap-fx f) (to-array deps)))))


(defmacro use-memo
  [deps & body]
  (deps-macro-body
   &env deps body
   (fn
     ([fn-body]
      (vary-meta
        `(raw-use-memo (fn [] ~@fn-body))
        merge
        {:tag (hana/inferred-type &env fn-body)}))
     ([deps fn-body]
      (vary-meta
        `(raw-use-memo (fn [] ~@fn-body)
                       ~deps)
        merge
        {:tag (hana/inferred-type &env (last fn-body))})))))


#?(:cljs
   (defn use-memo*
     ([f] (react/useMemo f))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-memo*`; use `use-memo` macro for that"))))
      (react/useMemo f (to-array deps)))))


(defmacro use-callback
  [deps & body]
  (deps-macro-body
   &env deps body
   (fn
     ([fn-body] `^function (raw-use-callback ~@fn-body))
     ([deps fn-body] `^function (raw-use-callback ~@fn-body
                                                  ~deps)))))

#?(:cljs
   (defn use-callback*
     ([f] (react/useCallback f))
     ([f deps]
      (when js/goog.DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-callback*`; use `use-callback` macro for that"))))
      (react/useCallback f (to-array deps)))))


(defmacro use-imperative-handle
  [ref deps & body]
  (deps-macro-body
   &env deps body
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
          (throw (js/Error. "Can't use `:auto-deps` with `use-imperative-handle*`; use `use-imperative-handle` macro for that"))))
      (react/useImperativeHandle ref f (to-array deps)))))


#?(:cljs
   (def use-debug-value
     "just react/useDebugValue"
     react/debugValue))

