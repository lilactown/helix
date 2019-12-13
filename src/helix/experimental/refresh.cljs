(ns helix.experimental.refresh
  (:require ["react-refresh/runtime" :as refresh]
            [goog.object :as gobj]))


(refresh/injectIntoGlobalHook js/window)


;; -- Set up global register fns
(gobj/set js/window "$$Register$$" refresh/register)


(gobj/set js/window "$$Signature$$" refresh/createSignatureFunctionForTransform)


(defn ^:dev/after-load refresh! []
  (js/console.log (refresh/performReactRefresh)))
