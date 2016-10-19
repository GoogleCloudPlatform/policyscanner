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

package com.google.cloud.security.scanner.actions.modifiers;

import static org.junit.Assert.assertEquals;

import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.coders.KvCoder;
import com.google.cloud.dataflow.sdk.coders.SerializableCoder;
import com.google.cloud.dataflow.sdk.testing.TestPipeline;
import com.google.cloud.dataflow.sdk.transforms.Create;
import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.transforms.View;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.cloud.security.scanner.primitives.PoliciedObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for JoinKnownGoodAndLiveStates
 */
@RunWith(JUnit4.class)
public class JoinKnownGoodAndLiveStatesTest {
  private static final String ROLE = "OWNER";
  private static final String MEMBER_1 = "user:test@test.test";
  private static final String MEMBER_2 = "serviceAccount:wow@wow.wow";
  private Pipeline pipeline;

  @Before
  public void setUp() {
    pipeline = TestPipeline.create();
  }

  @Test
  public void testFilterStateNoMatchingResources() {
    GCPProject checkedProject = getSampleProject("_checked");
    GCPProject liveProject = getSampleProject("_live");
    GCPResourceState checkedPolicy = getSampleGCPResourcePolicy(checkedProject, 1);
    GCPResourceState livePolicy = getSampleGCPResourcePolicy(liveProject, 2);
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> sideInputList =
        Arrays.asList(KV.of((GCPResource) checkedProject, KV.of(StateSource.DESIRED, checkedPolicy)));
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> mainInputList =
        Arrays.asList(KV.of((GCPResource) liveProject, KV.of(StateSource.LIVE, livePolicy)));

    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> collection =
        pipeline.apply(Create.of(sideInputList)).setCoder(
            KvCoder.of(SerializableCoder.of(GCPResource.class),
                KvCoder.of(SerializableCoder.of(StateSource.class),
                    SerializableCoder.of(GCPResourceState.class))));
    PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view =
        View.<GCPResource, KV<StateSource, GCPResourceState>>asMap().apply(collection);

    JoinKnownGoodAndLiveStates function = new JoinKnownGoodAndLiveStates(view);
    DoFnTester<KV<GCPResource, KV<StateSource, GCPResourceState>>,
        KV<GCPResource, Map<StateSource, GCPResourceState>>> tester = DoFnTester.of(function);
    tester.setSideInputInGlobalWindow(view, sideInputList);

    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> results =
        tester.processBatch(mainInputList);
    assertEquals(0, results.size());
  }

  @Test
  public void testFilterStateNoMismatches() {
    GCPProject project = getSampleProject("");
    GCPResourceState checkedPolicy = getSampleGCPResourcePolicy(project, 1);
    GCPResourceState livePolicy = checkedPolicy;
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> sideInputList =
        Arrays.asList(KV.of((GCPResource) project, KV.of(StateSource.DESIRED, checkedPolicy)));
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> mainInputList =
        Arrays.asList(KV.of((GCPResource) project, KV.of(StateSource.LIVE, livePolicy)));

    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> collection =
        pipeline.apply(Create.of(sideInputList)).setCoder(
            KvCoder.of(SerializableCoder.of(GCPResource.class),
                KvCoder.of(SerializableCoder.of(StateSource.class),
                    SerializableCoder.of(GCPResourceState.class))));
    PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view =
        View.<GCPResource, KV<StateSource, GCPResourceState>>asMap().apply(collection);

    JoinKnownGoodAndLiveStates function = new JoinKnownGoodAndLiveStates(view);
    DoFnTester<KV<GCPResource, KV<StateSource, GCPResourceState>>,
        KV<GCPResource, Map<StateSource, GCPResourceState>>> tester = DoFnTester.of(function);
    tester.setSideInputInGlobalWindow(view, sideInputList);

    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> results =
        tester.processBatch(mainInputList);
    assertEquals(1, results.size());
  }

  @Test
  public void testFilterStateOneMismatch() {
    GCPProject project = getSampleProject("");
    GCPResourceState checkedPolicy = getSampleGCPResourcePolicy(project, 1);
    GCPResourceState livePolicy = getSampleGCPResourcePolicy(project, 2);
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> sideInputList =
        Arrays.asList(KV.of((GCPResource) project, KV.of(StateSource.DESIRED, checkedPolicy)));
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> mainInputList =
        Arrays.asList(KV.of((GCPResource) project, KV.of(StateSource.LIVE, livePolicy)));

    PCollection<KV<GCPResource, KV<StateSource, GCPResourceState>>> collection =
        pipeline.apply(Create.of(sideInputList)).setCoder(
            KvCoder.of(SerializableCoder.of(GCPResource.class),
                KvCoder.of(SerializableCoder.of(StateSource.class),
                    SerializableCoder.of(GCPResourceState.class))));
    PCollectionView<Map<GCPResource, KV<StateSource, GCPResourceState>>> view =
        View.<GCPResource, KV<StateSource, GCPResourceState>>asMap().apply(collection);

    JoinKnownGoodAndLiveStates function = new JoinKnownGoodAndLiveStates(view);
    DoFnTester<KV<GCPResource, KV<StateSource, GCPResourceState>>,
        KV<GCPResource, Map<StateSource, GCPResourceState>>> tester = DoFnTester.of(function);
    tester.setSideInputInGlobalWindow(view, sideInputList);

    Map<StateSource, GCPResourceState> outputMap = new HashMap<>(2);
    outputMap.put(StateSource.DESIRED, checkedPolicy);
    outputMap.put(StateSource.LIVE, livePolicy);
    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> expectedOutput =
        Arrays.asList(KV.of((GCPResource) project, outputMap));
    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> results =
        tester.processBatch(mainInputList);

    assertEquals(expectedOutput, results);
  }

  private GCPProject getSampleProject(String suffix) {
    if (!suffix.equals("")) {
      suffix = "_" + suffix;
    }
    String projectId = "sampleProjectId" + suffix;
    String orgId = "sampleOrgId" + suffix;
    String projectName = "sampleProjectName" + suffix;
    return new GCPProject(projectId, orgId, projectName);
  }

  private GCPResourcePolicy getSampleGCPResourcePolicy(PoliciedObject resource, int bindingsCount) {
    List<String> members = Arrays.asList(MEMBER_1, MEMBER_2);
    PolicyBinding binding = new PolicyBinding(ROLE, members);
    List<PolicyBinding> bindings = new ArrayList<>(bindingsCount);
    for (int i = 0; i < bindingsCount; ++i) {
      bindings.add(binding);
    }
    return new GCPResourcePolicy(resource, bindings);
  }
}
