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

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.Map;

/**
 * Transform to produce a notification message from the differing states.
 */
public class StateDiscrepancyMessenger
    extends DoFn<KV<GCPResource, Map<StateSource, GCPResourceState>>, String> {

  /**
   * Construct a notification message out of the incoming object.
   * The incoming object is a KV pair with the GCPResource as the key,
   * and a map containing the DESIRED and LIVE states.
   * The map will contain exactly 2 elements, corresponding to the two states.
   * @param processContext The ProcessContext object that contains processContext-specific
   * methods and objects.
   * @exception IllegalArgumentException is thrown if the map does not contain exactly two elements.
   */
  @Override
  public void processElement(ProcessContext processContext) throws IllegalArgumentException {
    GCPResource resource = processContext.element().getKey();
    Map<StateSource, GCPResourceState> input = processContext.element().getValue();
    if (input.size() == 2) {
      String message = "For resource: " + resource.toString()+
          "\n\tChecked in state:\n\t\t" +
          input.get(StateSource.DESIRED).toString() +
          "\n\tActual state:\n\t\t" +
          input.get(StateSource.LIVE).toString();
      processContext.output(message);
    }
    else {
      throw new IllegalArgumentException(
          "The <StateSource, GCPResourceState> map does not contain exactly two elements."
      );
    }
  }
}
