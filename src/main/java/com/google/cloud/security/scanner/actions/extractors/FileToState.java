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
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;

/**
 * Transform to convert (FilePath, FileContent) pairs to (GCPResource, GCPResourceState) pairs.
 */
public class FileToState
    extends DoFn<KV<List<String>, String>, KV<GCPResource, GCPResourceState>> {

  /**
   * Converts a Key-Value pair of (FilePath, FileContent) to (GCPResource, GCPResourceState).
   * The FilePath is a list of Strings which represents the location of a file.
   * The FileContent is the content of the file described by the FilePath.
   * The path is used to obtain the resource, and the content describes the state of that resource.
   * @param processContext The ProcessContext object that contains processContext-specific
   * methods and objects.
   * @throws IllegalArgumentException if the input is malformed.
   */
  @Override
  public void processElement(ProcessContext processContext) throws IllegalArgumentException {
    KV<List<String>, String> input = processContext.element();
    List<String> filePath = input.getKey();
    String fileContent = input.getValue();

    if (filePath.size() == 3 && filePath.get(2).equals(GCPResourcePolicy.getPolicyFile())) {
      // only project policies are supported for now.
      // filePath.size() must be 3 and of the form org_id/project_id/POLICY_FILE.

      GCPProject project = new GCPProject(filePath.get(1), filePath.get(0));
      Gson gson = new Gson();
      List<PolicyBinding> bindings = Arrays
          .asList(gson.fromJson(fileContent, PolicyBinding[].class));
      GCPResourceState policy = new GCPResourcePolicy(project, bindings);
      processContext.output(KV.of((GCPResource) project, policy));
    }
    else {
      throw new IllegalArgumentException("Malformed input to FileToState.");
    }
  }
}
