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

package com.google.cloud.security.scanner.pipelines;

import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.io.Read;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.testing.DataflowAssert;
import com.google.cloud.dataflow.sdk.transforms.PTransform;
import com.google.cloud.dataflow.sdk.transforms.ParDo;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PDone;
import com.google.cloud.security.scanner.actions.extractors.ListServiceAccountKeys;
import com.google.cloud.security.scanner.actions.extractors.ListServiceAccounts;
import com.google.cloud.security.scanner.actions.messengers.ExportedServiceAccountKeyMessenger;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPServiceAccount;
import com.google.cloud.security.scanner.primitives.GCPServiceAccountKey;
import com.google.cloud.security.scanner.sources.LiveProjectSource;

/**
 * Remove user-managed service account keys from the projects.
 */
public class ExportedServiceAccountKeyRemover {
  private Pipeline pipeline;
  private PCollection<String> outputMessages;

  /**
   * Constructor far ExportedServiceAccountKeyRemover
   * @param options The options used to construct the pipeline.
   * @param org The organization the projects are to be read from.
   */
  public ExportedServiceAccountKeyRemover(PipelineOptions options, String org) {
    this.pipeline = Pipeline.create(options);
    this.outputMessages = constructPipeline(this.pipeline, org);
  }

  /**
   * Run the pipeline.
   */
  public ExportedServiceAccountKeyRemover run() {
    this.pipeline.run();
    return this;
  }

  /**
   * Attach a sink to output the messages to.
   * @param sinkTransform Transform to output messages to the sink.
   */
  public ExportedServiceAccountKeyRemover attachSink(PTransform<PCollection<String>, PDone> sinkTransform) {
    this.outputMessages.apply(sinkTransform);
    return this;
  }

  /**
   * Assert a containsInAnyOrder on the pipeline with the passed in inputs.
   * @param expectedOutputs The strings that are to be contained in the pipeline's final output.
   */
  ExportedServiceAccountKeyRemover appendAssertContains(String[] expectedOutputs) {
    DataflowAssert.that(this.outputMessages).containsInAnyOrder(expectedOutputs);
    return this;
  }

  private PCollection<String> constructPipeline(Pipeline pipeline, String org) {
    // Read projects from the CRM API.
    PCollection<GCPProject> projects =
        pipeline.apply(Read.from(new LiveProjectSource(org)));
    // List the service accounts of the projects.
    PCollection<GCPServiceAccount> serviceAccounts =
        projects.apply(ParDo.named("List Service Accounts").of(new ListServiceAccounts()));
    // List the keys of the service accounts.
    PCollection<GCPServiceAccountKey> serviceAccountKeys =
        serviceAccounts.apply(ParDo.named("List Service Account Keys")
            .of(new ListServiceAccountKeys()));
    // Construct an alert message for all the discrepancies found.
    return serviceAccountKeys.apply(ParDo
        .named("Remove user-managed keys")
        .of(new ExportedServiceAccountKeyMessenger()));
  }
}
