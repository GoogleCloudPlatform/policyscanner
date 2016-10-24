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

package com.google.cloud.security.scanner.actions.messengers;

import static org.junit.Assert.assertEquals;

import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.actions.modifiers.TagStateWithSource.StateSource;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for UnmatchedStatesMessenger
 */
@RunWith(JUnit4.class)
public class UnmatchedStatesMessengerTest {

  private DoFnTester<KV<String, Iterable<GCPResource>>, String> tester;

  @Before
  public void setUp() {
    this.tester = DoFnTester.of(new UnmatchedStatesMessenger());
  }

  @Test
  public void testNoUnmatchedStates() {
    List<KV<String, Iterable<GCPResource>>> inputs =
        Arrays.asList();
    assertEquals(0, this.tester.processBatch(inputs).size());
  }

  @Test
  public void testOneUnmatchedDesiredState() {
    List<GCPResource> projectList = new ArrayList<>(1);
    GCPResource project = new GCPProject("someProjectId", "someOrgId", "projectName");
    projectList.add(project);
    List<KV<String, Iterable<GCPResource>>> inputs =
        Arrays.asList(KV.of(StateSource.DESIRED.toString(), (Iterable<GCPResource>)projectList));

    assertEquals(constructMessage(StateSource.DESIRED, project),
        this.tester.processBatch(inputs).get(0));
  }

  @Test
  public void testOneUnmatchedLiveState() {
    List<GCPResource> projectList = new ArrayList<>(1);
    GCPResource project = new GCPProject("someProjectId", "someOrgId", "projectName");
    projectList.add(project);
    List<KV<String, Iterable<GCPResource>>> inputs =
        Arrays.asList(KV.of(StateSource.LIVE.toString(), (Iterable<GCPResource>)projectList));

    assertEquals(constructMessage(StateSource.LIVE, project),
        this.tester.processBatch(inputs).get(0));
  }

  private String constructMessage(StateSource stateSource, GCPResource resource) {
    return stateSource.toString() + ":" + resource.getId();
  }
}
