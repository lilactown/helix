(ns helix.impl.hooks)

;; React `useEffect` expects either a function or undefined to be returned
(defn wrap-fx [f]
  (fn wrap-fx-return []
    (let [x (f)]
      (if (fn? x)
        x
        js/undefined))))
