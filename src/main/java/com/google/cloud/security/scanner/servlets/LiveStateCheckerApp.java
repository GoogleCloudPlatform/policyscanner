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

package com.google.cloud.security.scanner.servlets;

import com.google.appengine.api.utils.SystemProperty;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.runners.BlockingDataflowPipelineRunner;
import com.google.cloud.dataflow.sdk.runners.DataflowJobException;
import com.google.cloud.dataflow.sdk.runners.DataflowPipelineJob;
import com.google.cloud.security.scanner.common.CloudUtil;
import com.google.cloud.security.scanner.common.Constants;
import com.google.cloud.security.scanner.exceptions.PolicyScannerDataflowException;
import com.google.cloud.security.scanner.pipelines.LiveStateChecker;
import com.google.cloud.security.scanner.sources.GCSFilesSource;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handler class for the Dataflow local runner test endpoint. */
public class LiveStateCheckerApp extends HttpServlet {

  /**
   * Handler for the GET request to this app.
   * @param req The request object.
   * @param resp The response object.
   * @throws IOException Thrown if there's an error reading from one of the APIs.
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    PrintWriter out = resp.getWriter();

    Preconditions.checkNotNull(Constants.ORG_NAME);
    Preconditions.checkNotNull(Constants.ORG_ID);
    Preconditions.checkNotNull(Constants.POLICY_BUCKET);
    Preconditions.checkNotNull(Constants.OUTPUT_PREFIX);
    Preconditions.checkNotNull(Constants.DATAFLOW_STAGING);

    GCSFilesSource source = null;
    try {
      source = new GCSFilesSource(Constants.POLICY_BUCKET, Constants.ORG_NAME);
    } catch (GeneralSecurityException e) {
      throw new IOException("SecurityException: Cannot access bucket: " + e.getMessage());
    }
    PipelineOptions options;
    if (CloudUtil.willExecuteOnCloud()) {
      options = getCloudExecutionOptions(Constants.DATAFLOW_STAGING);
    } else {
      options = getLocalExecutionOptions();
    }

    String datetimestamp = new SimpleDateFormat(Constants.SINK_TIMESTAMP_FORMAT).format(new Date());
    try {
      new LiveStateChecker(options, source, Constants.ORG_ID)
          .setOutputLocationsWithPrefix(Constants.OUTPUT_PREFIX, datetimestamp)
          .build()
          .run();
    } catch (DataflowJobException de) {
      DataflowPipelineJob job = de.getJob();
      String message = "There was an error in the Dataflow job.\nYou can see the job details here: "
          + "https://console.cloud.google.com/dataflow/job/" + job.getJobId()
          + "?project=" + job.getProjectId();
      throw new PolicyScannerDataflowException(message, de);
    }

    String outputBucket = Constants.OUTPUT_PREFIX.replaceAll("gs://", "");
    outputBucket = outputBucket.substring(0, outputBucket.lastIndexOf('/'));
    String outputBucketLink = "https://console.cloud.google.com/storage/browser/" + outputBucket;
    String outputPage = "<b>Finished running Scanner!</b><br><br>"
                        + "The output was written to GCS: <a href='%s'>" + "output file" + "</a>";
    out.println(String.format(outputPage, outputBucketLink));
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