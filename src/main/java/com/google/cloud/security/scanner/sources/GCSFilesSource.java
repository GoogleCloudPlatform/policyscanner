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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Objects;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.KvCoder;
import com.google.cloud.dataflow.sdk.coders.ListCoder;
import com.google.cloud.dataflow.sdk.coders.StringUtf8Coder;
import com.google.cloud.dataflow.sdk.io.BoundedSource;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.exceptions.BucketAccessException;
import com.google.common.base.Preconditions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

/** Encapsulation for reading configuration files from GCS directories */
public class GCSFilesSource extends BoundedSource<KV<List<String>, String>> {
  private static final long SIZE_ESTIMATE = 200;
  private static final String DIR_DELIMITER = "/";
  private static Storage gcs;
  private String bucket;
  private String repository;

  public static synchronized void setStorageApiStub(Storage gcs) {
    GCSFilesSource.gcs = gcs;
  }

  /**
   * @return Return the Google Cloud Storage API object.
   * @throws GeneralSecurityException Thrown if there's a permissions error
   *  initializing the API object.
   * @throws IOException Thrown if there's an IO error initializing the object.
   */
  public static synchronized Storage getStorageApiStub()
      throws GeneralSecurityException, IOException {
    if (gcs == null) {
      gcs = constructStorageApiStub();
    }
    return gcs;
  }

  /**
   * Constructor for GCSFileSource.
   * @param bucket The bucket where the configuration files reside.
   * @param repository The root directory where the files reside.
   * @throws GeneralSecurityException Thrown if there's a permissions error using the GCS API.
   * @throws IOException Thrown if there's a IO error using the GCS API.
   */
  public GCSFilesSource(String bucket, String repository)
      throws GeneralSecurityException, IOException {
    this.bucket = bucket;
    this.repository = repository;

    try {
      // test that the bucket actually exists.
      getStorageApiStub().buckets().get(bucket).execute();
    } catch (GoogleJsonResponseException gjre) {
      String msgFormat = new StringBuilder()
          .append("Can't access bucket \"gs://%s\".\n\n")
          .append("1. Check that your appengine-web.xml has the correct environment variables.\n")
          .append("2. Check your project IAM settings: the Compute Engine service account should\n")
          .append("   have Editor access (or at least Storage Object Creator) if you're running\n")
          .append("   on production. If you are running this locally, your user account or group\n")
          .append("   should be granted Storage Object Creator.\n")
          .append("3. Try re-authing your application-default credentials with this command:\n\n")
          .append("   $ gcloud auth application-default login\n\n")
          .append("More details:\n%s").toString();
      String message = String.format(msgFormat, bucket, gjre.getContent());
      throw new BucketAccessException(message);
    }
  }

  /**
   * @return The bucket this source is supposed to read from.
   */
  public String getBucket() {
    return this.bucket;
  }

  /**
   * @return The repository where the configuration files reside.
   */
  public String getRepository() {
    return this.repository;
  }

  /**
   * In GCS there are no directories; everything is a file.
   * However, each file is treated like it belongs in a directory based on its prefix.
   * The delimiter decides the path of the file by delimiting the names of supposed directories.
   * @return The delimiter used to mark different directories.
   */
  public String getDirDelimiter() {
    return DIR_DELIMITER;
  }


