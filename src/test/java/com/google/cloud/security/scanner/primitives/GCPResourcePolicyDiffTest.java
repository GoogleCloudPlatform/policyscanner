package com.google.cloud.security.scanner.primitives;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test the correctness of the policy diff output */
@RunWith(JUnit4.class)
public class GCPResourcePolicyDiffTest {
  private static final String OWNER_ROLE = "OWNERS";
  private static final String VIEWER_ROLE = "VIEWERS";
  private GCPProject policyScannerProject;
  private GCPProject testProject;

  private List<GCPResourcePolicy.PolicyBinding> desiredBindings;
  private List<GCPResourcePolicy.PolicyBinding> liveBindings;

  private List<String> desiredOwnerList;
  private List<String> desiredViewerList;
  private List<String> liveOwnerList;
  private List<String> liveViewerList;

  @Before
  public void setUpBeforeEachTest() {
    this.policyScannerProject = new GCPProject("PolicyScanner", "Google", "policy-scanner");
    this.testProject = new GCPProject("TestProject", "Google", "test-project");

    this.desiredBindings = new ArrayList<>();
    this.liveBindings = new ArrayList<>();

    this.desiredOwnerList = new ArrayList<>();
    this.desiredViewerList = new ArrayList<>();
    this.liveOwnerList = new ArrayList<>();
    this.liveViewerList = new ArrayList<>();
  }

