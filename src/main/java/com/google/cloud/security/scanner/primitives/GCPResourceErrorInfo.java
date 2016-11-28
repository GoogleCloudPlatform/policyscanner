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

import com.google.cloud.dataflow.sdk.repackaged.com.google.common.base.Objects;
import java.io.Serializable;

public class GCPResourceErrorInfo implements Serializable {
  private GCPResource gcpResource;
  private String errorMessage;

  public GCPResourceErrorInfo(GCPResource resource, String errorMessage) {
    this.gcpResource = resource;
    this.errorMessage = errorMessage;
  }

  public GCPResource getResource() {
    return this.gcpResource;
  }

  public String getErrorMessage() {
    return this.errorMessage;
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append(String.format("resourceId:\"%s\"", this.gcpResource.getId()))
        .append(String.format(",reason:\"%s\"", this.errorMessage))
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof GCPResourceErrorInfo) {
      GCPResourceErrorInfo errorInfo = (GCPResourceErrorInfo) o;
      return Objects.equal(this.gcpResource, errorInfo.getResource())
          && Objects.equal(this.errorMessage, errorInfo.getErrorMessage());
    }
    return false;
  }
}
