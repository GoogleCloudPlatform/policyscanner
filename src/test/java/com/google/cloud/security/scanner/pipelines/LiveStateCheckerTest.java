/**
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.security.scanner.pipelines;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects.GetIamPolicy;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.testing.DataflowAssert;
import com.google.cloud.security.scanner.actions.messengers.MessageConstructor;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicyDiff;
import com.google.cloud.security.scanner.sources.GCSFilesSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for LiveStateChecker.
 */
@RunWith(JUnit4.class)
public class LiveStateCheckerTest {
  private static final String BUCKET = "sampleBucket";
  private static final String ORG_ID = "sampleOrgId";
  private static final String PROJECT_ID = "sampleProjectId";
  private static final String DELIM = "/";
  private static final String POLICY_FILE = "POLICY";
  private GCSFilesSource checkedSource;

  private CloudResourceManager.Projects projectsObject = mock(CloudResourceManager.Projects.class);
  private CloudResourceManager.Projects.List listProjects = mock(CloudResourceManager.Projects.List.class);
  private ListProjectsResponse listProjectsResponse = new ListProjectsResponse();
  private GetIamPolicy getIamPolicy = mock(GetIamPolicy.class);

  private Storage gcs = mock(Storage.class);

  private Storage.Buckets buckets = mock(Storage.Buckets.class);
  private Storage.Buckets.Get bucketGet = mock(Storage.Buckets.Get.class);

  private Storage.Objects objects = mock(Storage.Objects.class);
  private Storage.Objects.Get objectGet = mock(Storage.Objects.Get.class);
  private Storage.Objects.List objectList = mock(Storage.Objects.List.class);

  @Before
  public void setUp() throws GeneralSecurityException, IOException {
    GCPProject.setProjectsApiStub(projectsObject);

    CloudResourceManager.Projects.List emptyList = mock(CloudResourceManager.Projects.List.class);
    ListProjectsResponse emptyListProjectResponse = new ListProjectsResponse();

    when(projectsObject.list()).thenReturn(listProjects);
    when(listProjects.setPageToken(anyString())).thenReturn(emptyList);
    when(listProjects.setPageToken(null)).thenReturn(listProjects);
    when(listProjects.setFilter(anyString())).thenReturn(listProjects);

    when(emptyList.setPageToken(null)).thenReturn(emptyList);
    when(emptyList.setPageToken(anyString())).thenReturn(emptyList);
    when(emptyList.setFilter(anyString())).thenReturn(emptyList);

    when(emptyList.execute()).thenReturn(emptyListProjectResponse
        .setNextPageToken("maybe halt?")
        .setProjects(new ArrayList<Project>(0)));

    when(objectList.setPageToken(anyString())).thenReturn(objectList);
    when(objectList.setPageToken(null)).thenReturn(objectList);
    when(objectList.setPrefix(anyString())).thenReturn(objectList);

    when(objects.list(anyString())).thenReturn(objectList);
    when(objects.get(anyString(), anyString())).thenReturn(objectGet);
    when(gcs.objects()).thenReturn(objects);

    when(buckets.get(anyString())).thenReturn(bucketGet);
    when(gcs.buckets()).thenReturn(buckets);

    when(this.projectsObject.getIamPolicy(anyString(), any(GetIamPolicyRequest.class)))
        .thenReturn(this.getIamPolicy);

    GCSFilesSource.setStorageApiStub(gcs);
    this.checkedSource = new GCSFilesSource(BUCKET, ORG_ID);
  }

  @Test
  public void testPipeline() throws IOException {
    String editorRole = "roles/editor";
    String editorMember = "serviceAccount:sample@sample.sample.com";
    String editorMemberLive = "serviceAccount:sample@wow.com";
    String ownerRole = "roles/owner";
    String ownerMember = "user:sample@sample.com";
    String fileContent = "[\n"
        + "      {\n"
        + "        \"role\": \"" + ownerRole + "\",\n"
        + "        \"members\": [\n"
        + "          \"" + ownerMember + "\"\n"
        + "        ]\n"
        + "      },\n"
        + "      {\n"
        + "        \"role\": \"" + editorRole + "\",\n"
        + "        \"members\": [\n"
        + "          \"" + editorMember + "\"\n"
        + "        ]\n"
        + "      }\n"
        + "    ]";
    String filePath = ORG_ID + DELIM + PROJECT_ID + DELIM + POLICY_FILE;
    String projectName = "sampleProjectName";
    String projectId = PROJECT_ID;
    String orgId = ORG_ID;
    ResourceId resourceId = new ResourceId().setId(orgId);
    Project project =
        new Project()
            .setProjectId(projectId)
            .setParent(resourceId)
            .setName(projectName)
            .setLifecycleState("ACTIVE");
    Binding editorBinding = new Binding()
        .setRole(editorRole)
        .setMembers(Arrays.asList(editorMemberLive));
    Binding ownerBinding = new Binding()
        .setRole(ownerRole)
        .setMembers(Arrays.asList(ownerMember));
    List<Binding> bindings = Arrays.asList(ownerBinding, editorBinding);
    Policy iamPolicy = new Policy().setBindings(bindings);
    PipelineOptions options = PipelineOptionsFactory.create();

    setUpGetFileContent(fileContent);
    setUpGetFilesPage(filePath);
    when(listProjects.execute())
        .thenReturn(this.listProjectsResponse
            .setNextPageToken("halting string")
            .setProjects(Arrays.asList(project)));
    when(this.getIamPolicy.execute()).thenReturn(iamPolicy);

    GCPProject.setProjectsApiStub(this.projectsObject);

    // setting up the output objects.
    GCPProject gcpProject = new GCPProject(projectId, orgId, projectName);
    PolicyBinding ownerPolicyBinding = new PolicyBinding(ownerRole, Arrays.asList(ownerMember));
    PolicyBinding editorPolicyBinding =
        new PolicyBinding(editorRole, Arrays.asList(editorMember));
    PolicyBinding editorPolicyBindingLive =
        new PolicyBinding(editorRole, Arrays.asList(editorMemberLive));
    GCPResourcePolicy desiredPolicy = new GCPResourcePolicy(
        gcpProject, Arrays.asList(ownerPolicyBinding, editorPolicyBinding));
    GCPResourcePolicy livePolicy = new GCPResourcePolicy(
        gcpProject, Arrays.asList(ownerPolicyBinding, editorPolicyBindingLive));
    GCPResourcePolicyDiff diff = GCPResourcePolicyDiff.diff(desiredPolicy, livePolicy);
    MessageConstructor messageConstructor =
        new MessageConstructor(gcpProject, desiredPolicy, livePolicy, diff);

    new LiveStateChecker(options, this.checkedSource, ORG_ID)
        .build()
        .appendAssertContains(new String[]{messageConstructor.constructMessage()})
        .run();
  }

