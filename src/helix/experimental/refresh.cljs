(ns helix.experimental.refresh
  (:require ["react-refresh/runtime" :as refresh]
            [goog.object :as gobj]))



;; -- Set up global register fns
(gobj/set js/window "$$Register$$" refresh/register)


(gobj/set js/window "$$Signature$$" refresh/createSignatureFunctionForTransform)


(defn inject-hook! []
  (refresh/injectIntoGlobalHook js/window))


(defn refresh! []
  (js/console.log (refresh/performReactRefresh)))
