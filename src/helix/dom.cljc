(ns helix.dom
  (:refer-clojure :exclude [map meta time])
  (:require
   [helix.core :as hx]
   [helix.impl.props :as impl.props])
  #?(:cljs (:require-macros [helix.dom])))

(declare
 input textarea option select a abbr address area article aside audio b base bdi
 bdo big blockquote body br button canvas caption cite code col colgroup data datalist
 dd del details dfn dialog div dl dt em embed fieldset figcaption figure footer form
 h1 h2 h3 h4 h5 h6 head header hr html i iframe img ins kbd keygen label legend li link
 main map mark menu menuitem meta meter nav noscript object ol optgroup output p param
 picture pre progress q rp rt ruby s samp script section small source span strong style
 sub summary sup table tbody td tfoot th thead time title tr track u ul var video wbr
 circle clipPath ellipse g line mask path pattern polyline rect svg text defs
 linearGradient polygon radialGradient stop tspan)

(def tags
  '[input textarea option select

    a
    abbr
    address
    area
    article
    aside
    audio
    b
    base
    bdi
    bdo
    big
    blockquote
    body
    br
    button
    canvas
    caption
    cite
    code
    col
    colgroup
    data
    datalist
    dd
    del
    details
    dfn
    dialog
    div
    dl
    dt
    em
    embed
    fieldset
    figcaption
    figure
    footer
    form
    h1
    h2
    h3
    h4
    h5
    h6
    head
    header
    hr
    html
    i
    iframe
    img
    ins
    kbd
    keygen
    label
    legend
    li
    link
    main
    map
    mark
    menu
    menuitem
    meta
    meter
    nav
    noscript
    object
    ol
    optgroup
    output
    p
    param
    picture
    pre
    progress
    q
    rp
    rt
    ruby
    s
    samp
    script
    section
    small
    source
    span
    strong
    style
    sub
    summary
    sup
    table
    tbody
    td
    tfoot
    th
    thead
    time
    title
    tr
    track
    u
    ul
    var
    video
    wbr

    ;; svg
    circle
    clipPath
    ellipse
    g
    line
    mask
    path
    pattern
    polyline
    rect
    svg
    text
    defs
    linearGradient
    polygon
    radialGradient
    stop
    tspan])


(defmacro $d
  "Creates a new React DOM element. \"type\" ought to be a string like \"span\",
  \"div\",etc.

  When a map of props are passed as the second argument, will statically convert
  to a JS object, specially handling things like converting kebab-case props to
  camelCase and other transformations.

  Use the special & or :& prop to merge dynamic props in."
  [type & args]
  (if (map? (first args))
    `^js/React.Element (.createElement
                        (hx/get-react)
                        ~type
                        (impl.props/dom-props ~(first args))
                        ~@(rest args))
    `^js/React.Element (.createElement
                        (hx/get-react)
                        ~type
                        nil
                        ~@args)))


#?(:clj (defn gen-tag
          [tag]
          `(defmacro ~tag [& args#]
             `($d ~(str '~tag) ~@args#))))

#?(:clj (defmacro gen-tags
          []
          `(do
             ~@(for [tag tags]
                 (gen-tag tag)))))

#?(:clj (gen-tags))
