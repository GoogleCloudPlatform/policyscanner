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
import com.google.cloud.security.scanner.primitives.GCPServiceAccount;
import com.google.cloud.security.scanner.primitives.GCPServiceAccountKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Transform to list service accounts belonging to a project.
 */
public class ListServiceAccountKeys
    extends DoFn<GCPServiceAccount, GCPServiceAccountKey> {

  /**
   * Output the list of service accounts associated with a project.
   * @param processContext The ProcessContext object that contains
   * context-specific methods and objects.
   * @throws IOException Thrown when there's an error reading from the API.
   * @throws GeneralSecurityException Thrown when there's an error reading from the API.
   */
  @Override
  public void processElement(ProcessContext processContext)
      throws IOException, GeneralSecurityException {
    GCPServiceAccount account = processContext.element();
    for (GCPServiceAccountKey key : account.getKeys()) {
      processContext.output(key);
    }
  }
}
