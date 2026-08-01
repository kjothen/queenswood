(ns com.repldriven.queenswood.fdb.scan
  (:import
    (com.apple.foundationdb.record EndpointType
                                   ExecuteProperties
                                   ScanProperties
                                   TupleRange)
    (com.apple.foundationdb.record.provider.foundationdb
     FDBStoreTimer$Waits)
    (com.apple.foundationdb.tuple Tuple)))

(defn- record->bytes
  [r]
  (-> r
      .getRecord
      .toByteArray))

(defn- prefix-range
  "Returns a TupleRange scoped to a prefix tuple."
  [prefix-tuple]
  (TupleRange/allOf prefix-tuple))

(defn- cursor-tuple
  "Builds a cursor Tuple from prefix parts and a cursor value. The
  cursor is the whole primary key past the prefix, so a scalar and a
  vector both have to widen the prefix correctly."
  [prefix cursor]
  (let [parts (into (vec prefix)
                    (if (sequential? cursor) cursor [cursor]))]
    (Tuple/from (into-array Object parts))))

(defn- cursor
  "The record's primary key past the prefix.

  It has to be the WHOLE tail, not the single element at `position`.
  An exclusive endpoint runs through `ByteArrayUtil.strinc`, which
  advances past every key sharing those bytes as a prefix — so
  resuming after one element of a longer key skips every remaining
  record under it. A store with several rows per that element would
  lose all but the first at each page boundary, silently.

  A one-element tail is returned as the element itself, so stores
  whose key is unique at that position keep the scalar cursor their
  callers already surface as an API page token."
  [r position]
  (let [pk (.getPrimaryKey r)
        tail (mapv #(.get pk (int %)) (range position (.size pk)))]
    (if (= 1 (count tail)) (first tail) tail)))

(defn scan-entries
  "Scans records by primary key order, returning each one with its
  key. Same options and cursor semantics as `scan`; the difference is
  the shape:

  `{:entries [{:key cursor :record bytes} ...] :before ... :after ...}`

  `:key` is the record's primary key past the prefix — the same value
  the cursor uses. It exists so callers that pair two stores can join
  on the key without deserialising the records, which keeps this
  namespace free of any knowledge of what is stored.

  `:before` is the cursor of the first record in the page (what the
  client should send back as `:before` to page *previous*). `:after`
  is the cursor of the last record — only set when more rows exist
  beyond the page — for the client to send back as `:after` to page
  *next*. Both are phrased in the client's display direction, so
  `page[after]` / `page[before]` always mean next / prev regardless
  of whether the natural order is ascending or descending.

  opts:
    :prefix  vector of leading PK parts to scope the scan
    :after   cursor, client's \"next page\" boundary
    :before  cursor, client's \"previous page\" boundary
    :limit   int, page size
    :order   `:asc` (default) or `:desc` — selects the display
             direction; in `:desc` the first page (no cursor)
             returns the highest-keyed records first

  When `:prefix` is given, the scan is constrained to records whose
  PK starts with those values. A cursor is the whole PK past the
  prefix, or that element alone when only one remains."
  [store {:keys [prefix after before limit order]}]
  (let [descending? (= :desc order)
        ;; Translate client-oriented cursors into native range bounds.
        ;; In asc, `:after X` is a low exclusive bound (forward from X+ε);
        ;; `:before X` is a high exclusive bound (reverse to X-ε). In
        ;; desc, the roles swap — "next after X" now means "keys less
        ;; than X", and "prev before X" means "keys greater than X".
        low-cursor (if descending? before after)
        high-cursor (if descending? after before)
        ;; Scan backward when the natural traversal opposes key order:
        ;; asc + `:before` (paginating back from a higher cursor), or
        ;; desc without a low-cursor (default desc scan runs from the
        ;; end down).
        reverse-scan? (if descending?
                        (nil? low-cursor)
                        (some? high-cursor))
        prefix-size (count (or prefix []))
        prefix-tuple (when (seq prefix)
                       (Tuple/from (into-array Object prefix)))
        base-range (when prefix-tuple
                     (prefix-range prefix-tuple))
        range (cond
               (and prefix-tuple low-cursor)
               (TupleRange.
                (cursor-tuple prefix low-cursor)
                (.getHigh ^TupleRange base-range)
                EndpointType/RANGE_EXCLUSIVE
                (.getHighEndpoint ^TupleRange
                                  base-range))

               (and prefix-tuple high-cursor)
               (TupleRange.
                (.getLow ^TupleRange base-range)
                (cursor-tuple prefix high-cursor)
                (.getLowEndpoint ^TupleRange
                                 base-range)
                EndpointType/RANGE_EXCLUSIVE)

               prefix-tuple
               base-range

               low-cursor
               (TupleRange.
                (cursor-tuple nil low-cursor)
                nil
                EndpointType/RANGE_EXCLUSIVE
                EndpointType/TREE_END)

               high-cursor
               (TupleRange.
                nil
                (cursor-tuple nil high-cursor)
                EndpointType/TREE_START
                EndpointType/RANGE_EXCLUSIVE)

               :else
               TupleRange/ALL)
        execute-props (-> (ExecuteProperties/newBuilder)
                          (.setReturnedRowLimit (inc limit))
                          .build)
        scan-props (ScanProperties. execute-props reverse-scan?)
        raw (->> (.scanRecords store
                               ^TupleRange range
                               nil
                               ^ScanProperties scan-props)
                 .asList
                 (.asyncToSync
                  (.getContext store)
                  FDBStoreTimer$Waits/WAIT_SCAN_RECORDS)
                 vec)
        more? (> (count raw) limit)
        trimmed (cond-> raw more? (subvec 0 limit))
        ;; Native scan produces records low-to-high on forward and
        ;; high-to-low on reverse. Flip only when the scan direction
        ;; disagrees with the display direction.
        page (if (= reverse-scan? descending?)
               trimmed
               (vec (rseq trimmed)))]
    {:entries (mapv (fn [r]
                      {:key (cursor r prefix-size)
                       :record (record->bytes r)})
                    page)
     :before (when (seq page)
               (cursor (first page) prefix-size))
     :after (when more?
              (cursor (peek page) prefix-size))}))

(defn scan
  "Scans records by primary key order. Options and cursor semantics
  are `scan-entries`'; this returns
  `{:records [bytes ...] :before cursor|nil :after cursor|nil}`, with
  `:records` in the requested display order."
  [store opts]
  (let [{:keys [entries before after]} (scan-entries store opts)]
    {:records (mapv :record entries) :before before :after after}))
