package com.google.cloud.security.scanner.servlets;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.cloud.security.scanner.sources.GCSFilesSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/** Handler class for the Dataflow local runner test endpoint. */
public class GitGCSSyncApp extends HttpServlet {

  private String bucket;

  /**
   * Handler for the GET request to this app.
   * @param req The request object.
   * @param resp The response object.
   * @throws IOException Thrown if there's an error reading from one of the APIs.
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    PrintWriter out = resp.getWriter();
    this.bucket = System.getenv("POLICY_SCANNER_GIT_SYNC_DEST_BUCKET");
    String repository = System.getenv("POLICY_SCANNER_GIT_REPOSITORY_URL");
    String repoName = System.getenv("POLICY_SCANNER_GIT_REPOSITORY_NAME");
    File gitDir = new File(repoName);
    if (gitDir.exists()) {
      delete(gitDir);
    }
    try {
      Git.cloneRepository()
          .setURI(repository)
          .call();
    } catch (GitAPIException e) {
      throw new IOException(e.getMessage());
    }
    syncToGCS(repoName + "/.git/");
    out.write("Copied files to GCS!");
  }

  private void syncToGCS(String repoFolder) throws IOException {
    File repo = new File(repoFolder);
    if (!repo.isDirectory()) {
      throw new IOException("Expected a git directory but got a file.");
    }
    if (!Arrays.asList(repo.list()).contains(".git")) {
      throw new IOException("Expected a git directory but got a regular directory.");
    }
    Storage gcs = null;
    try {
      gcs = GCSFilesSource.getStorageApiStub();
    } catch (GeneralSecurityException e) {
      throw new IOException("Cannot instantiate the GCS API object.");
    }
    // if you don't want to write the files in the repo to the root of the bucket,
    // you can specify a prefix here ending with "/", and the files will be written
    // inside that folder in the bucket. e.g. "repoFolder/"
    String prefix = "";
    recursiveGCSSync(repo, prefix, gcs);
  }

  private void recursiveGCSSync(File repo, String prefix, Storage gcs) throws IOException {
    for (String name : repo.list()) {
      if (!name.equals(".git")) {
        File file = new File(repo.getPath(), name);
        if (file.isDirectory()) {
          recursiveGCSSync(file, prefix + file.getName() + "/", gcs);
        } else {
          InputStream inputStream = new FileInputStream(repo.getPath() + "/" + file.getName());
          InputStreamContent mediaContent = new InputStreamContent("text/plain", inputStream);
          mediaContent.setLength(file.length());
          StorageObject objectMetadata = new StorageObject()
              .setName(prefix + file.getName());
          gcs.objects().insert(bucket, objectMetadata, mediaContent).execute();
        }
      }
    }
  }

  private void delete(File file) {
    if (file.isDirectory()) {
      for (String fileName : file.list()) {
        delete(new File(file.getPath(), fileName));
      }
    }
    file.delete();
  }
}
