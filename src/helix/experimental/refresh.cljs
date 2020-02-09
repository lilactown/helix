(ns helix.experimental.refresh
  (:require ["react-refresh/runtime" :as refresh]
            [goog.object :as gobj]))


(defn inject-hook!
  []
  (refresh/injectIntoGlobalHook js/window))


;; -- Set up global register fns
(gobj/set js/window "$$Register$$" refresh/register)


(gobj/set js/window "$$Signature$$" refresh/createSignatureFunctionForTransform)


(defn refresh!
  []
  (js/console.log (refresh/performReactRefresh)))
