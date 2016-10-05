/**
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.security.scanner.actions.modifiers;

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.HashMap;
import java.util.Map;

/**
 * Transform to filter out matching states and leave only mismatched state configurations.
 */
public class FilterOutMatchingState
    extends DoFn<KV<GCPResource, KV<StateSource, GCPResourceState>>,
    KV<GCPResource, Map<StateSource, GCPResourceState>>> {

  private PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view;

  /**
   * Constructor for the FilterOutMatchingState DoFn.
   * @param view The PCollectionView which contains the side-input elements.
   */
  public FilterOutMatchingState
      (PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view) {
    this.view = view;
  }

  /**
   * Process an element of the type KV<GCPResource, KV<StateResource, GCPResourceState>>
   * and output only those states that do not match.
   * The GCPResource is the resource that is being described by the GCPResourceState.
   * The GCPResourceState is the attribute describing the GCPResource.
   * StateSource represents the source of the GCPResourceState:
   *  - it was either checked in as a known-good, or
   *  - it is the live state of the resource
   *  GCPResourceStates tagged with one StateSource (say, DESIRED) will be inputted through
   *  a side input, and those tagged with the other will be inputted through the main input.
   * @param context The ProcessContext object that contains context-specific methods and objects.
   */
  @Override
  public void processElement(ProcessContext context) {
    GCPResource resource = context.element().getKey();
    KV<StateSource, GCPResourceState> mainValue = context.element().getValue();

    if (context.sideInput(this.view).containsKey(resource)) {
      // make sure there's an element in the side input with the same GCPResource.

      KV<StateSource, GCPResourceState> sideValue = context.sideInput(this.view).get(resource);
      if (!mainValue.getValue().equals(sideValue.getValue())) {
        // make sure the GCPResourceStates are different.

        // the HashMap will contain two entries, one for
        // the DESIRED state and one for the LIVE state.
        Map<StateSource, GCPResourceState> mismatchedStates = new HashMap<>(2);
        mismatchedStates.put(mainValue.getKey(), mainValue.getValue());
        mismatchedStates.put(sideValue.getKey(), sideValue.getValue());
        context.output(KV.of(resource, mismatchedStates));
      }
    }
  }
}
