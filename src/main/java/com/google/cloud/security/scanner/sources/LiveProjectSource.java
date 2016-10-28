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

import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.SerializableCoder;
import com.google.cloud.dataflow.sdk.io.BoundedSource;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.security.scanner.primitives.GCPProject;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/** Source for listing projects through the CRM API. */
public class LiveProjectSource extends BoundedSource<GCPProject> {
  private static final long SIZE_ESTIMATE = 200;
  private static final String DELETE_PREFIX = "DELETE";
  private String orgId;

  /**
   * Constructor for LiveProjectSource.
   * @param orgId The orgId ID of the orgId to read from.
   * null to read from all visible orgs.
   */
  public LiveProjectSource(String orgId) {
    this.orgId = orgId;
  }

  /**
   * Getter for the field orgId.
   * @return The ID of the organization whose projects are to be read.
   */
  public String getOrgId() {
    return this.orgId;
  }

  /**
   * This function just returns the same source as a list, and does not
   * actually split the load into several bundles.
   * @param desiredBundleSizeBytes The desired bundle size. Not used.
   * @param options Pipeline options. Not used
   * @return A list containing this source as its only element.
   */
  @Override
  public List<LiveProjectSource> splitIntoBundles(
      long desiredBundleSizeBytes, PipelineOptions options) throws Exception {
    List<LiveProjectSource> projectSources = new ArrayList<>(1);
    projectSources.add(this);
    return projectSources;
  }

  /**
   * Currently returns a hardcoded value.
   * Get the estimated size of the data that will be read by this source.
   * @param options Pipeline options. Not used.
   * @return The size estimate of the data to be read by this source.
   */
  @Override
  public long getEstimatedSizeBytes(PipelineOptions options) throws Exception {
    return SIZE_ESTIMATE;
  }

  /**
   * This function does not give any guarantees about whether its output will be sorted or not.
   * @param options Pipeline options. Not used.
   * @return Returns true if the source's output is always sorted. False otherwise.
   */
  @Override
  public boolean producesSortedKeys(PipelineOptions options) throws Exception {
    return false;
  }

  /**
   * Create a new reader that will read from this source.
   * @param options Pipeline options. Not used.
   * @return A BoundedReader object to read from this source.
   */
  @Override
  public BoundedReader<GCPProject> createReader(PipelineOptions options) throws IOException {
    return new LiveProjectReader(this);
  }

  /**
   * Validate whether this source can function or not.
   */
  @Override
  public void validate() {}

  /**
   * Get the default coder to use for this source's output.
   * @return The default coder to use for this source's output.
   */
  @Override
  public Coder<GCPProject> getDefaultOutputCoder() {
    return SerializableCoder.of(GCPProject.class);
  }

  /** Reader for LiveProjectSource */
  public static class LiveProjectReader extends BoundedReader<GCPProject> {
    private LiveProjectSource source;
    private List<GCPProject> projects;
    private String nextPageToken;

    /**
     * Getter for the projects field.
     * @return Return the projects currently in the reader's queue.
     */
    List<GCPProject> getProjects() {
      return projects;
    }

    /**
     * Getter for the nextPageToken field.
     * @return Return the nextPageToken to be used in the
     * next request to get the next page of projects.
     */
    String getNextPageToken() {
      return nextPageToken;
    }

    /**
     * Construct a LiveProjectReader object.
     * @param source The source this reader is supposed to read from.
     */
    public LiveProjectReader(LiveProjectSource source) {
      this.source = source;
      this.projects = new ArrayList<>();
      this.nextPageToken = null;
    }

    /**
     * Initialize the reader so it can begin reading files.
     * @return True if the initialization succeeded. False otherwise.
     */
    @Override
    public boolean start() throws IOException {
      return refreshProjects(null);
    }

    /**
     * Advance the reader so it can read the next file in the queue.
     * @return True if there are still more files to be read. False otherwise.
     */
    @Override
    public boolean advance() throws IOException {
      if (this.projects.size() > 1) {
        this.projects.remove(0);
        return true;
      } else if (this.projects.size() == 1) {
        this.projects.remove(0);
        // if token is null the last page read was the last page to be read.
        return (this.nextPageToken != null) && refreshProjects(this.nextPageToken);
      }
      return false;
    }

    /**
     * Get the next file in queue.
     * @return A GCPProject object representing the project that is read.
     * @throws NoSuchElementException If the file can't be read from the CRM API.
     */
    @Override
    public GCPProject getCurrent() throws NoSuchElementException {
      if (this.projects.isEmpty()) {
        throw new NoSuchElementException("No more GCPProject objects");
      }
      return this.projects.get(0);
    }

    /**
     * Close this reader.
     */
    @Override
    public void close() throws IOException {}

    /**
     * Return the source this reader is reading from.
     * @return The source this reader is reading from.
     */
    @Override
    public BoundedSource<GCPProject> getCurrentSource() {
      return this.source;
    }

    private boolean refreshProjects(String nextPageToken) throws IOException {
      ListProjectsResponse projectListResponse;
      Projects.List projectsList;
      try {
        projectsList = GCPProject.getProjectsApiStub().list();
        if (nextPageToken != null) {
          projectsList = projectsList.setPageToken(nextPageToken);
        }
        if (source.getOrgId() != null) {
            projectsList = projectsList
                .setFilter("parent.type:organization parent.id:" + source.getOrgId());
        }
        projectListResponse = projectsList.execute();
      } catch (GeneralSecurityException gse) {
        throw new IOException("Cannot get projects. Access denied");
      }
      List<Project> projects = projectListResponse.getProjects();

      for (Project project : projects) {
        String orgId = null;
        if (project.getParent() != null) {
          orgId = project.getParent().getId();
        }
        if (project.getLifecycleState() == null
            || project.getLifecycleState().startsWith(DELETE_PREFIX)) {
          continue;
        }
        this.projects.add(new GCPProject(project.getProjectId(), orgId, project.getName()));
      }
      this.nextPageToken = projectListResponse.getNextPageToken();

      return !this.projects.isEmpty();
    }
  }
}
