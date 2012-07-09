/*
 * Copyright Myrrix Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.client;

import java.io.File;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import net.myrrix.common.MyrrixTest;
import net.myrrix.web.Runner;
import net.myrrix.web.RunnerConfiguration;

public abstract class AbstractClientTest extends MyrrixTest {

  private static File savedModelFile = null;

  private Runner runner;
  private ClientRecommender client;

  protected abstract String getTestDataPath();

  protected boolean useSecurity() {
    return false;
  }

  protected final Runner getRunner() {
    return runner;
  }

  protected final ClientRecommender getClient() {
    return client;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    File tempDir = getTestTempDir();
    File testDataDir = new File(getTestDataPath());
    Preconditions.checkState(testDataDir.exists() && testDataDir.isDirectory(),
                             "%s is not an existing directory", testDataDir.getAbsolutePath());

    if (savedModelFile == null) {
      File[] srcDataFiles = testDataDir.listFiles(new PatternFilenameFilter("[^.].*"));
      if (srcDataFiles != null) {
        for (File srcDataFile : srcDataFiles) {
          File destFile = new File(tempDir, srcDataFile.getName());
          Files.copy(srcDataFile, destFile);
        }
      }
    } else {
      Files.copy(savedModelFile, new File(tempDir, "model.bin"));
    }

    RunnerConfiguration runnerConfig = new RunnerConfiguration();
    runnerConfig.setInstanceID(0L);
    runnerConfig.setPort(8080);
    if (useSecurity()) {
      runnerConfig.setSecurePort(8443);
      runnerConfig.setKeystorePassword("changeit");
      runnerConfig.setKeystoreFile(new File("testdata/keystore").getAbsoluteFile());
      runnerConfig.setUserName("foo");
      runnerConfig.setPassword("bar");
    }
    runnerConfig.setLocalInputDir(tempDir);

    runner = new Runner(runnerConfig);
    runner.call();

    boolean clientSecure = runnerConfig.getKeystoreFile() != null;
    int clientPort = clientSecure ? runnerConfig.getSecurePort() : runnerConfig.getPort();
    MyrrixClientConfiguration clientConfig = new MyrrixClientConfiguration();
    clientConfig.setHost("localhost");
    clientConfig.setPort(clientPort);
    clientConfig.setSecure(clientSecure);
    clientConfig.setKeystorePassword(runnerConfig.getKeystorePassword());
    clientConfig.setKeystoreFile(runnerConfig.getKeystoreFile());
    clientConfig.setUserName(runnerConfig.getUserName());
    clientConfig.setPassword(runnerConfig.getPassword());
    client = new ClientRecommender(clientConfig);

    client.refresh(null);

    while (!client.isReady()) {
      try {
        Thread.sleep(5000L);
      } catch (InterruptedException e) {
        // continue
      }
    }
  }

  @Override
  @After
  public void tearDown() throws Exception {
    if (runner != null) {
      runner.close();
    }

    if (savedModelFile == null) {
      savedModelFile = File.createTempFile("model-", ".bin");
      savedModelFile.deleteOnExit();
      File modelBinFile = new File(getTestTempDir(), "model.bin");
      if (modelBinFile.exists()) {
        Files.copy(modelBinFile, savedModelFile);
      }
    }

    super.tearDown();
  }

  @AfterClass
  public static void tearDownClass() {
    if (savedModelFile != null) {
      savedModelFile.delete();
      savedModelFile = null;
    }
  }

}