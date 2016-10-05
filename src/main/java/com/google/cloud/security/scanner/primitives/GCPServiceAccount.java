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

import static com.google.cloud.security.scanner.primitives.GCPServiceAccountKey.GCPServiceAccountKeyType.SYSTEM_MANAGED;
import static com.google.cloud.security.scanner.primitives.GCPServiceAccountKey.GCPServiceAccountKeyType.USER_MANAGED;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.Iam.Projects.ServiceAccounts;
import com.google.api.services.iam.v1.IamScopes;
import com.google.api.services.iam.v1.model.ListServiceAccountKeysResponse;
import com.google.api.services.iam.v1.model.ListServiceAccountsResponse;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.api.services.iam.v1.model.ServiceAccountKey;
import com.google.cloud.security.scanner.primitives.GCPServiceAccountKey.GCPServiceAccountKeyType;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Represents a Service Account in GCP
 */
public class GCPServiceAccount implements GCPResource {
  private static ServiceAccounts serviceAccountsApiStub;
  private String id;
  private String project;

  /**
   * Set the API Stub for accessing the IAM Service Accounts API.
   * Intended for mocking.
   * @param serviceAccounts
   */
  public static void setServiceAccountsApiStub(ServiceAccounts serviceAccounts) {
    serviceAccountsApiStub = serviceAccounts;
  }

  /**
   * Get the API stub for accessing the IAM Service Accounts API.
   * @return ServiceAccounts api stub for accessing the IAM Service Accounts API.
   * @throws IOException Thrown if there's an IO error initializing the api connection.
   * @throws GeneralSecurityException Thrown if there's a security error
   * initializing the connection.
   */
  public static ServiceAccounts getServiceAccountsApiStub() throws IOException, GeneralSecurityException {
    if (serviceAccountsApiStub == null) {
      HttpTransport transport;
      GoogleCredential credential;
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      transport = GoogleNetHttpTransport.newTrustedTransport();
      credential = GoogleCredential.getApplicationDefault(transport, jsonFactory);
      if (credential.createScopedRequired()) {
        Collection<String> scopes = IamScopes.all();
        credential = credential.createScoped(scopes);
      }
      serviceAccountsApiStub = new Iam.Builder(transport, jsonFactory, credential)
          .build()
          .projects()
          .serviceAccounts();
    }
    return serviceAccountsApiStub;
  }

  /**
   * Return the service accounts belonging to a project.
   * @param project The project ID of the project whose service accounts are to be listed.
   * @return A list of GCPServiceAccount objects representing the service accounts to be listed.
   * @throws IOException Thrown if there's an error reading from the IAM service account API.
   * @throws GeneralSecurityException Thrown if there's a security error
   * accessing the IAM service account API.
   */
  public static List<GCPServiceAccount> getServiceAccounts(String project)
      throws IOException, GeneralSecurityException {
    ListServiceAccountsResponse response = getServiceAccountsApiStub()
        .list("projects/" + project)
        .execute();
    List<GCPServiceAccount> accounts = new ArrayList<>(response.getAccounts().size());
    for (ServiceAccount account : response.getAccounts()) {
      accounts.add(new GCPServiceAccount(account.getUniqueId(), account.getProjectId()));
    }
    return accounts;
  }

  /**
   * Constructor for GCPServiceAccount.
   * @param id The id of the service account.
   * @param project The project this service account belongs to.
   */
  public GCPServiceAccount(String id, String project) {
    this.id = id;
    this.project = project;
  }

  /**
   * Get all the keys associated with this service account.
   * @return A List of GCPServiceAccountKey objects representing the keys
   * asssociated with this service account.
   * @throws GeneralSecurityException Thrown if there's a security error when getting the keys.
   * @throws IOException Thrown if there's some IO error when getting the keys.
   */
  public List<GCPServiceAccountKey> getKeys() throws GeneralSecurityException, IOException {
    ServiceAccounts.Keys.List listRequest = getServiceAccountsApiStub()
        .keys()
        .list("projects/" + project + "/serviceAccounts/" + id);

    List<GCPServiceAccountKey> keys = getKeysOfType(listRequest, USER_MANAGED);
    keys.addAll(getKeysOfType(listRequest, SYSTEM_MANAGED));
    return keys;
  }

  /**
   * Getter for the service account id.
   * @return The id of the service account.
   */
  @Override
  public String getId() {
    return id;
  }

  /**
   * Getter for the project field.
   * @return The project ID of the project this service account belongs to.
   */
  public String getProject() {
    return project;
  }

  private List<GCPServiceAccountKey> getKeysOfType(ServiceAccounts.Keys.List listRequest,
      GCPServiceAccountKeyType keyType) throws IOException {
    listRequest.setKeyTypes(Arrays.asList(keyType.toString()));
    ListServiceAccountKeysResponse response = listRequest.execute();
    List<GCPServiceAccountKey> keys = new ArrayList<>(response.getKeys().size());
    for (ServiceAccountKey key : response.getKeys()) {
      keys.add(new GCPServiceAccountKey(this, key, keyType));
    }
    return keys;
  }
}
