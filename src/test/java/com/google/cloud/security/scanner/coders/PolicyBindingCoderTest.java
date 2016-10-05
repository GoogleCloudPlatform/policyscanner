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
import static org.junit.Assert.fail;

import com.google.cloud.dataflow.sdk.coders.Coder.Context;
import com.google.cloud.dataflow.sdk.coders.SerializableCoder;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** */
@RunWith(JUnit4.class)
public class PolicyBindingCoderTest {
  private SerializableCoder<PolicyBinding> coder;
  private static final String OWNER_ROLE = "OWNERS";
  private static final String VIEWER_ROLE = "VIEWERS";
  private Context context;
  private List<String> ownerList;
  private List<String> viewerList;

  @Before
  public void setUp() {
    this.coder = SerializableCoder.of(PolicyBinding.class);
    this.context = new Context(false);
    this.ownerList = new ArrayList<>(2);
    this.viewerList = new ArrayList<>(1);

    ownerList.add("user:sampleOwner1");
    ownerList.add("group:sampleOwner2");
    viewerList.add("serviceAccount:sampleViewer1");
  }

  public void testObjectIntegrity(PolicyBinding binding) throws IOException {
    ByteArrayInputStream in;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    coder.encode(binding, out, this.context);
    in = new ByteArrayInputStream(out.toByteArray());
    assertEquals(coder.decode(in, context), binding);
  }

  @Test
  public void testEmptyMemberBinding() {
    PolicyBinding binding = new PolicyBinding(OWNER_ROLE, new ArrayList<String>());
    try {
      testObjectIntegrity(binding);
    } catch (IOException e) {
      fail("Exception in empty member binding");
    }
  }

  @Test
  public void testEmptyRoleBinding() {
    PolicyBinding binding = new PolicyBinding("", new ArrayList<String>());
    try {
      testObjectIntegrity(binding);
    } catch (IOException e) {
      fail("Exception in empty role binding");
    }
  }

  @Test
  public void testOwnerBinding() {
    PolicyBinding binding = new PolicyBinding(OWNER_ROLE, ownerList);
    try {
      testObjectIntegrity(binding);
    } catch (IOException e) {
      fail("Exception in owner binding");
    }
  }

  @Test
  public void testViewerBinding() {
    PolicyBinding binding = new PolicyBinding(VIEWER_ROLE, viewerList);
    try {
      testObjectIntegrity(binding);
    } catch (IOException e) {
      fail("Exception in viewer binding");
    }
  }
}
