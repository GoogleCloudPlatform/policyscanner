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

package com.google.cloud.security.scanner.testing;

import com.google.appengine.api.utils.SystemProperty;
import com.google.cloud.dataflow.sdk.io.TextIO;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.runners.BlockingDataflowPipelineRunner;
import com.google.cloud.security.scanner.pipelines.ExportedServiceAccountKeyRemover;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handler class for the Dataflow local runner test endpoint. */
public class UserManagedKeysApp extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    PrintWriter out = resp.getWriter();
    String orgId = System.getenv("POLICY_SCANNER_ORG_ID");
    String sinkUrl = System.getenv("POLICY_SCANNER_SINK_URL");
    String dataflowTmpBucket = System.getenv("POLICY_SCANNER_DATAFLOW_TMP_BUCKET");
    String stagingLocation = "gs://" + dataflowTmpBucket + "/dataflow_tmp";
    boolean executeOnCloud = Boolean.valueOf(System.getenv("POLICY_SCANNER_EXECUTE_ON_CLOUD"));

    Preconditions.checkNotNull(orgId);
    Preconditions.checkNotNull(sinkUrl);
    Preconditions.checkNotNull(dataflowTmpBucket);

    PipelineOptions options;
    if (executeOnCloud) {
      options = getCloudExecutionOptions(stagingLocation);
    } else {
      options = getLocalExecutionOptions();
    }

    new ExportedServiceAccountKeyRemover(options, orgId)
        .attachSink(TextIO.Write.named("Write output messages").to(sinkUrl))
        .run();
    out.println("Test passed! The output was written to GCS");
  }

  private PipelineOptions getCloudExecutionOptions(String stagingLocation) {
    DataflowPipelineOptions options = PipelineOptionsFactory.as(DataflowPipelineOptions.class);
    options.setProject(SystemProperty.applicationId.get());
    options.setStagingLocation(stagingLocation);
    options.setRunner(BlockingDataflowPipelineRunner.class);
    return options;
  }

  private PipelineOptions getLocalExecutionOptions() {
    return PipelineOptionsFactory.create();
  }
}