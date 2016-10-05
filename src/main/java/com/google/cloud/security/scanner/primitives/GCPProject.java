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

package com.google.cloud.security.scanner.primitives;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects;
import com.google.api.services.cloudresourcemanager.CloudResourceManagerScopes;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.api.services.cloudresourcemanager.model.SetIamPolicyRequest;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents a GCP project. */
public class GCPProject implements PoliciedObject {
  private static Projects projectApiStub;

  private String name;
  private String id;
  private String orgId;

  /**
   * Setter for the CRM Projects API stub.
   * @param project The CRM Projects API stub.
   */
  public static synchronized void setProjectsApiStub(Projects project) {
    GCPProject.projectApiStub = project;
  }

  /**
   * Return the Projects api object used for accessing the Cloud Resource Manager Projects API.
   * @return Projects api object used for accessing the Cloud Resource Manager Projects API
   * @throws GeneralSecurityException Thrown if there's a permissions error.
   * @throws IOException Thrown if there's an IO error initializing the API object.
   */
  public static synchronized Projects getProjectsApiStub()
      throws GeneralSecurityException, IOException {
    if (projectApiStub != null) {
      return projectApiStub;
    }
    HttpTransport transport;
    GoogleCredential credential;
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    transport = GoogleNetHttpTransport.newTrustedTransport();
    credential = GoogleCredential.getApplicationDefault(transport, jsonFactory);
    if (credential.createScopedRequired()) {
      Collection<String> scopes = CloudResourceManagerScopes.all();
      credential = credential.createScoped(scopes);
    }
    projectApiStub = new CloudResourceManager
        .Builder(transport, jsonFactory, credential)
        .build()
        .projects();
    return projectApiStub;
  }

  /**
   * Construct a GCPProject object with only the project ID.
   * Other fields are left as null.
   * @param id The ID of the project. This is different from the project name.
   * @throws IllegalArgumentException If the project id is null.
   */
  public GCPProject(String id) throws IllegalArgumentException {
    this(id, null, null);
  }

  /**
   * Construct a GCPProject object with the project and org IDs.
   * @param id The ID of the project. This is different from the project name.
   * @param orgId The ID of the org this project belongs to.
   * @throws IllegalArgumentException If the project id is null.
   */
  public GCPProject(String id, String orgId) throws IllegalArgumentException {
    this(id, orgId, null);
  }

  /**
   * Construct a GCPProject object with the project and org IDs, and the project name.
   * @param id The id of the project.
   * @param orgId The id of the org this project belongs to.
   * @param name The display name of the project.
   * @throws IllegalArgumentException If the project id is null.
   */
  public GCPProject(String id, String orgId, String name) throws IllegalArgumentException {
    if (id == null) {
      throw new IllegalArgumentException("GCPProject's id cannot be null");
    }
    if (orgId == null) {
      orgId = "";
    }
    if (name == null) {
      name = "";
    }
    this.id = id;
    this.orgId = orgId;
    this.name = name;
  }

  /**
   * @return The display name of the project.
   */
  public String getName() {
    return this.name;
  }

  /**
   * @return The ID of the org this project belongs to.
   */
  public String getOrgId() {
    return this.orgId;
  }

  /**
   * @return The ID of the project.
   */
  @Override
  public String getId() {
    return this.id;
  }

  /**
   * Get the IAM policy binding this project.
   * @return The GCPResourcePolicy object which represents the policy binding this project.
   * @throws IOException Thrown if there's an IO error reading the policy.
   * @throws GeneralSecurityException Thrown if there's a permissions error reading the policy.
   */
  @Override
  public GCPResourcePolicy getPolicy() throws IOException, GeneralSecurityException {
    Map<String, List<String>> bindings = new HashMap<>();
    Policy policy = null;
    try {
      policy = getProjectsApiStub()
          .getIamPolicy(this.id, new GetIamPolicyRequest())
          .execute();
    } catch (GeneralSecurityException gse) {
      throw new GeneralSecurityException(
          "Cannot fetch IAM policy for project + " + id + "\nMessage: " + gse.getMessage()
      );
    }
    if (policy != null && policy.getBindings() != null) {
      for (Binding binding : policy.getBindings()) {
        bindings.put(binding.getRole(), binding.getMembers());
      }
      return new GCPResourcePolicy(this, bindings);
    }
    return null;
  }

  /**
   * Set the IAM policy that will bind this project.
   * @param sourceGCPResourcePolicy The policy this project is to be bound to.
   * @throws IOException Thrown if there's an IO error setting the policy.
   * @throws GeneralSecurityException Thrown if there's a permissions error setting the policy.
   */
  @Override
  public void setPolicy(GCPResourcePolicy sourceGCPResourcePolicy)
      throws IOException, GeneralSecurityException {
    Policy policy = new Policy();
    List<Binding> bindings = new ArrayList<>(sourceGCPResourcePolicy.getBindings().size());
    SetIamPolicyRequest request = new SetIamPolicyRequest();

    for (PolicyBinding sourceBinding : sourceGCPResourcePolicy.getBindings()) {
      Binding binding = new Binding();
      binding.setRole(sourceBinding.getRole());
      binding.setMembers(sourceBinding.getMembers());
      bindings.add(binding);
    }
    policy.setBindings(bindings);
    request.setPolicy(policy);
    getProjectsApiStub().setIamPolicy(this.id, request).execute();
  }

  /**
   * Comparator for GCPProject objects. It only checks for equality of the project IDs.
   * @param o The object to compare with.
   * @return True if they have the same project ID, false otherwise.
   */
  @Override
  public boolean equals(Object o) {
    return (o instanceof GCPProject) && ((GCPProject) o).id.equals(this.id);
  }

  /**
   * hashCode method which only respects the project's id.
   * @return Hash code for this object.
   */
  @Override
  public int hashCode() {
    return this.id.hashCode();
  }

  /**
   * toString method which only returns the id of the project.
   * @return The id of the project.
   */
  @Override
  public String toString() {
    return this.id;
  }
}
