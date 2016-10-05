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

import com.google.gson.Gson;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Represents a policy binding some GCP resource. */
public class GCPResourcePolicy implements GCPResourceState {
  private PoliciedObject resource;
  private List<PolicyBinding> bindings;

  private static final String POLICY_FILE = "POLICY";

  /**
   * Return the expected file name of the file in which a policy is supposed to reside.
   * @return POLICY_FILE, the expected file name of the checked-in policy files.
   */
  public static String getPolicyFile() {
    return POLICY_FILE;
  }

  /**
   * Construct a policy object from a map of bindings.
   * @param resource The resource this policy binds.
   * @param bindings A map mapping roles to lists of members for that role.
   */
  public GCPResourcePolicy(PoliciedObject resource, Map<String, List<String>> bindings) {
    this.resource = resource;
    this.bindings = new ArrayList<>(bindings.size());
    for (Map.Entry<String, List<String>> entry : bindings.entrySet()) {
      this.bindings.add(new PolicyBinding(entry.getKey(), entry.getValue()));
    }
    sortBindings();
  }

  /**
   * Construct a policy object from a list of PolicyBinding objects.
   * @param resource The resource this policy binds.
   * @param bindings A list of PolicyBinding objects representing the role bindings.
   */
  public GCPResourcePolicy(PoliciedObject resource, List<PolicyBinding> bindings) {
    this.resource = resource;
    this.bindings = bindings;
    sortBindings();
  }

  /**
   * @return The resource this policy binds.
   */
  public GCPResource getResource() {
    return this.resource;
  }

  /**
   * @return A list of PolicyBinding objects representing the role bindings.
   */
  public List<PolicyBinding> getBindings() {
    return this.bindings;
  }


  /**
   * Overriding the equals method to perform deep comparison of resource and bindings.
   * @param o The object to compare with.
   * @return True if the objects' resources and bindings are equal.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof GCPResourcePolicy) {
      GCPResourcePolicy other = (GCPResourcePolicy) o;
      return other.resource.equals(this.resource) && other.bindings.equals(this.bindings);
    }
    return false;
  }

  /**
   * hashCode method needs to be overridden with the equals method.
   * @return Hash code for this object.
   */
  @Override
  public int hashCode() {
    return this.resource.hashCode() + this.bindings.hashCode();
  }

  @Override
  public String toString() {
    return new Gson().toJson(this.bindings);
  }

  @Override
  public void set(GCPResourceState state) throws Exception {
    if (state instanceof GCPResourcePolicy) {
      this.resource.setPolicy((GCPResourcePolicy) state);
    }
    else {
      throw new IllegalArgumentException("Expected policy but received " +
          state.getClass().getName());
    }
  }

  @Override
  public GCPResourceState get() throws Exception {
    return this.resource.getPolicy();
  }

  private void sortBindings() {
    Collections.sort(bindings, new Comparator<PolicyBinding>() {
      @Override
      public int compare(PolicyBinding policyBinding, PolicyBinding t1) {
        return t1.getRole().compareTo(policyBinding.getRole());
      }
    });
  }

  /** Represents a binding of a role to some members */
  public static class PolicyBinding implements Serializable {
    private String role;
    private List<String> members;

    /**
     * Constructor to initialize the binding with a role and an empty member list.
     * @param role The role for this binding.
     */
    public PolicyBinding(String role) {
      this(role, new ArrayList<String>());
    }

    public PolicyBinding(String role, List<String> members) {
      this.role = role;
      this.members = new ArrayList<>(members);
      Collections.sort(this.members);
    }

    /**
     * @return Return the role associated with this binding.
     */
    public String getRole() {
      return role;
    }

    /**
     * @return Return the list of members associated with this binding.
     */
    public List<String> getMembers() {
      return members;
    }

    /**
     * An equals method to perform deep comparison of the role and the member list.
     * @param o The object to compare this binding against.
     * @return True if the roles and the member lists are the same.
     */
    @Override
    public boolean equals(Object o) {
      if (o instanceof PolicyBinding) {
        PolicyBinding other = (PolicyBinding) o;
        return Objects.equals(other.role, this.role) && Objects.equals(other.members, this.members);
      }
      return false;
    }

    /**
     * The hashCode methods need to be overridden along with the equals method.
     * @return The hashCode identifying this binding. Depends only on the role and the member list.
     */
    @Override
    public int hashCode() {
      return this.role.hashCode() + this.members.hashCode();
    }

    @Override
    public String toString() {
      return new Gson().toJson(this);
    }
  }
}
