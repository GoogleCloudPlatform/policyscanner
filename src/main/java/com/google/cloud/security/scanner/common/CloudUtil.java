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

import com.google.appengine.api.utils.SystemProperty;
import com.google.common.util.concurrent.RateLimiter;

public class CloudUtil {

  private static final RateLimiter adminApiRateLimiter = RateLimiter.create(Constants.ADMIN_API_MAX_QPS);

  public static boolean willExecuteOnCloud() {
    return SystemProperty.environment.value() == SystemProperty.Environment.Value.Production;
  }

  public static RateLimiter getAdminApiRateLimiter() {
    return adminApiRateLimiter;
  }
}
