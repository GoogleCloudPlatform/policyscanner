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
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;

/**
 * Transform to tag a policy object with its source.
 */
public class TagStateWithSource
  extends DoFn<KV<GCPResource, GCPResourceState>,
        KV<GCPResource, KV<StateSource, GCPResourceState>>> {

  /**
   * Represents the source of a given GCPResourceState.
   */
  public enum StateSource {
    // LIVE: The current state of the GCPResource.
    LIVE,
    // DESIRED: The ideal state of the GCPResource.
    DESIRED
  }

  private StateSource source;

  /**
   * Set the StateSource tag this transform will tag all the inputs with.
   * @param source The tag to be applied to all the inputs
   */
  public TagStateWithSource(StateSource source) {
    this.source = source;
  }

  /**
   * Tag all incoming inputs with the StateSource variable set in this transform.
   * @param processContext The ProcessContext object that contains processContext-specific
   * methods and objects.
   */
  @Override
  public void processElement(ProcessContext processContext) {
    KV<GCPResource, GCPResourceState> input = processContext.element();
    processContext.output(KV.of(input.getKey(), KV.of(this.source, input.getValue())));
  }
}
