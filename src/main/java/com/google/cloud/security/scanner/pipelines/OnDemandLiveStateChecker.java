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
import com.google.cloud.dataflow.sdk.io.BoundedSource;
import com.google.cloud.dataflow.sdk.io.Read;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.testing.DataflowAssert;
import com.google.cloud.dataflow.sdk.transforms.PTransform;
import com.google.cloud.dataflow.sdk.transforms.ParDo;
import com.google.cloud.dataflow.sdk.transforms.View;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.dataflow.sdk.values.PDone;
import com.google.cloud.security.scanner.actions.extractors.FilePathToLiveState;
import com.google.cloud.security.scanner.actions.extractors.FileToState;
import com.google.cloud.security.scanner.actions.messengers.StateDiscrepancyMessenger;
import com.google.cloud.security.scanner.actions.modifiers.FilePathFromPair;
import com.google.cloud.security.scanner.actions.modifiers.FilterOutMatchingState;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import java.util.List;
import java.util.Map;

/**
 * Check the live state of GCP resources and compare them to a known-good.
 */
public class OnDemandLiveStateChecker {
  private Pipeline pipeline;
  private PCollection<String> outputMessages;

  /**
   * Construct a OnDemandLiveStateChecker to compare the live states of GCP resources
   * with their checked-in known-good counterparts.
   * @param options The options used to construct the pipeline.
   * @param knownGoodSource The source used to read the known-good.
   */
  public OnDemandLiveStateChecker(PipelineOptions options,
      BoundedSource<KV<List<String>, String>> knownGoodSource) {
    this.pipeline = Pipeline.create(options);
    this.outputMessages = constructPipeline(this.pipeline, knownGoodSource);
  }

  /**
   * Run the pipeline.
   */
  public OnDemandLiveStateChecker run() {
    this.pipeline.run();
    return this;
  }

  /**
   * Attach a sink to output the messages to.
   * @param sinkTransform Transform to output messages to the sink.
   */
  public OnDemandLiveStateChecker attachSink(PTransform<PCollection<String>, PDone> sinkTransform) {
    this.outputMessages.apply(sinkTransform);
    return this;
  }

  /**
   * Assert a containsInAnyOrder on the pipeline with the passed in inputs.
   * @param expectedOutputs The strings that are to be contained in the pipeline's final output.
   */
  OnDemandLiveStateChecker appendAssertContains(String[] expectedOutputs) {
    DataflowAssert.that(this.outputMessages).containsInAnyOrder(expectedOutputs);
    return this;
  }

  private PCollection<String> constructPipeline(Pipeline pipeline,
      BoundedSource<KV<List<String>, String>> knownGoodSource) {
    // Read files from GCS.
    PCollection<KV<List<String>, String>> knownGoodFiles =
        pipeline.apply("Read known-good data", Read.from(knownGoodSource));
    // Convert files to GCPResourceState objects.
    PCollection<KV<GCPResource, GCPResourceState>> knownGoodStates =
        knownGoodFiles.apply(ParDo.named("Convert file data to Java objects")
            .of(new FileToState()));
    // Tag the state objects to indicate they're from a checked-in repo and not live.
    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> taggedKnownGoodStates =
        knownGoodStates.apply(ParDo.named("Mark states as being known-good")
            .of(new TagStateWithSource(StateSource.DESIRED)));

    // Extract a list of checked-in projects from GCS.
    PCollection<List<String>> allFilePaths = knownGoodFiles
        .apply("Extract just the file paths", ParDo.of(new FilePathFromPair()));
    // Read the live version of the states of the checked-in projects.
    PCollection<KV<GCPResource, GCPResourceState>> liveStates =
        allFilePaths.apply(ParDo.named("Get live resource and states from file path")
            .of(new FilePathToLiveState()));
    // Tag the states to indicate they're live and not from a checked-in source.
    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> taggedLiveStates =
        liveStates.apply(ParDo.named("Mark states as being live")
            .of(new TagStateWithSource(StateSource.LIVE)));

    // Join the two known-good and the live halves.
    PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> liveStatesView =
        taggedLiveStates.apply(View.<GCPResource, KV<StateSource, GCPResourceState>>asMap());
    PCollection<KV<GCPResource, Map<StateSource, GCPResourceState>>> mismatchedStates =
        taggedKnownGoodStates.apply(ParDo.named("Find states that don't match")
            .withSideInputs(liveStatesView)
            .of(new FilterOutMatchingState(liveStatesView)));
    // Construct an alert message for all the discrepancies found.
    return mismatchedStates.apply(ParDo
        .named("Generate notification messages")
        .of(new StateDiscrepancyMessenger()));
  }
}