  /**
   * Get the the contents of a file.
   * This function does not append the repository prefix to the file name.
   * @param filePath The full path of the file.
   * @return The contents of the file.
   * @throws GeneralSecurityException Thrown if there's a permissions error using the GCS API.
   * @throws IOException Thrown if there's a IO error using the GCS API.
   */
  String getFileContent(String filePath) throws GeneralSecurityException, IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    getStorageApiStub().objects().get(this.bucket, filePath).executeMediaAndDownloadTo(out);
    return out.toString();
  }

  /**
   * Obtain a list of files that reside in the repository directory.
   * This will yield all files, no matter how deeply nested they are in the directory.
   * @param fileNames The list which will be populated with the file names.
   * @return The token which will be used to get the next page of file list.
   * @throws GeneralSecurityException Thrown if there's a permissions error using the GCS API.
   * @throws IOException Thrown if there's a IO error using the GCS API.
   */
  String getFilesPage(List<String> fileNames) throws GeneralSecurityException, IOException {
    return getFilesPage(fileNames, null);
  }

  /**
   * Obtain a list of files that reside in the repository directory.
   * This will yield all files, no matter how deeply nested they are in the directory.
   * @param fileNames The list which will be populated with the file names.
   * @param nextPageToken The token used to get the next page of the file list.
   * @return The token which will be used to get the next page of the file list.
   * @throws GeneralSecurityException Thrown if there's a permissions error using the GCS API.
   * @throws IOException Thrown if there's a IO error using the GCS API.
   */
  String getFilesPage(List<String> fileNames, String nextPageToken)
      throws GeneralSecurityException, IOException {
    Objects objects =
        getStorageApiStub()
            .objects()
            .list(this.bucket)
            .setPageToken(nextPageToken)
            .setPrefix(this.repository + this.getDirDelimiter())
            .execute();
    for (int i = 0; i < objects.getItems().size(); ++i) {
      String fileName = objects.getItems().get(i).getName();
      if (!fileName.endsWith(getDirDelimiter())) {
        fileNames.add(fileName);
      }
    }
    return objects.getNextPageToken();
  }

  /**
   * This function just returns the same source as a list, and does not
   * actually split the load into several bundles.
   * @param desiredBundleSizeBytes The desired bundle size. Not used.
   * @param options Pipeline options. Not used
   * @return A list containing this source as its only element.
   */
  @Override
  public List<GCSFilesSource> splitIntoBundles(long desiredBundleSizeBytes,
      PipelineOptions options) {
    ArrayList<GCSFilesSource> bundle = new ArrayList<>(1);
    bundle.add(this);
    return bundle;
  }

  /**
   * Currently returns a hardcoded value.
   * Get the estimated size of the data that will be read by this source.
   * @param options Pipeline options. Not used.
   * @return The size estimate of the data to be read by this source.
   */
  @Override
  public long getEstimatedSizeBytes(PipelineOptions options) {
    return SIZE_ESTIMATE;
  }

  /**
   * This function does not give any guarantees about whether its output will be sorted or not.
   * @param options Pipeline options. Not used.
   * @return Returns true if the source's output is always sorted. False otherwise.
   */
  @Override
  public boolean producesSortedKeys(PipelineOptions options) {
    return false;
  }

  /**
   * Create a new reader that will read from this source.
   * @param options Pipeline options. Not used.
   * @return A BoundedReader object to read from this source.
   */
  @Override
  public BoundedReader<KV<List<String>, String>> createReader(PipelineOptions options) {
    return new GCSFilesReader(this);
  }

  /**
   * Validate whether this source can function or not.
   */
  @Override
  public void validate() {
    Preconditions.checkNotNull(bucket, "bucket");
    Preconditions.checkNotNull(repository, "repository");
  }

  /**
   * Get the default coder to use for this source's output.
   * @return The default coder to use for this source's output.
   */
  @Override
  public Coder<KV<List<String>, String>> getDefaultOutputCoder() {
    return KvCoder.of(ListCoder.of(StringUtf8Coder.of()), StringUtf8Coder.of());
  }

  private static Storage constructStorageApiStub() throws GeneralSecurityException, IOException {
    JsonFactory jsonFactory = new JacksonFactory();
    HttpTransport transport;
    transport = GoogleNetHttpTransport.newTrustedTransport();
    GoogleCredential credential = GoogleCredential.getApplicationDefault(transport, jsonFactory);
    if (credential.createScopedRequired()) {
      Collection<String> scopes = StorageScopes.all();
      credential = credential.createScoped(scopes);
    }
    return new Storage.Builder(transport, jsonFactory, credential)
        .setApplicationName("GCS Samples")
        .build();
  }

  /** Reader for reading data from GCSFilesSource */
  public static class GCSFilesReader extends BoundedReader<KV<List<String>, String>> {
    private GCSFilesSource source;
    private String nextPageToken;
    private List<String> currentFiles;

    /**
     * Construct a GCSFilesReader object.
     * @param source The source this reader is supposed to read from.
     */
    public GCSFilesReader(GCSFilesSource source) {
      this.source = source;
      this.nextPageToken = null;
      this.currentFiles = new ArrayList<>();
    }

    /**
     * Get more files to read.
     * @return True if there are more files to be read. False otherwise.
     */
    boolean refreshCurrentFiles() {
      try {
        this.nextPageToken = this.source.getFilesPage(currentFiles, null);
      } catch (IOException | GeneralSecurityException e) {
        return false;
      }
      return !this.currentFiles.isEmpty();
    }

    /**
     * Initialize the reader so it can begin reading files.
     * @return True if the initialization succeeded. False otherwise.
     */
    @Override
    public boolean start() {
      return refreshCurrentFiles();
    }

    /**
     * Advance the reader so it can read the next file in the queue.
     * @return True if there are still more files to be read. False otherwise.
     */
    @Override
    public boolean advance() {
      this.currentFiles.remove(0);
      return !((currentFiles.isEmpty())
          && ((this.nextPageToken == null) || !refreshCurrentFiles()));
    }

    /**
     * Get the next file in queue.
     * @return A Key-Value pair where the key is a list of strings representing the path of
     * the file and the value is a string representing the content of the file.
     * @throws NoSuchElementException If the file can't be read from the GCS API.
     */
    @Override
    public KV<List<String>, String> getCurrent() throws NoSuchElementException {
      String filePath = this.currentFiles.get(0);
      String fileContent = null;
      try {
        fileContent = this.source.getFileContent(filePath);
      } catch (IOException ioe) {
        throw new NoSuchElementException(
            "Object " + filePath + " not found in bucket " + this.source.bucket);
      } catch (GeneralSecurityException gse) {
        throw new NoSuchElementException(
            "Cannot access object "
                + filePath
                + " in bucket "
                + this.source.bucket
                + " due to security reasons");
      }
      List<String> splitPath = Arrays.asList(filePath.split(this.source.getDirDelimiter()));
      return KV.of(splitPath, fileContent);
    }

    /**
     * Close this reader.
     */
    @Override
    public void close(){}

    /**
     * Return the source this reader is reading from.
     * @return The source this reader is reading from.
     */
    @Override
    public BoundedSource<KV<List<String>, String>> getCurrentSource() {
      return this.source;
    }
  }
}
