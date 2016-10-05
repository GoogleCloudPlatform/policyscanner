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

import com.google.api.client.util.Preconditions;
import com.google.api.services.iam.v1.model.ServiceAccountKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Key for a service account in GCP.
 */
public class GCPServiceAccountKey {
  private GCPServiceAccount account;
  private String name;
  private GCPServiceAccountKeyAlgorithm keyAlgorithm;
  private GCPServiceAccountKeyType keyType;
  private String validAfterTime;
  private String validBeforeTime;

  /**
   * Tells which key algorithm is used.
   */
  public enum GCPServiceAccountKeyAlgorithm {
    KEY_ALG_UNSPECIFIED,
    KEY_ALG_RSA_1024,
    KEY_ALG_RSA_2048,
  }

  /**
   * Tells how the key is managed.
   */
  public enum GCPServiceAccountKeyType {
    KEY_TYPE_UNSPECIFIED,
    USER_MANAGED,  // The keys are exported and managed by the user.
    SYSTEM_MANAGED // The keys are managed by Google.
  }

  /**
   * Constructor for GCPServiceAccountKey.
   * @param account The GCPServiceAccount this key is associated with.
   * @param name The name of the key.
   * @param keyAlgorithm The key algorithm used.
   * @param keyType The type of the key: user-managed or system-managed.
   * @param validAfterTime The beginning of the valid period for this key.
   * @param validBeforeTime The end of the valid period for this key.
   */
  public GCPServiceAccountKey(GCPServiceAccount account, String name,
      GCPServiceAccountKeyAlgorithm keyAlgorithm, GCPServiceAccountKeyType keyType,
      String validAfterTime, String validBeforeTime) {
    this.account = Preconditions.checkNotNull(account);
    this.name = Preconditions.checkNotNull(name);
    this.keyAlgorithm = Preconditions.checkNotNull(keyAlgorithm);
    this.keyType = Preconditions.checkNotNull(keyType);
    this.validAfterTime = Preconditions.checkNotNull(validAfterTime);
    this.validBeforeTime = Preconditions.checkNotNull(validBeforeTime);
  }

  /**
   * Constructor for GCPServiceAccountKey using the IAM API's ServiceAccountKey object.
   * @param account The Service Account associated with this key.
   * @param key The ServiceAccountKey object to use for initializing.
   * @param keyType The key type of the key which can only be inferred from the request made.
   */
  public GCPServiceAccountKey(GCPServiceAccount account, ServiceAccountKey key,
      GCPServiceAccountKeyType keyType) {
    this(account,
        key.getName(),
        GCPServiceAccountKey.GCPServiceAccountKeyAlgorithm.valueOf(
            key.getKeyAlgorithm().toUpperCase()
        ),
        keyType,
        key.getValidAfterTime(),
        key.getValidBeforeTime());
  }

  /**
   * Use the IAM API to delete the key.
   * @throws IOException Thrown if there's an error reading from the IAM service account API.
   * @throws GeneralSecurityException Thrown if there's a security error
   * accessing the IAM service account API.
   */
  public void delete() throws IOException, GeneralSecurityException {
    GCPServiceAccount.getServiceAccountsApiStub()
        .keys()
        .delete(
            "projects/"
            + account.getProject()
            + "/serviceAccounts/"
            + account.getId()
            + "/keys/"
            + name
        ).execute();
  }

  /**
   * equals method which only respects the service account
   * the key is associated with and the key name.
   * @param o The object to be compared to this object.
   * @return True if the objects are equal. False otherwise.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof GCPServiceAccountKey) {
      GCPServiceAccountKey other = (GCPServiceAccountKey) o;
      return other.account.equals(account) && other.name.equals(name);
    }
    return false;
  }

  /**
   * hashCode method which only respects the service account
   * the key is associated with and the key name.
   * @return An int representing the hash of the object.
   */
  @Override
  public int hashCode() {
    return Objects.hash(account, name);
  }

  /**
   * Getter for the account field.
   * @return The service account associated with this key.
   */
  public GCPServiceAccount getAccount() {
    return account;
  }

  /**
   * Getter for the name field.
   * @return The name of this key.
   */
  public String getName() {
    return name;
  }

  /**
   * Getter for the validBeforeTime field.
   * @return The end of the valid period of the key.
   */
  public String getValidBeforeTime() {
    return validBeforeTime;
  }

  /**
   * Getter for the validAfterTime field.
   * @return The beginning of the valid period of the key.
   */
  public String getValidAfterTime() {
    return validAfterTime;
  }

  /**
   * Getter for the keyType field.
   * @return Whether the key is user-managed or system-managed.
   */
  public GCPServiceAccountKeyType getKeyType() {
    return keyType;
  }

  /**
   * Getter for the keyAlgorithm field.
   * @return The algorithm used for this key.
   */
  public GCPServiceAccountKeyAlgorithm getKeyAlgorithm() {
    return keyAlgorithm;
  }

  /**
   * Setter for the keyAlgorithm for this key.
   * @param keyAlgorithm The algorithm used for this key.
   */
  public void setKeyAlgorithm(
      GCPServiceAccountKeyAlgorithm keyAlgorithm) {
    this.keyAlgorithm = keyAlgorithm;
  }
}