  @Test
  public void testUnmatchedStatesOutputIsCorrect() throws IOException {
    // create the policy for the live project
    String editorRole = "roles/editor";
    String editorMember = "serviceAccount:sample@sample.sample.com";
    String ownerRole = "roles/owner";
    String ownerMember = "user:sample@sample.com";
    String fileContent = "[\n"
        + "      {\n"
        + "        \"role\": \"" + ownerRole + "\",\n"
        + "        \"members\": [\n"
        + "          \"" + ownerMember + "\"\n"
        + "        ]\n"
        + "      },\n"
        + "      {\n"
        + "        \"role\": \"" + editorRole + "\",\n"
        + "        \"members\": [\n"
        + "          \"" + editorMember + "\"\n"
        + "        ]\n"
        + "      }\n"
        + "    ]";
    String liveProjectName = "someLiveProjectName";
    String liveProjectId = "someLiveProjectId";
    String orgId = ORG_ID;
    ResourceId resourceId = new ResourceId().setId(orgId);
    Project liveProject =
        new Project().setProjectId(liveProjectId).setParent(resourceId).setName(liveProjectName);
    Binding editorBinding = new Binding()
        .setRole(editorRole)
        .setMembers(Arrays.asList(editorMember));
    Binding ownerBinding = new Binding()
        .setRole(ownerRole)
        .setMembers(Arrays.asList(ownerMember));
    List<Binding> bindings = Arrays.asList(ownerBinding, editorBinding);
    Policy iamPolicy = new Policy().setBindings(bindings);
    // when calling projects().list(), return the live project
    when(listProjects.execute())
    .thenReturn(this.listProjectsResponse
        .setNextPageToken("halting string")
        .setProjects(Arrays.asList(liveProject)));
    when(this.getIamPolicy.execute()).thenReturn(iamPolicy);

    // mock out the desired policy
    String desiredProjectId = "someKnownGoodProject";
    String desiredPolicyPath = ORG_ID + DELIM + desiredProjectId + DELIM + POLICY_FILE;

    setUpGetFileContent(fileContent);
    setUpGetFilesPage(desiredPolicyPath);

    PipelineOptions options = PipelineOptionsFactory.create();

    LiveStateChecker liveStateChecker =
        new LiveStateChecker(options, this.checkedSource, ORG_ID)
          .build();

    String[] expectedOutput = new String[] {
        "DESIRED:someKnownGoodProject",
        "LIVE:someLiveProjectId"
    };

    DataflowAssert
        .that(liveStateChecker.getUnmatchedStatesOutput())
        .containsInAnyOrder(expectedOutput);

    liveStateChecker.run();
  }

  private void setUpGetFileContent(final String testString) {
    try {
      doAnswer(
          new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
              ByteArrayOutputStream out = (ByteArrayOutputStream) invocationOnMock.getArguments()[0];
              out.write(testString.getBytes(UTF_8));
              return null;
            }
          })
          .when(this.objectGet)
          .executeMediaAndDownloadTo(any(ByteArrayOutputStream.class));
    } catch (IOException e) {
      fail("Could not set up getFileContent");
    }
  }

  private void setUpGetFilesPage(String objectName) {
    setUpGetFilesPage(Arrays.asList(objectName));
  }

  private void setUpGetFilesPage(List<String> objectNames) {
    // these are final classes, so use fakes instead of mocks.
    List<StorageObject> fakeItems = new ArrayList<>();
    for (String anObjectName : objectNames) {
      fakeItems.add(new StorageObject().setName(anObjectName));
    }

    Objects listObjects = new Objects().setItems(fakeItems);

    try {
      when(this.objectList.execute()).thenReturn(listObjects);
    } catch (IOException e) {
      fail("Failed to setup getFilesPage");
    }
  }
}
