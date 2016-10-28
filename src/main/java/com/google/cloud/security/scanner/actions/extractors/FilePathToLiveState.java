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
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transform to convert a file path to a live version of the resource it corresponds to.
 */
public class FilePathToLiveState extends DoFn<List<String>,KV<GCPResource, GCPResourceState>> {

  private static final Logger LOG = Logger.getLogger(FilePathToLiveState.class.getName());

  /**
   * Convert the file path into the GCP resource object that it corresponds to.
   * @param processContext The ProcessContext object that contains context-specific
   * methods and objects.
   * @throws IOException Thrown when there's an error reading from the API.
   * @throws GeneralSecurityException Thrown when there's an error reading from the API.
   */
  @Override
  public void processElement(ProcessContext processContext)
      throws IOException, GeneralSecurityException {
    List<String> filePath = processContext.element();
    if (filePath.size() == 3 && filePath.get(2).equals(GCPResourcePolicy.getPolicyFile())) {
      // only project policies are supported for now.
      // filePath.size() must be 3 and of the form org_id/project_id/POLICY_FILE.

      GCPProject project = new GCPProject(filePath.get(1), filePath.get(0));
      GCPResourceState policy = null;
      try {
        policy = project.getPolicy();
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Error getting policy", e);
      }
      if (policy != null) {
        processContext.output(KV.of((GCPResource) project, policy));
      }
    }
    else {
      throw new IllegalArgumentException("Malformed input to FilePathToLiveState.");
    }
  }
}
