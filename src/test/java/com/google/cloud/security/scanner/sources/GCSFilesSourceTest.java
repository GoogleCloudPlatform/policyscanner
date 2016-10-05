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

package com.google.cloud.security.scanner.sources;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.cloud.dataflow.sdk.io.BoundedSource.BoundedReader;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.values.KV;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** Tests for GCSFilesSource */
@RunWith(JUnit4.class)
public class GCSFilesSourceTest {
  private static final String BUCKET = "cloud-security-scanner-test";
  private static final String REPOSITORY = "sampleOrgId";

  private GCSFilesSource source;

  private Storage gcs = mock(Storage.class);

  private Storage.Buckets buckets = mock(Storage.Buckets.class);
  private Storage.Buckets.Get bucketGet = mock(Storage.Buckets.Get.class);

  private Storage.Objects objects = mock(Storage.Objects.class);
  private Storage.Objects.Get objectGet = mock(Storage.Objects.Get.class);
  private Storage.Objects.List objectList = mock(Storage.Objects.List.class);

  @Before
  public void setUp() throws IOException, GeneralSecurityException {
    when(this.objectList.setPageToken(anyString())).thenReturn(this.objectList);
    when(this.objectList.setPageToken(null)).thenReturn(this.objectList);
    when(this.objectList.setPrefix(anyString())).thenReturn(this.objectList);

    when(this.objects.list(anyString())).thenReturn(this.objectList);
    when(this.objects.get(anyString(), anyString())).thenReturn(this.objectGet);
    when(this.gcs.objects()).thenReturn(this.objects);

    when(this.buckets.get(anyString())).thenReturn(this.bucketGet);
    when(this.gcs.buckets()).thenReturn(this.buckets);

    GCSFilesSource.setStorageApiStub(gcs);
    source = new GCSFilesSource(BUCKET, REPOSITORY);
  }

  @Test
  public void testRepository() throws Exception {
    assertEquals(source.getRepository(), REPOSITORY);
  }

  @Test
  public void testBundleSplitIsJustSource() throws Exception {
    PipelineOptions options = PipelineOptionsFactory.create();
    List<GCSFilesSource> bundles = source.splitIntoBundles(0, null);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(0, options);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(1, options);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(100000, options);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);

