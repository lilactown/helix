(ns helix.hooks
  "Wrapper around react hooks.

  Many functions take a `deps` argument, corresponding to their React
  equivalent.  This is an argument which can either be a a vector of
  deps or a special keyword:

  vector of deps  Use specified deps explicitly.
  :always         Re-run hook on every render, equivalent to passing no deps
                  vector to the hook.
  :once           Equivalent to using [] as the deps.
  :auto-deps      Infer the dependencies automatically from the code by finding
                  local vars.  Not available for the function form of a hook."
  #?(:clj (:require [helix.impl.analyzer :as hana])
     :cljs (:require
            ["react" :as react]
            [goog.object :as gobj]))
  #?(:cljs (:require-macros [helix.hooks])))


#?(:cljs
   (do (def raw-use-effect react/useEffect)
       (def raw-use-layout-effect react/useLayoutEffect)
       (def raw-use-memo react/useMemo)
       (def raw-use-callback react/useCallback)
       (def raw-use-imperative-handle react/useImperativeHandle)))


(defprotocol IStateUpdater
  "Protocol that marks a type as callable when passed to a use-state setter.")


#?(:cljs
   (extend-protocol IStateUpdater
     MultiFn ;; multimethods
     function))


#?(:cljs
   (defn use-state
     "Like `react/useState`, but the update function returned can be used similar
  to `swap!` if the first argument implements `IStateUpdater`.
  By default, this includes functions and multimethods.

  Example:
  ```
  (let [[state set-state] (use-state {:count 0})]
   ;; ...
   (set-state update :count inc))
  ```"
     [initial]
     (let [[v u] (react/useState initial)
           updater (react/useCallback (fn updater
                                        ([x & xs]
                                         (if (satisfies? IStateUpdater x)
                                           (updater (fn spread-updater [y]
                                                      (apply x y xs)))
                                           ;; if the first argument isn't valid
                                           ;; updater, then call `u` with it
                                           ;; ignoring other args
                                           (u x))))
                                      ;; `u` is guaranteed to be stable so we elide it
                                      #js [])]
       [v updater])))


#?(:cljs
   (defn use-ref
     "Like react/useRef. Supports accessing the \"current\" property via
     dereference (@) and updating the \"current\" property via `reset!` and
     `swap!`"
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
   (defn wrap-fx [f]
     (fn wrap-fx-return []
       (let [x (f)]
         (if (fn? x)
           x
           js/undefined)))))


(defn simple-body? [body]
  (and (= (count body) 1) (symbol? (first body))))


#?(:clj
   (defn deps-macro-body [env deps body simple-body-ok? deps->hook-body]
     (cond
       ;;
       ;; Warn on typical errors writing body
       ;;

       ;; a single symbol
       (and (= (count body) 1) (symbol? (first body)) (not simple-body-ok?))
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
  "Like react/useEffect.  See namespace doc for `deps`.  `body` should be a
     code form which will be wrapped in a function and passed to
     react/useEffect.  If it returns a function, that will be used to clean up.

     Unlike react/useEffect, only if you return a function will it be used, you
     DO NOT need to return js/undefined."
  {:style/indent :defn}
  [deps & body]
  #?(:clj
     (deps-macro-body
      &env deps body false
      (fn
        ([fn-body] `^clj-nil (raw-use-effect (wrap-fx (fn [] ~@fn-body))))
        ([deps fn-body]
         `^clj-nil (raw-use-effect (wrap-fx (fn [] ~@fn-body)) ~deps))))))


#?(:cljs
   ;; we provide a CLJS defn in the case when we need to pass around the hook
   ;; as a value. This will be slower, `:auto-deps` won't work and devtools will
   ;; be harder to read
   (defn use-effect*
     "Like react/useEffect.  See `use-effect` for details on what `f`'s return values.  See namespace doc for `deps`."
     ([f] (react/useEffect (wrap-fx f)))
     ([f deps]
      (when goog/DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-effect*`; use `use-effect` macro for that"))))
      (react/useEffect (wrap-fx f) (to-array deps)))))


(defmacro use-layout-effect
  "Like `use-effect` but instead calls react/useLayoutEffect."
  {:style/indent :defn}
  [deps & body]
  #?(:clj
     (deps-macro-body
      &env deps body false
      (fn
        ([fn-body] `^clj-nil (raw-use-layout-effect (wrap-fx (fn [] ~@fn-body))))
        ([deps fn-body]
         `^clj-nil (raw-use-layout-effect (wrap-fx (fn [] ~@fn-body)) ~deps))))))


#?(:cljs
   (defn use-layout-effect*
     "Like `use-effect*` but instead calls react/useLayoutEffect."
     ([f] (react/useLayoutEffect (wrap-fx f)))
     ([f deps]
      (when goog/DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-layout-effect*`; use `use-layout-effect` macro for that"))))
      (react/useLayoutEffect (wrap-fx f) (to-array deps)))))


(defmacro use-memo
  "Like react/useMemo.  See namespace doc for `deps`.  `body` should be a
     code form which will be wrapped in a function."
  {:style/indent :defn}
  [deps & body]
  #?(:clj
     (deps-macro-body
      &env deps body false
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
          {:tag (hana/inferred-type &env (last fn-body))}))))))


