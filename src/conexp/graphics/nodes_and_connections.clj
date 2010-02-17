(ns conexp.graphics.nodes-and-connections
  (:use [conexp.util :only (update-ns-meta!)]
	[conexp.base :only (defvar-, defvar, round)]
	conexp.graphics.util
	conexp.graphics.scenes
	[clojure.contrib.core :only (-?>)])
  (:import [java.awt Color]
	   [no.geosoft.cc.graphics GWindow GScene GObject GSegment
	                           GStyle GInteraction ZoomInteraction
	                           GText GPosition]))


(update-ns-meta! conexp.graphics.nodes-and-connections
  :doc "Namespace for representing nodes and their connections for drawing lattice diagrams.")


;;; nodes and connections

(defn node?
  "Tests whether thing is a node of a lattice diagram or not."
  [thing]
  (and (instance? GObject thing)
       (= (:type @(.getUserData #^GObject thing)) :node)))

(defn connection?
  "Tests whether thing is a connection of a lattice diagram or not."
  [thing]
  (and (instance? GObject thing)
       (= (:type @(.getUserData #^GObject thing)) :connection)))

(defn position
  "Returns the position of a node in a lattice diagram."
  [#^GObject node]
  (:position @(.getUserData node)))

(defn radius
  "Returns the radius of a node in a lattice diagram."
  [#^GObject node]
  (:radius @(.getUserData node)))

(defn set-node-radius!
  "Sets radius of node."
  [#^GObject node, radius]
  (dosync
   (alter (.getUserData node) assoc :radius radius)))

(defn get-name
  "Returns name of thing."
  [#^GObject thing]
  (:name @(.getUserData thing)))

(defn lower-node
  "Returns for a connection conn the lower node in a lattice diagram."
  [#^GObject conn]
  (:lower @(.getUserData conn)))

(defn upper-node
  "Returns for a connection conn the upper node in a lattice diagram."
  [#^GObject conn]
  (:upper @(.getUserData conn)))

(defn upper-connections
  "Returns all upper connections for node in a lattice diagram."
  [#^GObject node]
  (:upper @(.getUserData node)))

(defn lower-connections
  "Returns all lower connections of node in a lattice diagram."
  [#^GObject node]
  (:lower @(.getUserData node)))

(defn upper-neighbors
  "Returns all upper neighbors of node in a lattice diagram."
  [node]
  (map upper-node (upper-connections node)))

(defn lower-neighbors
  "Returns all lower neighbors of node in a lattice diagram."
  [node]
  (map lower-node (lower-connections node)))


;;; create and connect nodes

(defvar- *default-node-style* (doto (GStyle.)
				(.setForegroundColor Color/BLACK)
				(.setBackgroundColor Color/WHITE)
				(.setLineWidth 1.0))
  "Default node style for lattice diagrams.")

(defvar- *default-object-concept-style* (doto (GStyle.)
					  (.setBackgroundColor Color/BLACK))
  "Default style for nodes being an object concept.")

(defvar- *default-attribute-concept-style* (doto (GStyle.)
					     (.setBackgroundColor Color/BLUE))
  "Default style for nodes being an attribute concept.")

(defvar- *default-node-label-style* (doto (GStyle.)
				      (.setBackgroundColor Color/WHITE))
  "Default style for node labels.")

(defn- create-two-halfcircles
  "Creates points for two half circles, lower circle points first."
  [x y radius]
  (let [x (double x),
	y (double y),
	radius (double radius),
	samples 100,
	angle (/ Math/PI samples),
	[upper-points, lower-points] (loop [current-angle (double (- (/ Math/PI 2.0))),
					    acc-samples 0,
					    upper-points [],
					    lower-points []]
				       (if (> acc-samples samples)
					 [upper-points, lower-points]
					 (let [new-x (+ x (* radius (Math/sin current-angle))),
					       new-y (+ y (* radius (Math/cos current-angle)))]
					   (recur (double (+ current-angle angle))
						  (inc acc-samples)
						  (conj upper-points new-x new-y)
						  (conj lower-points new-x (+ y y (- new-y)))))))]
    [(into-array Double/TYPE lower-points), (into-array Double/TYPE upper-points)]))

(defvar *default-node-radius* 5.0
  "Initial node radius when drawing lattices.")

(defn- add-node
  "Adds a node to scn at position [x y]. Default name is \"[x y]\"."
  ([#^GScene scn, x, y]
     (add-node scn x y (str [x y])))
  ([#^GScene scn, x, y, name]
     (let [#^GSegment upper-segment (GSegment.),
	   #^GSegment lower-segment (GSegment.),
	   object (proxy [GObject] []
		    (draw []
		      (let [upper-style (if (= 1 (-?> this upper-neighbors count))
					  *default-attribute-concept-style*
					  nil),
			    lower-style (if (= 1 (-?> this lower-neighbors count))
					  *default-object-concept-style*
					  nil),
			    [x y] (position this),
			    [l u] (create-two-halfcircles x y (radius this))]
			(.setGeometryXy lower-segment l)
			(.setGeometryXy upper-segment u)
			(.setStyle lower-segment lower-style)
			(.setStyle upper-segment upper-style))))
	   style (GStyle.)]
       (doto object
	 (.setStyle *default-node-style*)
	 (.addSegment lower-segment)
	 (.addSegment upper-segment)
	 (.setUserData (ref {:type :node,
			     :position [(double x), (double y)],
			     :radius *default-node-radius*,
			     :name name})))
       (doto scn
	 (.add object))

       ;; testing labels
       (let [#^GText label (GText. (str name) (bit-or GPosition/NORTH GPosition/RIGHT))]
	 (.setStyle label *default-node-label-style*)
	 (.addText upper-segment label))
       ;;

       object)))

(defvar- *default-line-style* (doto (GStyle.)
				(.setLineWidth 2.0)
				(.setForegroundColor Color/BLACK))
  "Default line style.")

(defn- connect-nodes
  "Connects two nodes on scene."
  ([#^GScene scn, #^GObject x, #^GObject y]
    (connect-nodes scn x y (str (get-name x) " -> " (get-name y))))
  ([#^GScene scn, #^GObject x, #^GObject y, name]
     (let [line (GSegment.),
	   c    (proxy [GObject] []
		  (draw []
		    (let [[x1 y1] (position (lower-node this))
			  [x2 y2] (position (upper-node this))]
		      (.setGeometry line
				    (double x1) (double y1)
				    (double x2) (double y2)))))]
       (doto scn
	 (.add c))
       (doto line
	 (.setStyle *default-line-style*))
       (doto c
	 (.addSegment line)
	 (.toBack)
	 (.setUserData (ref {:type :connection,
			     :lower x,
			     :upper y,
			     :name name})))
       (dosync
	(alter (.getUserData x) update-in [:upper] conj c)
	(alter (.getUserData y) update-in [:lower] conj c)))))


;;; move nodes around

(defn- height-of-lower-neighbors
  "Returns maximal height of all lower neighbors."
  [node]
  (let [lowers (lower-neighbors node)]
    (if (empty? lowers)
      nil
      (reduce max (map (fn [node]
			 ((position node) 1))
		       lowers)))))

(defn- height-of-upper-neighbors
  "Returns minimal height of all upper neighbors."
  [node]
  (let [uppers (upper-neighbors node)]
    (if (empty? uppers)
      nil
      (reduce min (map (fn [node]
			 ((position node) 1))
		       uppers)))))

(defn move-node-unchecked-to
  "Moves node to [new-x new-y]."
  [#^GObject node, new-x, new-y]
  ;; update self position
  (dosync
   (alter (.getUserData node) assoc :position [new-x new-y]))

  ;; move node on the device
  (.redraw node)

  ;; update connections to upper neighbors
  (doseq [#^GObject c (upper-connections node)]
    (.redraw c))

  ;; update connections to lower neighbors
  (doseq [#^GObject c (lower-connections node)]
    (.redraw c))

  ;; done
  [new-x new-y])

(defn move-node-by
  "Moves node by [dx dy] making sure it will not be over some of its
  upper neighbors or under some of its lower neighbors."
  ;; race condition when move nodes too fast?
  [#^GObject node, dx, dy]
  (let [[x y] (position node),

	;; make sure nodes don't go too far
	max-y (height-of-upper-neighbors node),
	min-y (height-of-lower-neighbors node),
	dy    (if max-y (min dy (- max-y y)) dy),
	dy    (if min-y (max dy (- min-y y)) dy)]
    (move-node-unchecked-to node (+ x dx) (+ y dy))))

(defn move-node-to
  "Moves node to position [x y], restricted by it's upper and lower
  neighbours."
  [node x y]
  (let [[current-x current-y] (position node)]
    (move-node-by node (- x current-x) (- y current-y))))


;;;

(defn move-interaction
  "Standard move interaction for lattice diagrams. Installs
  :move-start, :move-drag and :move-stop hooks on scene to be called
  whenever a node is moved. Callbacks get the moved vertex as
  argument."
  [scene]
  (add-hook scene :move-start)
  (add-hook scene :move-drag)
  (add-hook scene :move-stop)
  (let [interaction-obj (atom nil)]
    (proxy [GInteraction] []
      (event [#^GScene scn, evt, x, y]
	(condp = evt
	   GWindow/BUTTON1_DOWN  (let [thing (.find scn x y)]
				   (when (node? thing)
				     (reset! interaction-obj thing)
				     (call-hook-with scn :move-start thing))),
	   GWindow/BUTTON1_DRAG  (when @interaction-obj
				   (let [[x y] (device-to-world scn x y)]
				     (move-node-to @interaction-obj x y)
				     (.refresh scn))
				   (call-hook-with scn :move-drag @interaction-obj)),
	   GWindow/BUTTON1_UP    (do
				   (call-hook-with scn :move-stop @interaction-obj)
				   (reset! interaction-obj nil))
	   nil)))))

(defn zoom-interaction
  "Standrd zoom interaction for lattice diagrams. Installs
  :zoom-event hook called whenever view changes. Callbacks
  take no arguments."
  [scene]
  (add-hook scene :zoom-event)
  (let [#^ZoomInteraction zoom-obj (ZoomInteraction. scene)]
    (proxy [GInteraction] []
      (event [#^GScene scn, evt, x, y]
	(.event zoom-obj scn evt x y)
	(when scn
	  (call-hook-with scn :zoom-event))))))

(defn add-nodes-with-connections
  "Adds to scene scn nodes placed by node-coordinate-map and connected
  via pairs in the sequence node-connections."
  [scn node-coordinate-map node-connections]
  (let [node-map (apply hash-map
			(apply concat (map (fn [[node [x y]]]
					     [node
					      (add-node scn x y node)])
					   node-coordinate-map)))]
    (doseq [[node-1 node-2] node-connections]
      (connect-nodes scn (node-map node-1) (node-map node-2)))
    node-map))


;;;

nil
