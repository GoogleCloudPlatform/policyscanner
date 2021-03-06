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
import com.google.cloud.security.scanner.primitives.GCPResourceErrorInfo;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transform to convert Projects to (PoliciedObject, Policy) pairs.
 */
public class ExtractState
  extends DoFn<GCPProject, KV<GCPResource, GCPResourceState>> {

  private TupleTag<GCPResourceErrorInfo> errorOutputTag;
  private transient static final Logger logger = Logger.getLogger(ExtractState.class.getName());

  public ExtractState() {
  }

  public ExtractState(TupleTag<GCPResourceErrorInfo> tag) {
    errorOutputTag = tag;
  }

  /**
   * Convert a GCPProject to a Key-Value pair of the project and its policy.
   * @param processContext The ProcessContext object that contains processContext-specific
   * methods and objects.
   */
  @Override
  public void processElement(ProcessContext processContext) {
    GCPProject input = processContext.element();

    if (input.getId() == null) {
      this.addToSideOutput(processContext, input, "Null project id");
      return;
    }
    GCPResourceState policy = null;
    String errorMsg = null;
    try {
      policy = input.getPolicy();
    } catch (Exception e) {
      errorMsg = e.getMessage();
      logger.log(Level.FINE, "Error getting policy", e);
    }

    if (policy == null) {
      this.addToSideOutput(processContext, input, String.format("Policy error %s", errorMsg));
    } else {
      processContext.output(KV.of((GCPResource) input, policy));
    }
  }

  /**
   * Add some error output to the side output tag
   * @param context the ProcessContext for this DoFn
   * @param project the project associated with the error
   * @param errorMessage the message describing the error
   */
  private void addToSideOutput(ProcessContext context, GCPProject project, String errorMessage) {
    if (errorOutputTag != null) {
      context.sideOutput(errorOutputTag,
          new GCPResourceErrorInfo(project, errorMessage));
    }
  }
}
