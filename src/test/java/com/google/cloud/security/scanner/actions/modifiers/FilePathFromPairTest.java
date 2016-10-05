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

package com.google.cloud.security.scanner.actions.modifiers;

import static org.junit.Assert.assertEquals;

import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for FilePathFromPair.
 */
@RunWith(JUnit4.class)
public class FilePathFromPairTest {

  private DoFnTester<KV<List<String>, String>, List<String>> tester;

  @Before
  public void setUp() {
    tester = DoFnTester.of(new FilePathFromPair());
  }

  @Test
  public void testEmptyFilePath() {
    List<String> filePath = new ArrayList<>(0);
    String fileContent = "";
    List<List<String>> result = tester.processBatch(KV.of(filePath, fileContent));
    assertEquals(result.size(), 1);
    assertEquals(result.get(0), filePath);
  }

  @Test
  public void testNonEmptyFilePath() {
    List<String> filePath = Arrays.asList("sample", "file", "path");
    String fileContent = "";
    List<List<String>> result = tester.processBatch(KV.of(filePath, fileContent));
    assertEquals(result.size(), 1);
    assertEquals(result.get(0), filePath);
  }
}
