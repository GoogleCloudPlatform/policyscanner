/**
 * Copyright 2016 Google Inc.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>    http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.security.scanner.primitives;

import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Represents the delta between two policies. */
public class GCPResourcePolicyDiff implements Serializable {
  private final GCPResourcePolicy firstPolicy;
  private final GCPResourcePolicy secondPolicy;
  private final ImmutableList<PolicyBindingDelta> deltas; // the differences in the two policies

  /**
   * Construct a GCPResourcePolicyDiff object from two GCPResourcePolicy objects.
   *
   * @param firstPolicy The first policy object.
   * @param secondPolicy The second policy object.
   * @return A GCPResourcePolicyDiff object that represents the difference between the two policies.
   */
  public static GCPResourcePolicyDiff diff(
      GCPResourcePolicy firstPolicy, GCPResourcePolicy secondPolicy) {
    Set<PolicyBindingDelta> firstPolicyBindings = flattenBindings(firstPolicy);
    Set<PolicyBindingDelta> secondPolicyBindings = flattenBindings(secondPolicy);
    Set<PolicyBindingDelta> removedBindings = new HashSet<>(firstPolicyBindings);
    removedBindings.removeAll(secondPolicyBindings);
    Set<PolicyBindingDelta> addedBindings = new HashSet<>(secondPolicyBindings);
    addedBindings.removeAll(firstPolicyBindings);

    ImmutableList.Builder<PolicyBindingDelta> deltas = ImmutableList.builder();
    for (PolicyBindingDelta delta : removedBindings) {
      deltas.add(
          new PolicyBindingDelta(
              delta.getRole(), delta.getMember(), PolicyBindingDelta.Action.REMOVED));
    }

    for (PolicyBindingDelta delta : addedBindings) {
      deltas.add(
          new PolicyBindingDelta(
              delta.getRole(), delta.getMember(), PolicyBindingDelta.Action.ADDED));
    }

    return new GCPResourcePolicyDiff(firstPolicy, secondPolicy, deltas.build());
  }

  /**
   * Constructor for GCPResourcePolicyDiff
   *
   * @param firstPolicy The first policy object.
   * @param secondPolicy The second policy object.
   * @param deltas The list of differences between the two objects.
   */
  GCPResourcePolicyDiff(
      GCPResourcePolicy firstPolicy,
      GCPResourcePolicy secondPolicy,
      List<PolicyBindingDelta> deltas) {
    this.firstPolicy = Preconditions.checkNotNull(firstPolicy);
    this.secondPolicy = Preconditions.checkNotNull(secondPolicy);
    this.deltas = ImmutableList.copyOf(Preconditions.checkNotNull(deltas));
  }

  /**
   * Getter for deltas.
   *
   * @return The list of differences between the two policies.
   */
  public ImmutableList<PolicyBindingDelta> getDeltas() {
    return deltas;
  }

  /**
   * Getter for secondPolicy.
   *
   * @return The second policy object in the comparison.
   */
  public GCPResourcePolicy getSecondPolicy() {
    return secondPolicy;
  }

  /**
   * Getter for firstPolicy.
   *
   * @return the first policy object in the comparison.
   */
  public GCPResourcePolicy getFirstPolicy() {
    return firstPolicy;
  }

  private static Set<PolicyBindingDelta> flattenBindings(GCPResourcePolicy policy) {
    Set<PolicyBindingDelta> flattenedPolicyBindings = new HashSet<>();
    for (PolicyBinding binding : policy.getBindings()) {
      for (String member : binding.getMembers()) {
        flattenedPolicyBindings.add(
            new PolicyBindingDelta(
                binding.getRole(), member, PolicyBindingDelta.Action.UNASSIGNED));
      }
    }
    return flattenedPolicyBindings;
  }

  /**
   * Represents one difference between two GCPResourcePolicy objects. Each difference is either one
   * member being present or absent from a role in the second policy.
   */
  public static class PolicyBindingDelta implements Serializable, Comparable<PolicyBindingDelta> {

    public enum Action {
      ADDED, // the binding was action in the second policy
      REMOVED, // the binding was removed in the second policy
      UNASSIGNED // an action hasn't been assigned yet
    }

    private final Action action;
    private final String role;
    private final String member;

    /**
     * Constructor for PolicyBindingDelta.
     *
     * @param role The role in the policy from which a member was removed, or to which it was added.
     * @param member The member which was removed from or added to a role.
     * @param action Whether the member was added to or removed from the role in the second policy
     *     in the comparison.
     */
    public PolicyBindingDelta(String role, String member, Action action) {
      this.action = Preconditions.checkNotNull(action);
      this.role = Preconditions.checkNotNull(role);
      this.member = Preconditions.checkNotNull(member);
    }

    /**
     * Check object equality based on action, member, and role.
     *
     * @param o the object for which we are checking equality
     * @return whether the PolicyBindingDelta is equal to this
     */
    @Override
    public boolean equals(Object o) {
      if (o instanceof PolicyBindingDelta) {
        PolicyBindingDelta otherDelta = (PolicyBindingDelta) o;
        return getAction() == otherDelta.getAction()
            && Objects.equals(getMember(), otherDelta.getMember())
            && Objects.equals(getRole(), otherDelta.getRole());
      }
      return false;
    }

    /**
     * Override hashcode using the PolicyBindingDelta properties
     *
     * @return the hashcode for the PolicyBindingDelta
     */
    @Override
    public int hashCode() {
      return Objects.hash(getAction(), getRole(), getMember());
    }

    /**
     * Comparator for ordering PolicyBindingDeltas so that the ADDED deltas come first. Deltas with
     * the same action are sorted in alphabetical order of role. Deltas with the same action and
     * role are sorted in the alphabetic order of members.
     *
     * @param delta The object to compare this delta to.
     * @return a negative integer, zero, or a positive integer as the first argument is equal to, or
     *     greater than the second.
     */
    @Override
    public int compareTo(PolicyBindingDelta delta) {
      if (this == delta) return 0;
      int result = this.getAction().compareTo(delta.getAction());
      if (result != 0) return result;
      result = this.getRole().compareTo(delta.getRole());
      return result;
    }

    /**
     * Getter for action.
     *
     * @return ADDED if the member was added to the role in the second policy, and REMOVED if the
     *     member was removed from the role in the second policy.
     */
    public Action getAction() {
      return action;
    }

    /**
     * Getter for role.
     *
     * @return The role in the policy from which a member was removed, or to which it was added.
     */
    public String getRole() {
      return role;
    }

    /**
     * Getter for member.
     *
     * @return Whether the member was added to or removed from the role in the second policy in the
     *     comparison.
     */
    public String getMember() {
      return member;
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append("PolicyBindingDelta=[")
          .append("role=")
          .append(getRole())
          .append(",")
          .append("member=")
          .append(getMember())
          .append(",")
          .append("action=")
          .append(getAction())
          .append(",")
          .append("]")
          .toString();
    }
  }
}
