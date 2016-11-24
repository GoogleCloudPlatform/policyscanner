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
import com.google.cloud.dataflow.sdk.io.TextIO;
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.runners.AggregatorRetrievalException;
import com.google.cloud.dataflow.sdk.runners.BlockingDataflowPipelineRunner;
import com.google.cloud.security.scanner.common.CloudUtil;
import com.google.cloud.security.scanner.common.Constants;
import com.google.cloud.security.scanner.pipelines.DesiredStateEnforcer;
import com.google.cloud.security.scanner.sources.GCSFilesSource;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handler class for the Dataflow local runner test endpoint. */
public class DesiredStateEnforcerApp extends HttpServlet {

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
      throw new IOException("SecurityException: Cannot create GCSFileSource");
    }
    PipelineOptions options;
    if (CloudUtil.willExecuteOnCloud()) {
      options = getCloudExecutionOptions(Constants.DATAFLOW_STAGING);
    } else {
      options = getLocalExecutionOptions();
    }
    String datetimestamp = new SimpleDateFormat(Constants.SINK_TIMESTAMP_FORMAT).format(new Date());
    DesiredStateEnforcer enforcer = null;
    try {
      enforcer = new DesiredStateEnforcer(options, source, Constants.ORG_ID)
          .attachSink(TextIO.Write
              .named("Write messages to GCS")
              .to(MessageFormat.format(Constants.SINK_NAME_FORMAT,
                  new Object[]{
                      Constants.OUTPUT_PREFIX,
                      datetimestamp,
                      Constants.OUTPUT_LABEL_ENFORCER
                      })))
          .run();
      if (enforcer.getTotalEnforcedStates() < 1) {
        out.println("Finished running Enforcer! No states needed to be enforced.");
      } else {
        out.println("Finished running Enforcer! The output was written to GCS");
      }
    } catch (AggregatorRetrievalException aggRetrievalException) {
      // TODO(carise): do something better than this
      aggRetrievalException.printStackTrace();
    }
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