#?(:cljs
   (defn use-memo*
     "Like react/useMemo.  `f` is unchanged in meaning.  See namespace doc for
     `deps`."
     ([f] (react/useMemo f))
     ([f deps]
      (when goog/DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-memo*`; use `use-memo` macro for that"))))
      (react/useMemo f (to-array deps)))))


(defmacro use-callback
  "Like react/useCallback.  See namespace doc for `deps`.  `fn-body` should
     be a code form which returns a function."
  {:style/indent :defn}
  [deps & fn-body]
  #?(:clj
     (deps-macro-body
      &env deps fn-body true
      (fn
        ([fn-body] `^function (raw-use-callback ~@fn-body))
        ([deps fn-body] `^function (raw-use-callback ~@fn-body
                                                     ~deps))))))

#?(:cljs
   (defn use-callback*
     "`f` is a function which will be passed to react/useCallback.  See
     namespace doc for `deps`."
     ([f] (react/useCallback f))
     ([f deps]
      (when goog/DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-callback*`; use `use-callback` macro for that"))))
      (react/useCallback f (to-array deps)))))


(defmacro use-imperative-handle
  "Like react/useImperativeHandle.  `ref` is unchanged in meaning.  See
     namespace doc for `deps`.  `body` should be a code form which will be
     wrapped in a function."
  {:style/indent :defn}
  [ref deps & body]
  #?(:clj
     (deps-macro-body
      &env deps body false
      (fn
        ([fn-body] `(raw-use-imperative-handle ref (fn [] ~@fn-body)))
        ([deps fn-body] `(raw-use-imperative-handle
                          (fn [] ~@fn-body)
                          ~deps))))))


#?(:cljs
   (defn use-imperative-handle*
     "Like react/useImperativeHandle.  `ref` and `f` are unchanged in meaning.
     See namespace doc for `deps`"
     ([ref f] (react/useImperativeHandle ref f))
     ([ref f deps]
      (when goog/DEBUG
        (when (= deps :auto-deps)
          (throw (js/Error. "Can't use `:auto-deps` with `use-imperative-handle*`; use `use-imperative-handle` macro for that"))))
      (react/useImperativeHandle ref f (to-array deps)))))


#?(:cljs
   (def use-debug-value
     "Just react/useDebugValue"
     react/useDebugValue))


#?(:cljs
   (defn use-subscription
     "Hook used for safely managing subscriptions, respecting Clojure equality.

  In order to avoid removing and re-adding subscriptions each time this hook is
  called, the parameters passed to this hook should be memoized in some way–
  either by wrapping the entire params object with `use-memo` or by wrapping the
  individual callbacks with `use-callback`."
     [{:keys [get-current-value subscribe]}]
     (let [[state set-state] (react/useState
                              (fn []
                                ;; Read the current value from our subscription.
                                ;; When this value changes, we'll schedule an update with React.
                                ;; It's important to also store the hook params so that we can check for staleness.
                                ;; (See the comment in checkForUpdates() below for more info.)
                                #js {:get-current-value get-current-value
                                     :subscribe subscribe
                                     :value (get-current-value)}))]

       ;; It is important not to subscribe while rendering because this can lead to memory leaks.
       ;; (Learn more at reactjs.org/docs/strict-mode.html#detecting-unexpected-side-effects)
       ;; Instead, we wait until the commit phase to attach our handler.
       ;;
       ;; We intentionally use a passive effect (useEffect) rather than a synchronous one (useLayoutEffect)
       ;; so that we don't stretch the commit phase.
       ;; This also has an added benefit when multiple components are subscribed to the same source:
       ;; It allows each of the event handlers to safely schedule work without potentially removing an another handler.
       ;; (Learn more at https://codesandbox.io/s/k0yvr5970o)
       (react/useEffect
        (fn []
          (let [did-unsubscribe #js {:value false}
                check-for-updates
                (fn check-for-updates
                  []
                  ;; It's possible that this callback will be invoked even after being unsubscribed,
                  ;; if it's removed as a result of a subscription event/update.
                  ;; In this case, React will log a DEV warning about an update from an unmounted component.
                  ;; We can avoid triggering that warning with this check.
                  (when (not (gobj/get did-unsubscribe "value"))
                    (let [value (get-current-value)]
                      (set-state
                       (fn [prev]
                         (cond
                           ;; Ignore values from stale sources!
                           ;; Since we subscribe and unsubscribe in a passive effect,
                           ;; it's possible that this callback will be invoked for a stale (previous) subscription.
                           ;; This check avoids scheduling an update for that stale subscription.
                           (or (not= get-current-value
                                     (gobj/get prev "get-current-value"))
                               (not= subscribe
                                     (gobj/get prev "subscribe")))
                           prev

                           ;; The moment we've all been waiting for... the entire
                           ;; point of rewriting this hook in ClojureScript.
                           ;; If the value is equal under Clojure equality to the
                           ;; previous value, then return the previous value to
                           ;; preserve reference equality and allow React to bail.
                           (= value (gobj/get prev "value"))
                           prev

                           ;; return the new value
                           :else #js {:get-current-value (gobj/get prev "get-current-value")
                                      :subscribe (gobj/get prev "subscribe")
                                      :value value}))))))
                unsubscribe (subscribe check-for-updates)]
            ;; Because we're subscribing in a passive effect,
            ;; it's possible that an update has occurred between render and our effect handler.
            ;; Check for this and schedule an update if work has occurred.
            (check-for-updates)
            (fn []
              (gobj/set did-unsubscribe "value" true)
              (unsubscribe))))
        #js [get-current-value subscribe])

       (if (or
            (not= get-current-value (gobj/get state "get-current-value"))
            (not= subscribe (gobj/get state "subscribe")))
         ;; If parameters have changed since our last render,
         ;; schedule an update with its current value.
         (let [value (get-current-value)]
           (set-state #js {:get-current-value get-current-value
                           :subscribe subscribe
                           :value value})
           ;; If the subscription has been updated, we'll schedule another update with React.
           ;; React will process this update immediately, so the old subscription value won't be committed.
           ;; It is still nice to avoid returning a mismatched value though, so let's override the return value.
           value)

         ;; If parameters haven't changed, return value stored in state
         (gobj/get state "value")))))
