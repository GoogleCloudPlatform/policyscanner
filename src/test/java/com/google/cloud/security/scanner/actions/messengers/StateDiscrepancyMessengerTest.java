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

package com.google.cloud.security.scanner.actions.messengers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
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
 * Test for StateDiscrepancyMessenger
 */
@RunWith(JUnit4.class)
public class StateDiscrepancyMessengerTest {
  private static final String ROLE = "OWNER";
  private static final String MEMBER_1 = "user:test@test.test";
  private static final String MEMBER_2 = "serviceAccount:wow@wow.wow";

  private DoFnTester<KV<GCPResource, Map<StateSource, GCPResourceState>>, String> tester;

  @Before
  public void setUp() {
    this.tester = DoFnTester.of(new StateDiscrepancyMessenger());
  }

  @Test
  public void testEmptyMapInput() {
    GCPResource project = getSampleProject("");
    Map<StateSource, GCPResourceState> emptyMap = new HashMap<>(0);
    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> inputs =
        Arrays.asList(KV.of(project, emptyMap));
    try {
      this.tester.processBatch(inputs).size();
      fail();
    } catch (Exception ignored) {
      // test passed.
    }
  }

  @Test
  public void testIncompleteMapInput() {
    GCPProject project = getSampleProject("");
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    Map<StateSource, GCPResourceState> incompleteMap = new HashMap<>(1);
    incompleteMap.put(StateSource.DESIRED, policy);
    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> inputs =
        Arrays.asList(KV.of((GCPResource) project, incompleteMap));
    try {
      this.tester.processBatch(inputs).size();
      fail();
    } catch (Exception ignored) {
      // test passed.
    }
  }

  @Test
  public void testSingleMapInput() {
    GCPProject project = getSampleProject("");
    GCPResourceState checkedPolicy = getSampleGCPResourcePolicy(project, 1);
    GCPResourceState livePolicy = getSampleGCPResourcePolicy(project, 2);
    Map<StateSource, GCPResourceState> inputMap = new HashMap<>(1);
    inputMap.put(StateSource.DESIRED, checkedPolicy);
    inputMap.put(StateSource.LIVE, livePolicy);

    List<KV<GCPResource, Map<StateSource, GCPResourceState>>> inputs =
        Arrays.asList(KV.of((GCPResource) project, inputMap));
    String expectedOutput = constructMessage(project, inputMap);
    List<String> results = this.tester.processBatch(inputs);
    assertEquals(results.size(), 1);
    assertEquals(results.get(0), expectedOutput);
  }

  private String constructMessage(GCPResource resource, Map<StateSource, GCPResourceState> input) {
    return "For resource: " + resource.toString()+
        "\n\tChecked in state:\n\t\t" +
        input.get(StateSource.DESIRED).toString() +
        "\n\tActual state:\n\t\t" +
        input.get(StateSource.LIVE).toString();
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
