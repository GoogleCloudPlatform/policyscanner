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
import java.util.Map;

public class FindUnmatchedStates
  extends DoFn<KV<GCPResource, KV<StateSource, GCPResourceState>>,
      KV<String, GCPResource>> {

  private PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view;

  public FindUnmatchedStates(
      PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view) {
    this.view = view;
  }

  /**
   * For each state (could either be a known good or live state), see whether it
   * exists in the sideInput. If not, it's an unmatched state and should be
   * included in the output.
   *
   * @param context the ProcessContext object containing information about the state
   */
  @Override
  public void processElement(ProcessContext context) {
    GCPResource resource = context.element().getKey();
    KV<StateSource, GCPResourceState> mainValue = context.element().getValue();

    if (!context.sideInput(this.view).containsKey(resource)) {
      context.output(KV.of(mainValue.getKey().toString(), resource));
    }
  }
}