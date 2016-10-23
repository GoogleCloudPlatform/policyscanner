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

package com.google.cloud.security.scanner.common;

/** Constants that are used by this application */
public final class Constants {
  // format strings
  public static final String SINK_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss";
  public static final String SINK_NAME_FORMAT = "{0}-{1}-{2}";

  // output file labels
  public static final String OUTPUT_LABEL_ENFORCER = "enforcer";
  public static final String OUTPUT_LABEL_SCANNER = "scanner";
  public static final String OUTPUT_LABEL_OUTSTANDING = "outstanding";
}