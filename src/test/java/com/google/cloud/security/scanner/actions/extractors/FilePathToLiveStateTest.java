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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects;
import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects.GetIamPolicy;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.cloud.security.scanner.primitives.PoliciedObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for FilePathToLiveState.
 */
@RunWith(JUnit4.class)
public class FilePathToLiveStateTest {

  private DoFnTester<List<String>, KV<GCPResource, GCPResourceState>> tester;
  private Projects projects = mock(Projects.class);

  @Before
  public void setUp() {
    tester = DoFnTester.of(new FilePathToLiveState());
    GCPProject.setProjectsApiStub(projects);
  }

  @Test
  public void testInvalidStateFile() {
    List<String> filePath = Arrays.asList("sampleOrg", "sampleProject", "invalidStateFileName");
    try {
      tester.processBatch(filePath);
      fail("No exception thrown on invalid statefile name");
    } catch (Exception ignored) {
      // test passed
    }
  }

  @Test
  public void testValidFile() throws IOException {
    String projectId = "sampleProject";
    List<String> filePath = Arrays.asList("sampleOrg", projectId, "POLICY");
    GCPProject project = new GCPProject(projectId);
    GCPResourcePolicy gcpResourcePolicy = getSampleGCPResourcePolicy(project);
    Policy policy = getSamplePolicy();
    GetIamPolicy correctRequest = mock(GetIamPolicy.class);
    GetIamPolicy wrongRequest = mock(GetIamPolicy.class);
    when(projects.getIamPolicy(anyString(), any(GetIamPolicyRequest.class)))
        .thenReturn(wrongRequest);
    when(projects.getIamPolicy(eq(projectId), any(GetIamPolicyRequest.class)))
        .thenReturn(correctRequest);
    when(correctRequest.execute()).thenReturn(policy);
    when(wrongRequest.execute()).thenThrow(new NoSuchElementException());
    try {
      assertEquals(tester.processBatch(filePath), Arrays.asList(KV.of(project, gcpResourcePolicy)));
    } catch (IllegalArgumentException ignored) {
      fail("Exception thrown on valid statefile name");
    } catch (NoSuchElementException nse) {
      fail("Tried accessing the wrong project ID");
    }
  }

  private Policy getSamplePolicy() {
    String ownerRole = "OWNER";
    String editorRole = "EDITOR";
    List<String> ownerMembers = Arrays.asList("owner@own.er", "own@erown.er");
    List<String> editorMembers = Arrays.asList("editor@edit.or", "edit@oredit.or");
    Binding ownerBinding = new Binding().setRole(ownerRole).setMembers(ownerMembers);
    Binding editorBinding = new Binding().setRole(editorRole).setMembers(editorMembers);
    List<Binding> bindings = Arrays.asList(ownerBinding, editorBinding);
    return new Policy().setBindings(bindings);
  }

  private GCPResourcePolicy getSampleGCPResourcePolicy(PoliciedObject resource) {
    String ownerRole = "OWNER";
    String editorRole = "EDITOR";
    List<String> ownerMembers = Arrays.asList("owner@own.er", "own@erown.er");
    List<String> editorMembers = Arrays.asList("editor@edit.or", "edit@oredit.or");
    PolicyBinding ownerBinding = new PolicyBinding(ownerRole, ownerMembers);
    PolicyBinding editorBinding = new PolicyBinding(editorRole, editorMembers);
    List<PolicyBinding> bindings = Arrays.asList(ownerBinding, editorBinding);
    return new GCPResourcePolicy(resource, bindings);
  }
}