  @Test
  public void testEmptyPolicySetsAreEqual() {
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, new ArrayList<String>()));
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, new ArrayList<String>()));
    GCPResourcePolicy desiredPolicy =
        new GCPResourcePolicy(this.policyScannerProject, this.desiredBindings);

    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, new ArrayList<String>()));
    this.liveBindings.add(
        new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, new ArrayList<String>()));
    GCPResourcePolicy livePolicy = new GCPResourcePolicy(this.testProject, this.liveBindings);

    GCPResourcePolicyDiff diff = GCPResourcePolicyDiff.diff(desiredPolicy, livePolicy);

    assertEquals(0, diff.getDeltas().size());
  }

  @Test
  public void testPolicySetsAreEqual() {
    this.desiredOwnerList.add("user:a@google.com");
    this.desiredOwnerList.add("user:b@google.com");
    this.desiredViewerList.add("user:x@google.com");
    this.desiredViewerList.add("user:y@google.com");
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.desiredOwnerList));
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.desiredViewerList));
    GCPResourcePolicy desiredPolicy =
        new GCPResourcePolicy(this.policyScannerProject, this.desiredBindings);

    this.liveOwnerList.add("user:a@google.com");
    this.liveOwnerList.add("user:b@google.com");
    this.liveViewerList.add("user:x@google.com");
    this.liveViewerList.add("user:y@google.com");
    liveBindings.add(new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.liveOwnerList));
    liveBindings.add(new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.liveViewerList));
    GCPResourcePolicy livePolicy = new GCPResourcePolicy(this.testProject, this.liveBindings);

    GCPResourcePolicyDiff diff = GCPResourcePolicyDiff.diff(desiredPolicy, livePolicy);

    assertEquals(0, diff.getDeltas().size());
  }

  @Test
  public void testDiffIsCorrectForAddedAndRemovedBindings() {
    this.desiredOwnerList.add("user:a@google.com");
    this.desiredOwnerList.add("user:b@google.com");
    this.desiredViewerList.add("user:x@google.com");
    this.desiredViewerList.add("user:y@google.com");
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.desiredOwnerList));
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.desiredViewerList));
    GCPResourcePolicy desiredPolicy =
        new GCPResourcePolicy(this.policyScannerProject, this.desiredBindings);

    this.liveOwnerList.add("user:a@google.com");
    this.liveOwnerList.add("user:removeme@google.com");
    this.liveViewerList.add("user:x@google.com");
    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.liveOwnerList));
    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.liveViewerList));
    GCPResourcePolicy livePolicy = new GCPResourcePolicy(this.testProject, this.liveBindings);

    GCPResourcePolicyDiff diff = GCPResourcePolicyDiff.diff(desiredPolicy, livePolicy);
    Set<GCPResourcePolicyDiff.PolicyBindingDelta> diffDeltas = new HashSet<>();
    diffDeltas.addAll(diff.getDeltas());

    Set<GCPResourcePolicyDiff.PolicyBindingDelta> actual = new HashSet<>();
    actual.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            OWNER_ROLE,
            "user:b@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.REMOVED));
    actual.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            VIEWER_ROLE,
            "user:y@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.REMOVED));
    actual.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            OWNER_ROLE,
            "user:removeme@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.ADDED));

    assertEquals(actual, diffDeltas);
  }

  @Test
  public void testDiffIsCorrectForRemovedBindings() {
    this.desiredOwnerList.add("user:a@google.com");
    this.desiredOwnerList.add("user:b@google.com");
    this.desiredViewerList.add("user:x@google.com");
    this.desiredViewerList.add("user:y@google.com");
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.desiredOwnerList));
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.desiredViewerList));
    GCPResourcePolicy desiredPolicy =
        new GCPResourcePolicy(this.policyScannerProject, this.desiredBindings);

    this.liveOwnerList.add("user:a@google.com");
    this.liveViewerList.add("user:x@google.com");
    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.liveOwnerList));
    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.liveViewerList));
    GCPResourcePolicy livePolicy = new GCPResourcePolicy(this.testProject, this.liveBindings);

    GCPResourcePolicyDiff diff = GCPResourcePolicyDiff.diff(desiredPolicy, livePolicy);
    Set<GCPResourcePolicyDiff.PolicyBindingDelta> actualDiff = new HashSet<>();
    actualDiff.addAll(diff.getDeltas());

    Set<GCPResourcePolicyDiff.PolicyBindingDelta> expectedDiff = new HashSet<>();
    expectedDiff.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            OWNER_ROLE,
            "user:b@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.REMOVED));
    expectedDiff.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            VIEWER_ROLE,
            "user:y@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.REMOVED));

    assertEquals(expectedDiff, actualDiff);
  }

  @Test
  public void testDiffIsCorrectForAddedBindings() {
    this.desiredOwnerList.add("user:b@google.com");
    this.desiredViewerList.add("user:y@google.com");
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.desiredOwnerList));
    this.desiredBindings.add(
        new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.desiredViewerList));
    GCPResourcePolicy desiredPolicy =
        new GCPResourcePolicy(this.policyScannerProject, this.desiredBindings);

    this.liveOwnerList.add("user:a@google.com");
    this.liveOwnerList.add("user:b@google.com");
    this.liveViewerList.add("user:x@google.com");
    this.liveViewerList.add("user:y@google.com");
    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(OWNER_ROLE, this.liveOwnerList));
    this.liveBindings.add(new GCPResourcePolicy.PolicyBinding(VIEWER_ROLE, this.liveViewerList));
    GCPResourcePolicy livePolicy = new GCPResourcePolicy(this.testProject, this.liveBindings);

    GCPResourcePolicyDiff diff = GCPResourcePolicyDiff.diff(desiredPolicy, livePolicy);
    Set<GCPResourcePolicyDiff.PolicyBindingDelta> actualDiff = new HashSet<>();
    actualDiff.addAll(diff.getDeltas());

    Set<GCPResourcePolicyDiff.PolicyBindingDelta> expectedDiff = new HashSet<>();
    expectedDiff.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            OWNER_ROLE,
            "user:a@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.ADDED));
    expectedDiff.add(
        new GCPResourcePolicyDiff.PolicyBindingDelta(
            VIEWER_ROLE,
            "user:x@google.com",
            GCPResourcePolicyDiff.PolicyBindingDelta.Action.ADDED));

    assertEquals(expectedDiff, actualDiff);
  }
}
