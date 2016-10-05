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

package com.google.cloud.security.scanner.primitives;

/** Implemented by all resources that have IAM policies. */
public interface PoliciedObject extends GCPResource {

  /**
   * Get the IAM policy binding the object.
   * @return The GCPResourcePolicy object which represents the policy binding the object.
   * @throws Exception Thrown if there's an error reading the policy.
   */
  public GCPResourcePolicy getPolicy() throws Exception;

  /**
   * Set the IAM policy binding the object.
   * @param gcpResourcePolicy The policy the object is to be bound to.
   * @throws Exception Thrown if there's an error reading the policy.
   */
  public void setPolicy(GCPResourcePolicy gcpResourcePolicy) throws Exception;
}
