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
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.HashMap;
import java.util.Map;

/**
 * Transform to filter and return only the states that are GCPResourcePolicy objects.
 */
public class FilterOutPolicies
    extends DoFn<KV<GCPResource, Map<StateSource, GCPResourceState>>,
    KV<GCPResource, Map<StateSource, GCPResourcePolicy>>> {

  @Override
  public void processElement(ProcessContext context) {
    Map<StateSource, GCPResourcePolicy> map = new HashMap<>(context.element().getValue().size());
    for (Map.Entry<StateSource, GCPResourceState> entry :
        context.element().getValue().entrySet()) {
      if (!(entry.getValue() instanceof GCPResourcePolicy)) {
        return;
      }
      map.put(entry.getKey(), (GCPResourcePolicy) entry.getValue());
    }
    context.output(KV.of(context.element().getKey(), map));
  }
}
