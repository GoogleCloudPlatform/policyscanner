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

package com.google.cloud.security.scanner.actions.messengers;

import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicyDiff;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicyDiff.PolicyBindingDelta;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicyDiff.PolicyBindingDelta.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Produce an output message from the differing policies.
 */
public class MessageConstructor{
 
  private GCPResource projectResource;
  private GCPResourcePolicy desiredPolicy;
  private GCPResourcePolicy livePolicy;
  private GCPResourcePolicyDiff diff;

  /**
   * Construct a MessageConstructor to create an output message.
   * @param projectResource The project that is being scanned.
   * @param desiredPolicy The controlled policy for the project.
   * @param livePolicy The actual policy found in the project.
   * @param diff The difference between the desired policy and live policy.
   */
  public MessageConstructor(
      GCPResource projectResource,
      GCPResourcePolicy desiredPolicy,
      GCPResourcePolicy livePolicy,
      GCPResourcePolicyDiff diff) {
    this.projectResource = projectResource;
    this.desiredPolicy = desiredPolicy;
    this.livePolicy = livePolicy;
    this.diff = diff;
  }

  /**
   * Construct the output message.
   */  
  public String constructMessage() {
    // message header
    String message = "PROJECT NAME: " + projectResource.toString() + "\n\n";

    // known good policy
    message += "KNOWN GOOD STATE:\n";
    List<PolicyBinding> desiredPolicyBindings = desiredPolicy.getBindings();
    for (PolicyBinding binding : desiredPolicyBindings) {
      List<String> members = binding.getMembers();
      for (String member : members) {
        message += binding.getRole() + " " + member + "\n";
      }
    }
    message += "\n";

    // live policy
    message += "LIVE STATE:\n";
    List<PolicyBinding> livePolicyBindings = livePolicy.getBindings();
    for (PolicyBinding binding : livePolicyBindings) {
      List<String> members = binding.getMembers();
      for (String member : members) {
        message += binding.getRole() + " " + member + "\n";
      }
    }
    message += "\n";

    // diff
    message += "DIFFERENCE:\n";
    List<PolicyBindingDelta> sortedDeltas = new ArrayList<PolicyBindingDelta>(diff.getDeltas());
    if (sortedDeltas.size() < 1) {
      return message + "No difference found between known good and live policies\n";
    }

    Collections.<PolicyBindingDelta>sort(sortedDeltas);
    for (PolicyBindingDelta delta : sortedDeltas) {
      if (delta.getAction() == Action.ADDED) {
        message += "Found unexpected ";
      } else if (delta.getAction() == Action.REMOVED) {
        message += "Not found ";
      } else {
        throw new IllegalArgumentException("Unspecified action for the policy diff");
      }
      message += delta.getRole() + " ";
      message += delta.getMember() + " ";
      message += "\n";
    }

    return message;
  }
}


