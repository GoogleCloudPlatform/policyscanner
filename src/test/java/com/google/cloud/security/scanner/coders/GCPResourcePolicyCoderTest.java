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

package com.google.cloud.security.scanner.coders;

import static org.junit.Assert.assertEquals;

import com.google.cloud.dataflow.sdk.coders.Coder.Context;
import com.google.cloud.dataflow.sdk.coders.SerializableCoder;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for GCPResourcePolicyCoder */
@RunWith(JUnit4.class)
public class GCPResourcePolicyCoderTest {
  private static final String OWNER_ROLE = "OWNERS";
  private static final String VIEWER_ROLE = "VIEWERS";
  private SerializableCoder<GCPResourcePolicy> coder;
  private Context context;
  Map<String, List<String>> mapBindings;
  List<PolicyBinding> bindings;

  private GCPProject project;

  @Before
  public void setUp() {
    this.project = new GCPProject("sampleProjectId", "sampleOrgId", "sampleProjectName");
    this.coder = SerializableCoder.of(GCPResourcePolicy.class);
    this.context = new Context(false);
    this.mapBindings = new HashMap<>();
    this.bindings = new ArrayList<>(2);

    List<String> ownerList = new ArrayList<>(2);
    List<String> viewerList = new ArrayList<>(1);
    ownerList.add("user:sampleOwner1");
    ownerList.add("group:sampleOwner2");
    viewerList.add("serviceAccount:sampleViewer1");

    this.mapBindings.put(OWNER_ROLE, ownerList);
    this.mapBindings.put(VIEWER_ROLE, viewerList);

    this.bindings.add(new PolicyBinding(OWNER_ROLE, ownerList));
    this.bindings.add(new PolicyBinding(VIEWER_ROLE, viewerList));
  }

  public void testObjectIntegrity(GCPResourcePolicy policy) throws IOException {
    ByteArrayInputStream in;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    coder.encode(policy, out, this.context);
    in = new ByteArrayInputStream(out.toByteArray());
    assertEquals(coder.decode(in, this.context), policy);
  }

  @Test
  public void testMapConstructor() throws IOException {
    GCPResourcePolicy policy = new GCPResourcePolicy(this.project, this.mapBindings);
    testObjectIntegrity(policy);
  }

  @Test
  public void testBindingsConstructor() throws IOException {
    GCPResourcePolicy policy = new GCPResourcePolicy(this.project, this.bindings);
    testObjectIntegrity(policy);
  }

  @Test
  public void testEmptyMapConstructor() throws IOException {
    GCPResourcePolicy policy =
        new GCPResourcePolicy(this.project, new HashMap<String, List<String>>());
    testObjectIntegrity(policy);
  }

  @Test
  public void testEmptyBindingsConstructor() throws IOException {
    GCPResourcePolicy policy = new GCPResourcePolicy(this.project, new ArrayList<PolicyBinding>());
    testObjectIntegrity(policy);
  }
}
