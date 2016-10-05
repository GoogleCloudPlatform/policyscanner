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

package com.google.cloud.security.scanner.actions.extractors;

import static org.junit.Assert.assertEquals;

import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.cloud.security.scanner.primitives.PoliciedObject;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for FileToState */
@RunWith(JUnit4.class)
public class FileToStateTest {
  private static final String POLICY_FILE = "POLICY";
  private static final String ROLE = "OWNER";
  private static final String MEMBER_1 = "user:test@test.test";
  private static final String MEMBER_2 = "serviceAccount:wow@wow.wow";
  private DoFnTester<KV<List<String>, String>, KV<GCPResource, GCPResourceState>> tester;

  @Before
  public void setUp() throws IOException {
    this.tester = DoFnTester.of(new FileToState());
  }

  @Test
  public void testOneElement() throws IOException {
    List<String> filePath = getSampleProjectFilePath(getSampleProject());
    String fileContent = getSamplePolicyBindingsString(1);
    GCPProject project = getSampleProject();
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    List<KV<List<String>, String>> inputs = Arrays.asList(KV.of(filePath, fileContent));

    List<KV<GCPResource, GCPResourceState>> results = this.tester.processBatch(inputs);
    assertEquals(results.size(), 1);
    assertEquals(results.get(0).getKey(), project);
    assertEquals(results.get(0).getValue(), policy);
  }

  @Test
  public void testMultipleElements() throws IOException {
    int elementCount = 5;
    GCPProject project = getSampleProject();
    List<String> filePath = getSampleProjectFilePath((GCPProject) project);
    String fileContent = getSamplePolicyBindingsString(1);
    GCPResourceState policy = getSampleGCPResourcePolicy(project, 1);
    List<KV<List<String>, String>> inputs = new ArrayList<>(elementCount);

    for (int i = 0; i < elementCount; ++i) {
      inputs.add(KV.of(filePath, fileContent));
    }

    List<KV<GCPResource, GCPResourceState>> results = this.tester.processBatch(inputs);
    assertEquals(results.size(), elementCount);
    for (int i = 0; i < elementCount; ++i) {
      assertEquals(results.get(i).getKey(), project);
      assertEquals(results.get(i).getValue(), policy);
    }
  }

  private GCPProject getSampleProject() {
    String projectId = "sampleProjectId";
    String orgId = "sampleOrgId";
    return new GCPProject(projectId, orgId, null);
  }

  private List<String> getSampleProjectFilePath(GCPProject project) {
    return Arrays.asList(project.getOrgId(), project.getId(), POLICY_FILE);
  }

  private String getSamplePolicyBindingsString(int bindingsCount) {
    List<String> members = Arrays.asList(MEMBER_1, MEMBER_2);
    PolicyBinding binding = new PolicyBinding(ROLE, members);
    PolicyBinding[] bindings = new PolicyBinding[bindingsCount];
    for (int i = 0; i < bindingsCount; ++i) {
      bindings[i] = binding;
    }
    return new Gson().toJson(bindings);
  }

  private GCPResourcePolicy getSampleGCPResourcePolicy(PoliciedObject resource, int bindingsCount) {
    List<String> members = Arrays.asList(MEMBER_1, MEMBER_2);
    PolicyBinding binding = new PolicyBinding(ROLE, members);
    PolicyBinding[] bindings = new PolicyBinding[bindingsCount];
    for (int i = 0; i < bindingsCount; ++i) {
      bindings[i] = binding;
    }
    return new GCPResourcePolicy(resource, Arrays.asList(bindings));
  }
}
