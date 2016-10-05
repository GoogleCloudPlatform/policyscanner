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

import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.cloud.security.scanner.primitives.PoliciedObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for TagStateWithSource
 */
@RunWith(JUnit4.class)
public class TagStateWithSourceTest {
  private static final String ROLE = "OWNER";
  private static final String MEMBER_1 = "user:test@test.test";
  private static final String MEMBER_2 = "serviceAccount:wow@wow.wow";
  private DoFnTester<KV<GCPResource, GCPResourceState>,
        KV<GCPResource, KV<StateSource, GCPResourceState>>> checkedTester;
  private DoFnTester<KV<GCPResource, GCPResourceState>,
      KV<GCPResource, KV<StateSource, GCPResourceState>>> liveTester;

  @Before
  public void setUp() throws IOException {
    this.checkedTester = DoFnTester.of(new TagStateWithSource(StateSource.DESIRED));
    this.liveTester = DoFnTester.of(new TagStateWithSource(StateSource.LIVE));
  }

  @Test
  public void testCheckedTaggerSingleInput() {
    GCPProject project = getSampleProject("");
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    List<KV<GCPResource, GCPResourceState>> inputs = Arrays.asList(KV.of((GCPResource) project, policy));
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> expectedOutputs =
        Arrays.asList(KV.of((GCPResource) project, KV.of(StateSource.DESIRED, policy)));

    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> results =
        this.checkedTester.processBatch(inputs);

    assertEquals(results, expectedOutputs);
  }

  @Test
  public void testLiveTaggerSingleInput() {
    GCPProject project = getSampleProject("");
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    List<KV<GCPResource, GCPResourceState>> inputs = Arrays.asList(KV.of((GCPResource) project, policy));
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> expectedOutputs =
        Arrays.asList(KV.of((GCPResource) project, KV.of(StateSource.LIVE, policy)));

    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> results =
        this.liveTester.processBatch(inputs);

    assertEquals(results, expectedOutputs);
  }

  @Test
  public void testCheckedTaggerMultipleInput() {
    int elementCount = 5;
    GCPProject project = getSampleProject("");
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    List<KV<GCPResource, GCPResourceState>> inputs = new ArrayList<>(elementCount);
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> expectedOutputs =
        new ArrayList<>(elementCount);

    for (int i = 0; i < elementCount; ++i) {
      inputs.add(KV.of((GCPResource) project, policy));
      expectedOutputs.add(KV.of((GCPResource) project, KV.of(StateSource.DESIRED, policy)));
    }

    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> results =
        this.checkedTester.processBatch(inputs);

    assertEquals(results, expectedOutputs);
  }

  @Test
  public void testLiveTaggerMultipleInput() {
    int elementCount = 5;
    GCPProject project = getSampleProject("");
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    List<KV<GCPResource, GCPResourceState>> inputs = new ArrayList<>(elementCount);
    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> expectedOutputs =
        new ArrayList<>(elementCount);

    for (int i = 0; i < elementCount; ++i) {
      inputs.add(KV.of((GCPResource) project, policy));
      expectedOutputs.add(KV.of((GCPResource) project, KV.of(StateSource.LIVE, policy)));
    }

    List<KV<GCPResource, KV<StateSource, GCPResourceState>>> results =
        this.liveTester.processBatch(inputs);

    assertEquals(results, expectedOutputs);
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
