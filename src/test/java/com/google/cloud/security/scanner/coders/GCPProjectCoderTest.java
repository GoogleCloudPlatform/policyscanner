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
import com.google.cloud.security.scanner.primitives.GCPProject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for GCPProjectCoder */
@RunWith(JUnit4.class)
public class GCPProjectCoderTest {
  private SerializableCoder<GCPProject> coder;
  private Context context;
  private String projectId = "sampleProjectId";
  private String orgId = "sampleOrgId";
  private String projectName = "sampleProjectName";

  @Before
  public void setUp() {
    coder = SerializableCoder.of(GCPProject.class);
    context = new Context(false);
  }

  private void testObjectIntegrity(GCPProject project) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayInputStream in;

    coder.encode(project, out, context);
    in = new ByteArrayInputStream(out.toByteArray());
    assertEquals(project, coder.decode(in, context));
  }

  @Test
  public void testEncodeAllFieldsNulled() throws IOException {
    try {
      testObjectIntegrity(new GCPProject(null, null, null));
      fail("GCPProject cannot hold a null ID");
    } catch (IOException | IllegalArgumentException ignored) {
      // test passed.
    }
  }

  @Test
  public void testProjectIdNulled() throws IOException {
    try {
      testObjectIntegrity(new GCPProject(null, orgId, projectName));
      fail("GCPProject cannot hold a null ID");
    } catch (IOException | IllegalArgumentException ignored) {
      // test passed.
    }
  }

  @Test
  public void testOrgIdNulled() throws IOException {
    testObjectIntegrity(new GCPProject(projectId, null, projectName));
  }

  @Test
  public void testProjectNameNulled() throws IOException {
    testObjectIntegrity(new GCPProject(projectId, orgId, null));
  }

  @Test
  public void testProjectNameAndIdNulled() throws IOException {
    try {
      testObjectIntegrity(new GCPProject(null, orgId, null));
      fail("GCPProject cannot hold a null ID");
    } catch (IOException | IllegalArgumentException ignored) {
      // test passed.
    }
  }

  @Test
  public void testProjectAndOrgIdNulled() throws IOException {
    try {
      testObjectIntegrity(new GCPProject(null, null, projectName));
      fail("GCPProject cannot hold a null ID");
    } catch (IOException | IllegalArgumentException ignored) {
      // test passed.
    }
  }

  @Test
  public void testProjectNameAndOrgIdNulled() throws IOException {
    try {
      testObjectIntegrity(new GCPProject(null, orgId, projectName));
      fail("GCPProject cannot hold a null ID");
    } catch (IOException | IllegalArgumentException ignored) {
      // test passed.
    }
  }
}
