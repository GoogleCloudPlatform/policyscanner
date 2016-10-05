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

package com.google.cloud.security.scanner.sources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.sources.LiveProjectSource.LiveProjectReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for LiveProjectSource */
@RunWith(JUnit4.class)
public class LiveProjectSourceTest {
  private static final String ORG = "sampleOrgId";

  private LiveProjectSource source;
  private ListProjectsResponse listProjectsResponse;

  @Before
  public void setUp() throws IOException {
    CloudResourceManager.Projects projectsObject = mock(CloudResourceManager.Projects.class);
    CloudResourceManager.Projects.List listProjects = mock(
        CloudResourceManager.Projects.List.class);
    GCPProject.setProjectsApiStub(projectsObject);

    listProjectsResponse = new ListProjectsResponse();
    source = new LiveProjectSource(ORG);

    when(projectsObject.list()).thenReturn(listProjects);
    when(listProjects.setPageToken(null)).thenReturn(listProjects);
    when(listProjects.setPageToken(anyString())).thenReturn(listProjects);
    when(listProjects.setFilter(anyString())).thenReturn(listProjects);
    when(listProjects.execute()).thenReturn(this.listProjectsResponse);
  }

  @Test
  public void testBundleSplitIsJustSource() throws Exception {
    PipelineOptions options = PipelineOptionsFactory.create();
    List<LiveProjectSource> bundles = source.splitIntoBundles(0, null);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(0, options);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(1, options);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(100000, options);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(10, null);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);
  }

  @Test
  public void testProducesSortedKeys() throws Exception {
    PipelineOptions options = PipelineOptionsFactory.create();
    assertFalse(source.producesSortedKeys(options));
    assertFalse(source.producesSortedKeys(null));
  }

  @Test
  public void testStart() {
    String projectName = "sampleProjectName";
    String projectId = "sampleProjectId";
    String orgId = ORG;
    ResourceId resourceId = new ResourceId().setId(orgId);
    List<Project> projects = new ArrayList<>();
    String nextPageToken = null;
    GCPProject gcpProject = new GCPProject(projectId, orgId, projectName);
    Project project =
        new Project().setProjectId(projectId).setParent(resourceId).setName(projectName);
    PipelineOptions options = PipelineOptionsFactory.create();
    LiveProjectReader reader;

    this.listProjectsResponse.setProjects(projects);
    this.listProjectsResponse.setNextPageToken(nextPageToken);
    try {
      reader = (LiveProjectReader) this.source.createReader(options);
      assertFalse(reader.start());
      assertNull(reader.getNextPageToken());
      assertTrue(reader.getProjects().isEmpty());
    } catch (IOException e) {
      fail("IOException in reader.start");
    }

    projects = Arrays.asList(project);
    nextPageToken = "samplePageToken";
    this.listProjectsResponse.setProjects(projects);
    this.listProjectsResponse.setNextPageToken(nextPageToken);
    try {
      reader = (LiveProjectReader) this.source.createReader(options);
      assertTrue(reader.start());
      assertEquals(reader.getNextPageToken(), nextPageToken);
      assertEquals(reader.getCurrent(), gcpProject);
    } catch (IOException e) {
      fail("IOException in reader.start");
    }
  }

  @Test
  public void testAdvanceWithoutStart() {
    PipelineOptions options = PipelineOptionsFactory.create();
    LiveProjectReader reader;

    this.listProjectsResponse.setProjects(new ArrayList<Project>(0));
    this.listProjectsResponse.setNextPageToken(null);
    try {
      reader = (LiveProjectReader) this.source.createReader(options);
      assertFalse(reader.advance());
      assertNull(reader.getNextPageToken());
      assertTrue(reader.getProjects().isEmpty());
      reader.getCurrent();
    } catch (IOException e) {
      fail("IOException in reader.start");
    } catch (NoSuchElementException ignored) {
      // test passed.
    }
  }

  @Test
  public void testAdvanceWhenPageTokenNull() {
    String projectName = "sampleProjectName";
    String projectId = "sampleProjectId";
    String orgId = ORG;
    ResourceId resourceId = new ResourceId().setId(orgId);
    GCPProject gcpProject = new GCPProject(projectId, orgId, projectName);
    Project project =
        new Project().setProjectId(projectId).setParent(resourceId).setName(projectName);
    List<Project> projects = Arrays.asList(project);
    PipelineOptions options = PipelineOptionsFactory.create();
    LiveProjectReader reader;

    this.listProjectsResponse.setProjects(projects);
    this.listProjectsResponse.setNextPageToken(null);
    try {
      reader = (LiveProjectReader) this.source.createReader(options);
      assertTrue(reader.start());
      assertEquals(reader.getNextPageToken(), null);
      assertEquals(reader.getCurrent(), gcpProject);
      assertFalse(reader.advance());
      reader.getCurrent();
      fail("No exception when reading from empty source");
    } catch (IOException e) {
      fail("IOException in reader.start");
    } catch (NoSuchElementException ignored) {
      // test passed.
    }
  }

  @Test
  public void testAdvance() {
    String projectName = "sampleProjectName";
    String projectId = "sampleProjectId";
    String orgId = "sampleOrgId";
    ResourceId resourceId = new ResourceId().setId(orgId);
    GCPProject gcpProject = new GCPProject(projectId, orgId, projectName);
    Project project =
        new Project().setProjectId(projectId).setParent(resourceId).setName(projectName);
    List<Project> projects = new ArrayList<>();
    String nextPageToken = null;
    PipelineOptions options = PipelineOptionsFactory.create();
    LiveProjectReader reader;

    projects = Arrays.asList(project);
    nextPageToken = "samplePageToken";
    this.listProjectsResponse.setProjects(projects);
    this.listProjectsResponse.setNextPageToken(nextPageToken);

    try {
      reader = (LiveProjectReader) this.source.createReader(options);
      assertTrue(reader.start());
      assertEquals(reader.getNextPageToken(), nextPageToken);
      assertEquals(reader.getProjects().size(), 1);
      assertEquals(reader.getCurrent(), gcpProject);

      this.listProjectsResponse.setNextPageToken(null);
      assertTrue(reader.advance());
      assertEquals(reader.getProjects().size(), 1);
      assertEquals(reader.getCurrent(), gcpProject);
      assertFalse(reader.advance());
      assertEquals(reader.getProjects().size(), 0);

      projects = Arrays.asList(project, project);
      this.listProjectsResponse.setProjects(projects);
      reader = (LiveProjectReader) this.source.createReader(options);
      assertTrue(reader.start());
      assertEquals(reader.getProjects().size(), 2);
      assertEquals(reader.getCurrent(), gcpProject);
      assertTrue(reader.advance());
      assertEquals(reader.getProjects().size(), 1);
      assertEquals(reader.getCurrent(), gcpProject);

      projects = new ArrayList<>();
      this.listProjectsResponse.setProjects(projects);
      assertFalse(reader.advance());
      assertEquals(reader.getProjects().size(), 0);
      assertFalse(reader.advance());
      assertEquals(reader.getProjects().size(), 0);
    } catch (IOException e) {
      fail("IOException in reader.start");
    }
  }
}
