;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns app.main.ui.shapes.svg-raw
  (:require
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.geom.shapes :as gsh]
   [app.main.ui.shapes.attrs :as usa]
   [app.util.data :as d]
   [app.util.object :as obj]
   [cuerdas.core :as str]
   [rumext.alpha :as mf]))

(defn clean-attrs
  "Transforms attributes to their react equivalent"
  [attrs]
  (letfn [(transform-key [key]
            (-> (name key)
                (str/replace ":" "-")
                (str/camel)
                (keyword)))

          (format-styles [style-str]
            (->> (str/split style-str ";")
                 (map str/trim)
                 (map #(str/split % ":"))
                 (group-by first)
                 (map (fn [[key val]]
                        (vector
                         (transform-key key)
                         (second (first val)))))
                 (into {})))

          (map-fn [[key val]]
            (cond
              (= key :style) [key (format-styles val)]
              :else (vector (transform-key key) val)))]

    (->> attrs
         (map map-fn)
         (into {}))))

(defn vbox->rect
  "Converts the viewBox into a rectangle"
  [vbox]
  (when vbox
    (let [[x y width height] (map d/parse-float (str/split vbox " "))]
      {:x x :y y :width width :height height})))

(defn vbox-center [shape]
  (let [vbox-rect (-> (get-in shape [:content :attrs :viewBox] "0 0 100 100")
                      (vbox->rect))]
    (gsh/center-rect vbox-rect)))

(defn vbox-bounds [shape]
  (let [vbox-rect (-> (get-in shape [:content :attrs :viewBox] "0 0 100 100")
                      (vbox->rect))
        vbox-center (gsh/center-rect vbox-rect)
        transform (gsh/transform-matrix shape nil vbox-center)]
    (-> (gsh/rect->points vbox-rect)
        (gsh/transform-points vbox-center transform)
        (gsh/points->rect))) )

(defn transform-viewbox [shape]
  (let [center (vbox-center shape)
        bounds (vbox-bounds shape)
        {:keys [x y width height]} (gsh/center->rect center (:width bounds) (:height bounds))]
    (str x " " y " " width " " height)))

(defn svg-raw-shape [shape-wrapper]
  (mf/fnc svg-raw-shape
    {::mf/wrap-props false}
    [props]
    (let [frame  (unchecked-get props "frame")
          shape  (unchecked-get props "shape")
          childs (unchecked-get props "childs")

          {:keys [tag attrs] :as content} (:content shape)

          attrs  (obj/merge! (clj->js (clean-attrs attrs))
                             (usa/extract-style-attrs shape))]

      (cond
        ;; Root SVG TAG
        (and (map? content) (= tag :svg))
        (let [;; {:keys [x y width height]} (-> (:points shape) gsh/points->selrect)
              {:keys [x y width height]} shape
              attrs (-> attrs
                        (obj/set! "x" x)
                        (obj/set! "y" y)
                        (obj/set! "width" width)
                        (obj/set! "height" height)
                        (obj/set! "preserveAspectRatio" "none")
                        #_(obj/set! "viewBox" (transform-viewbox shape)))]

          [:g.svg-raw {:transform (gsh/transform-matrix shape)}
           [:> "svg" attrs
            (for [item childs]
              [:& shape-wrapper {:frame frame
                                 :shape item
                                 :key (:id item)}])]])

        ;; Other tags different than root
        (map? content)
        [:> (name tag) attrs
         (for [item childs]
           [:& shape-wrapper {:frame frame
                              :shape item
                              :key (:id item)}])]

        ;; String content
        (string? content) content

        :else nil))))

