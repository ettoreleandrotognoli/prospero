/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package integration;

import com.redhat.prospero.cli.actions.GalleonProvision;
import com.redhat.prospero.cli.actions.Update;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/*
 * Currently requires a local repos to run
 * TODO: use channels based on central repo once the metadata issue is fixed (MVNCENTRAL-7012)
 */
public class SimpleInstallationTest {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();

    @Before
    public void setUp() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @Test
    public void installWildflyCore() throws Exception {
        final Path channelFile = prepareChannelFile("local-repo-desc.json");

        new GalleonProvision().installFeaturePack("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", OUTPUT_PATH.toString(), channelFile.toString());

        // verify installation with manifest file is present
        assertTrue(OUTPUT_PATH.resolve("manifest.xml").toFile().exists());
        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());


    }

    @Test
    public void updateWildflyCore() throws Exception {
        final Path channelFile = prepareChannelFile("local-repo-desc.json");
        new GalleonProvision().installFeaturePack("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", OUTPUT_PATH.toString(), channelFile.toString());

        prepareChannelFile(OUTPUT_PATH.resolve("channels.json"), "local-repo-desc.json", "local-updates-repo-desc.json");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.1.Final", wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws XmlException {
        final File manifestFile = OUTPUT_PATH.resolve("manifest.xml").toFile();
        return ManifestXmlSupport.parse(manifestFile).getArtifacts().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst();
    }

    private Path prepareChannelFile(String channelDescriptor) throws IOException {
        final Path channelFile = Files.createTempFile("channels", "json");
        channelFile.toFile().deleteOnExit();

        return prepareChannelFile(channelFile, channelDescriptor);
    }

    private Path prepareChannelFile(Path channelFile, String... channelDescriptor) throws IOException {
        List<URL> repoUrls = Arrays.stream(channelDescriptor).map(d->this.getClass().getClassLoader().getResource(d)).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("[");
        for (int i=0; i<repoUrls.size(); i++) {
            sb.append(String.format("{\"name\":\"%s\",\"url\":\"%s\"}", "repo-"+i, repoUrls.get(i)));
            if (i < (repoUrls.size()-1)) {
                sb.append(",");
            }
        }
        sb.append("]");

        try (FileWriter fw = new FileWriter(channelFile.toFile())) {
            fw.write(sb.toString());
        }
        return channelFile;
    }
}