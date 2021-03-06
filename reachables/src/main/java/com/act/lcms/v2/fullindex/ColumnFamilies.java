/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.lcms.v2.fullindex;

import com.act.utils.rocksdb.ColumnFamilyEnumeration;

import java.util.HashMap;
import java.util.Map;

// Package private so the searcher can use this enum but nobody else.
enum ColumnFamilies implements ColumnFamilyEnumeration<ColumnFamilies> {
  /* Maps the target m/z of a particular window to the window object.  The keys aren't especially important for this
   * column family, but putting the m/z on the LHS of the map might make it slightly easier to figure out what windows
   * an index was built with. */
  TARGET_TO_WINDOW("target_mz_to_window_obj"),
  /* This just maps a single fixed key to a list of timepoint `floats` (as raw bytes).  This gives us a one-step means
   * of recovering the full list of time points without having to iterate over all the keys/values in an index.
   * TODO: consider making the timepoints and window_obj CF's symmetrical. */
  TIMEPOINTS("timepoints"),
  /* This is where the data lives.  This CF maps `long` ids to the TMzI triples (as raw bytes).  When we want to recover
   * the original readings from the scan after intersecting the ids in the m/z and time windows, this is where we look.
   */
  ID_TO_TRIPLE("id_to_triple"),
  /* This maps time points (by time point value, since they're guaranteed to be from a closed universe) to lists of
   * `long` ids (as raw bytes) that correspond to the TMzI triples that occurred at that time.  We don't have to worry
   * about FP error here, as we're just marshalling data back and forth from primitive floats to bytes. */
  TIMEPOINT_TO_TRIPLES("timepoints_to_triples"),
  /* This maps MZWindow index (aka id) to lists of `long` ids (as raw bytes) that correspond to the TMzI triples that
   * fall within that window.  We could maybe use the MZWindow's target as a key, but given that the definition (as I
   * stole it from the TraceIndexExtractor) already had an index field, I figured a MZWindow -> id -> triples list
   * structure would be easier to reason about/debug. */
  WINDOW_ID_TO_TRIPLES("windows_to_triples"),
  ;

  /* Query patterns supported by these columns:
   * * MZWindow -> TMzI id list
   * * Time (exact) -> TMzI id list
   * * TMzI id -> TMzI values
   * * Target m/z (exact) -> MZWindow (note: use iterator, could condense into a fix-keyed list like timepoints)
   * * fixed key -> All timepoints (could use iterator, but this short circuits that)
   */

  private static final Map<String, ColumnFamilies> reverseNameMap =
      new HashMap<String, ColumnFamilies>() {{
        for (ColumnFamilies cf : ColumnFamilies.values()) {
          put(cf.getName(), cf);
        }
      }};

  private String name;

  ColumnFamilies(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public ColumnFamilies getFamilyByName(String name) {
    return reverseNameMap.get(name);
  }
}
