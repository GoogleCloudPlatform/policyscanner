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
import com.google.cloud.dataflow.sdk.io.TextIO;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.testing.DataflowAssert;
import com.google.cloud.dataflow.sdk.transforms.Flatten;
import com.google.cloud.dataflow.sdk.transforms.GroupByKey;
import com.google.cloud.dataflow.sdk.transforms.ParDo;
import com.google.cloud.dataflow.sdk.transforms.View;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionList;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.security.scanner.actions.extractors.ExtractState;
import com.google.cloud.security.scanner.actions.extractors.FileToState;
import com.google.cloud.security.scanner.actions.messengers.PolicyDiscrepancyMessenger;
import com.google.cloud.security.scanner.actions.messengers.UnmatchedStatesMessenger;
import com.google.cloud.security.scanner.actions.modifiers.FilterOutPolicies;
import com.google.cloud.security.scanner.actions.modifiers.FindUnmatchedStates;
import com.google.cloud.security.scanner.actions.modifiers.JoinKnownGoodAndLiveStates;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.cloud.security.scanner.sources.LiveProjectSource;
import java.util.List;
import java.util.Map;

/**
 * Check the live state of GCP resources and compare them to a known-good.
 */
public class LiveStateChecker {
  private Pipeline pipeline;
  private BoundedSource<KV<List<String>, String>> knownGoodSource;
  private String org;
  private PCollection<String> scannerDiffOutput;
  private String diffOutputLocation;
  private String unmatchedOutputLocation;

  /**
   * Construct a LiveStateChecker to compare the live states of GCP resources
   * with their checked-in known-good counterparts.
   * @param options The options used to construct the pipeline.
   * @param knownGoodSource The source used to read the known-good.
   * @param org The organization the projects are to be read from.
   */
  public LiveStateChecker(PipelineOptions options,
      BoundedSource<KV<List<String>, String>> knownGoodSource,
      String org) {
    this.pipeline = Pipeline.create(options);
    this.knownGoodSource = knownGoodSource;
    this.org = org;
  }

  public LiveStateChecker build() {
    this.scannerDiffOutput = constructPipeline(this.pipeline, this.org, this.knownGoodSource);
    return this;
  }

  /**
   * Run the pipeline.
   */
  public LiveStateChecker run() {
    this.pipeline.run();
    return this;
  }

  /**
   * Set the scanner diff output location
   * @param sinkUrl The output url
   */
  public LiveStateChecker setDiffOutputLocation(String sinkUrl) {
    this.diffOutputLocation = sinkUrl;
    return this;
  }

  /**
   * Set the scanner unmatched states output location
   * @param sinkUrl The output url
   */
  public LiveStateChecker setUnmatchedOutputLocation(String sinkUrl) {
    this.unmatchedOutputLocation = sinkUrl;
    return this;
  }

  /**
   * Assert a containsInAnyOrder on the pipeline with the passed in inputs.
   * @param expectedOutputs The strings that are to be contained in the pipeline's final output.
   */
  LiveStateChecker appendAssertContains(String[] expectedOutputs) {
    DataflowAssert.that(this.scannerDiffOutput).containsInAnyOrder(expectedOutputs);
    return this;
  }

  private PCollection<String> constructPipeline(
      Pipeline pipeline,
      String org,
      BoundedSource<KV<List<String>,
      String>> knownGoodSource) {
    // Read files from GCS.
    PCollection<KV<List<String>, String>> knownGoodFiles =
        pipeline.apply("Read known-good data", Read.from(knownGoodSource));
    // Convert files to GCPResourceState objects.
    PCollection<KV<GCPResource, GCPResourceState>> knownGoodStates =
        knownGoodFiles.apply(ParDo.named("Convert file data to Java Objects")
            .of(new FileToState()));
    // Tag the state objects to indicate they're from a checked-in repo and not live.
    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> taggedKnownGoodStates =
        knownGoodStates.apply(ParDo.named("Mark states as being known-good")
            .of(new TagStateWithSource(StateSource.DESIRED)));

    // Read projects from the CRM API.
    PCollection<GCPProject> allProjects =
        pipeline.apply("Read live projects", Read.from(new LiveProjectSource(org)));
    // Extract project states.
    PCollection<KV<GCPResource, GCPResourceState>> liveStates =
        allProjects.apply(ParDo.named("Extract project policies").of(new ExtractState()));
    // Tag the states to indicate they're live and not from a checked-in source.
    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> taggedLiveStates =
        liveStates.apply(ParDo.named("Mark states as being live")
            .of(new TagStateWithSource(StateSource.LIVE)));

    PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> knownGoodStatesView =
        taggedKnownGoodStates.apply(View.<GCPResource, KV<StateSource, GCPResourceState>>asMap());
    PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> liveStatesView =
        taggedLiveStates.apply(View.<GCPResource, KV<StateSource, GCPResourceState>>asMap());

    // Find unmatched states (known good with no matching live/live with no matching known-good)
    PCollection<KV<String, GCPResource>> unmatchedKnownGoodStates =
        taggedKnownGoodStates.apply(
            ParDo.named("Find known good states with no matching live states")
                .withSideInputs(liveStatesView)
                .of(new FindUnmatchedStates(liveStatesView)));
    PCollection<KV<String, GCPResource>> unmatchedLiveStates =
        taggedLiveStates.apply(
            ParDo.named("Find live states with no matching known good states")
                .withSideInputs(knownGoodStatesView)
                .of(new FindUnmatchedStates(knownGoodStatesView)));

    PCollection<KV<String, GCPResource>> mergedUnmatchedStates =
        PCollectionList.of(unmatchedKnownGoodStates).and(unmatchedLiveStates)
            .apply(Flatten.<KV<String, GCPResource>>pCollections());

    PCollection<KV<String, Iterable<GCPResource>>> groupedUnmatchedStates =
        mergedUnmatchedStates.apply(GroupByKey.<String, GCPResource>create());

    PCollection<String> allUnmatchedStates = groupedUnmatchedStates
        .apply(ParDo.named("Format unmatched states output")
        .of(new UnmatchedStatesMessenger()));

    if (this.unmatchedOutputLocation != null) {
      allUnmatchedStates.apply(TextIO.Write.named("Write unmatched states to GCS")
          .to(this.unmatchedOutputLocation));
    }

    // Join the two known-good and the live halves.
    PCollection<KV<GCPResource, Map<StateSource, GCPResourceState>>> joinedStates =
        taggedLiveStates.apply(ParDo.named("Find states that don't match")
            .withSideInputs(knownGoodStatesView)
            .of(new JoinKnownGoodAndLiveStates(knownGoodStatesView)));
    PCollection<KV<GCPResource, Map<StateSource, GCPResourcePolicy>>> joinedPolicies =
        joinedStates.apply(ParDo.named("FilterOutPolicies").of(new FilterOutPolicies()));

    // Construct an alert message for all the discrepancies found.
    PCollection<String> discrepancyOutput =
        joinedPolicies.apply(ParDo
            .named("Generate notification messages")
            .of(new PolicyDiscrepancyMessenger()));

    if (this.diffOutputLocation != null) {
      discrepancyOutput
          .apply(TextIO.Write.named("Write diff messages to GCS").to(this.diffOutputLocation));
    }

    return discrepancyOutput;
  }
}
