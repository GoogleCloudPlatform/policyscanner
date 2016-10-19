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

package com.google.cloud.security.scanner.actions.messengers;

import com.google.cloud.dataflow.sdk.transforms.Aggregator;
import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.transforms.Sum;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.Map;

/**
 * Transform to fix the live state and make it match the desired state.
 * Additionally, it produces a message about the change made.
 */
public class DiscrepancyAutoFixMessenger
    extends DoFn<KV<GCPResource, Map<StateSource, GCPResourceState>>, String> {

  private final Aggregator<Long, Long> totalEnforcedStates = 
      createAggregator("Total enforced states", new Sum.SumLongFn());

  /**
   * Construct a notification message out of the incoming object.
   * The incoming object is a KV pair with the GCPResource as the key,
   * and a map containing the DESIRED and LIVE states.
   * The map will contain exactly 2 elements, corresponding to the two states.
   * @param processContext The ProcessContext object that contains processContext-specific methods and objects.
   * @exception Exception is thrown if the input is malformed or the desired state can't be set.
   */
  @Override
  public void processElement(ProcessContext processContext) throws Exception {
    GCPResource resource = processContext.element().getKey();
    Map<StateSource, GCPResourceState> input = processContext.element().getValue();
    if (input.size() == 2) {
      GCPResourceState liveState = input.get(StateSource.LIVE);
      GCPResourceState desiredState = input.get(StateSource.DESIRED);
      liveState.set(desiredState);
      String message = "Live state for resource " + resource.toString()+ " changed from:\n" +
          liveState.toString() + "to the desired state:\n" + desiredState.toString();
      processContext.output(message);
      totalEnforcedStates.addValue(1L);
    }
    else {
      throw new IllegalArgumentException(
          "The <StateSource, GCPResourceState> map does not contain exactly two elements."
      );
    }
  }
  
  public final Aggregator<Long, Long> getTotalEnforcedStatesAggregator() {
    return totalEnforcedStates;
  }
}
