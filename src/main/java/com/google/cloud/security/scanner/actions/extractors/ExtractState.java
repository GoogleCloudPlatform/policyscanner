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

package com.google.cloud.security.scanner.actions.extractors;

import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.TupleTag;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transform to convert Projects to (PoliciedObject, Policy) pairs.
 */
public class ExtractState
  extends DoFn<GCPProject, KV<GCPResource, GCPResourceState>> {

  private transient static TupleTag<String> errorOutputTag;
  private transient static final Logger LOG = Logger.getLogger(ExtractState.class.getName());

  public ExtractState() {
  }

  public ExtractState setErrorOutputTag(TupleTag<String> tag) {
    errorOutputTag = tag;
    return this;
  }

  /**
   * Convert a GCPProject to a Key-Value pair of the project and its policy.
   * @param processContext The ProcessContext object that contains processContext-specific
   * methods and objects.
   * @throws IOException Thrown when there's an error reading from the API.
   * @throws GeneralSecurityException Thrown when there's an error reading from the API.
   * @throws IllegalArgumentException Thrown when a GCPProject with a null id is encountered.
   */
  @Override
  public void processElement(ProcessContext processContext)
      throws IOException, GeneralSecurityException, IllegalArgumentException {
    GCPProject input = processContext.element();

    if (input.getId() != null) {
      GCPResourceState policy = null;
      try {
        policy = input.getPolicy();
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Error getting policy", e);
      }
      if (policy == null) {
        if (errorOutputTag != null) {
          processContext.sideOutput(errorOutputTag, input.getId());
        }
      } else {
        processContext.output(KV.of((GCPResource) input, policy));
      }
    } else {
      throw new IllegalArgumentException("Found a GCPProject with a null id.");
    }
  }
}