    bundles = source.splitIntoBundles(10, null);
    assertEquals(bundles.size(), 1);
    assertEquals(bundles.get(0), source);
  }

  @Test
  public void testProducesSortedKeys() throws Exception {
    PipelineOptions options = PipelineOptionsFactory.create();
    assertFalse(source.producesSortedKeys(options));
    assertFalse(source.producesSortedKeys(null));
  }

  public void setUpGetFileContent(final String testString, final ByteArrayOutputStream[] out) {
    try {
      doAnswer(
              new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                  out[0] = (ByteArrayOutputStream) invocationOnMock.getArguments()[0];
                  out[0].write(testString.getBytes(UTF_8));
                  return null;
                }
              })
          .when(this.objectGet)
          .executeMediaAndDownloadTo(any(ByteArrayOutputStream.class));
    } catch (IOException e) {
      fail("Could not set up getFileContent");
    }
  }

  @Test
  public void testGetFileContent() throws Exception {
    final String testString = "sample file content";
    final ByteArrayOutputStream[] out = new ByteArrayOutputStream[1];
    setUpGetFileContent(testString, out);

    this.source.getFileContent("any/file/path");
    assertEquals(out[0].toString(), testString);
  }

  private void setUpGetFilesPage(String objectName) {
    setUpGetFilesPage(objectName, 1);
  }

  private void setUpGetFilesPage(String objectName, int fileCount) {
    List<String> objectNames = new ArrayList<>(fileCount);
    for (int i = 0; i < fileCount; ++i) {
      objectNames.add(objectName);
    }
    setUpGetFilesPage(objectNames);
  }

  private void setUpGetFilesPage(List<String> objectNames) {
    // these are final classes, so use fakes instead of mocks.
    List<StorageObject> fakeItems = new ArrayList<>();
    for (int i = 0; i < objectNames.size(); ++i) {
      StorageObject fakeObject = new StorageObject().setName(objectNames.get(i));
      fakeItems.add(fakeObject);
    }

    Objects listObjects = new Objects().setItems(fakeItems);

    try {
      when(this.objectList.execute()).thenReturn(listObjects);
    } catch (IOException e) {
      fail("Failed to setup getFilesPage");
    }
  }

  @Test
  public void testGetFilesPageWithDirNames() {
    String orgDir = REPOSITORY + this.source.getDirDelimiter();
    String projectDir = orgDir + "sampleProjectId" + this.source.getDirDelimiter();
    String stateFile = projectDir + "sampleStateFile";
    List<String> fileNames = new ArrayList<>();
    try {
      setUpGetFilesPage(Arrays.asList(orgDir, projectDir));
      assertNull(this.source.getFilesPage(fileNames));
      assertEquals(fileNames.size(), 0);

      setUpGetFilesPage(Arrays.asList(orgDir, projectDir, stateFile));
      assertNull(this.source.getFilesPage(fileNames));
      assertEquals(fileNames.size(), 1);
      assertEquals(fileNames.get(0), stateFile);
    } catch (IOException | GeneralSecurityException e) {
      fail("Failed to list a page of files");
    }
  }

  @Test
  public void testGetFilesPage() {
    String objectName = REPOSITORY + this.source.getDirDelimiter() + "sampleProject";
    List<String> fileNames = new ArrayList<>();
    setUpGetFilesPage(objectName);
    try {
      assertNull(this.source.getFilesPage(fileNames));
      assertEquals(fileNames.size(), 1);
      assertEquals(fileNames.get(0), objectName);

      assertNull(this.source.getFilesPage(fileNames, "testToken"));
      assertEquals(fileNames.size(), 2);
      assertEquals(fileNames.get(0), objectName);
    } catch (IOException | GeneralSecurityException e) {
      fail("Failed to list a page of files");
    }
  }

  @Test
  public void testReaderStart() {
    String objectName = REPOSITORY + this.source.getDirDelimiter() + "sampleProject";
    PipelineOptions options = PipelineOptionsFactory.create();
    setUpGetFilesPage(objectName);
    try {
      assertTrue(this.source.createReader(options).start());
    } catch (IOException e) {
      fail();
    }
  }

  @Test
  public void testReaderGetCurrent() {
    String projectName = "sampleProject";
    String objectName = REPOSITORY + this.source.getDirDelimiter() + projectName;
    String fileContent = "sample file content";
    ByteArrayOutputStream[] out = new ByteArrayOutputStream[1];
    PipelineOptions options = PipelineOptionsFactory.create();

    setUpGetFilesPage(objectName);
    setUpGetFileContent(fileContent, out);

    try {
      BoundedReader<KV<List<String>, String>> reader = this.source.createReader(options);
      reader.start();
      KV<List<String>, String> value = reader.getCurrent();
      assertEquals(value.getKey().size(), 2);
      assertEquals(value.getKey().get(0), REPOSITORY);
      assertEquals(value.getKey().get(1), projectName);
      assertEquals(value.getValue(), fileContent);
    } catch (IOException e) {
      fail();
    }
  }

  @Test
  public void testReaderAdvance() {
    String objectName = REPOSITORY + this.source.getDirDelimiter() + "sampleProject";
    PipelineOptions options = PipelineOptionsFactory.create();
    BoundedReader<KV<List<String>, String>> reader;

    try {
      setUpGetFilesPage(objectName, 0);
      reader = this.source.createReader(options);
      assertFalse(reader.start());

      setUpGetFilesPage(objectName, 1);
      reader = this.source.createReader(options);
      assertTrue(reader.start());
      assertFalse(reader.advance());

      setUpGetFilesPage(objectName, 2);
      reader = this.source.createReader(options);
      assertTrue(reader.start());
      assertTrue(reader.advance());
      assertFalse(reader.advance());
    } catch (IOException e) {
      fail();
    }
  }
}
