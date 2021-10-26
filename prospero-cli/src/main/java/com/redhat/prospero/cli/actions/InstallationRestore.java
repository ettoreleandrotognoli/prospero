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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.impl.repository.curated.ChannelBuilder;
import com.redhat.prospero.installation.Modules;
import com.redhat.prospero.maven.MavenUtils;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.universe.maven.MavenArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.redhat.prospero.api.ArtifactUtils.from;

public class InstallationRestore {

    public static void main(String[] args) throws Exception {

        String targetDir = args[0];
        String metadataBundle = args[1];

        new InstallationRestore().restore(Paths.get(targetDir), Paths.get(metadataBundle));
    }

    public void restore(Path installDir, Path metadataBundleZip) throws ProvisioningException, XmlException, IOException {
        if (installDir.toFile().exists()) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }

        final InstallationMetadata metadataBundle = InstallationMetadata.importMetadata(metadataBundleZip);

        final RepositorySystem repositorySystem = MavenUtils.defaultRepositorySystem();
        final DefaultRepositorySystemSession mavenSession = MavenUtils.getDefaultRepositorySystemSession(repositorySystem);
        final Repository repository = new ChannelBuilder(repositorySystem, mavenSession)
                .setChannels(metadataBundle.getChannels())
                .setRestoringManifest(metadataBundle.getManifest())
                .build();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(repositorySystem, mavenSession, repository);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);
        provMgr.provision(metadataBundle.getProvisioningConfig());
        writeProsperoMetadata(installDir, repoManager, metadataBundle.getChannels());
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws ProvisioningException {
        final Modules modules = new Modules(home);
        Set<MavenArtifact> installed = new HashSet<>();
        for (MavenArtifact resolvedArtifact : maven.resolvedArtfacts()) {
            if (containsArtifact(resolvedArtifact, modules)) {
                installed.add(resolvedArtifact);
            }
        }
        writeManifestFile(home, installed, channels);
    }

    private boolean containsArtifact(MavenArtifact resolvedArtifact, Modules modules) {
        return !modules.find(from(resolvedArtifact)).isEmpty();
    }

    private void writeManifestFile(Path home, Set<MavenArtifact> artifactSet, List<Channel> channels) throws ProvisioningException {
        List<Artifact> artifacts = new ArrayList<>();
        for (MavenArtifact artifact : artifactSet) {
            artifacts.add(from(artifact));
        }

        try {
            ManifestXmlSupport.write(new Manifest(artifacts, home.resolve(InstallationMetadata.MANIFEST_FILE_NAME)));
        } catch (XmlException e) {
            e.printStackTrace();
        }

        // write channels into installation
        final File channelsFile = home.resolve(InstallationMetadata.CHANNELS_FILE_NAME).toFile();
        try {
            com.redhat.prospero.api.Channel.writeChannels(channels, channelsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
