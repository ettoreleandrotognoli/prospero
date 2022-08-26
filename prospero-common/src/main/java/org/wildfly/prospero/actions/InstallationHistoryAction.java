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

package org.wildfly.prospero.actions;

import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.nio.file.Path;
import java.util.List;

import static org.wildfly.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationHistoryAction {

    private final Path installation;
    private final Console console;

    public InstallationHistoryAction(Path installation, Console console) {
        this.installation = installation;
        this.console = console;
    }

    public List<ArtifactChange> compare(SavedState savedState) throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getChangesSince(savedState);
    }

    public List<SavedState> getRevisions() throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getRevisions();
    }

    public void rollback(SavedState savedState, MavenSessionManager mavenSessionManager) throws OperationException, ProvisioningException {
        InstallationMetadata metadata = new InstallationMetadata(installation);
        metadata = metadata.rollback(savedState);
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();
        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(installation, prosperoConfig, mavenSessionManager)
                .setConsole(console)
                .setRestoreManifest(metadata.getManifest())
                .build();

        try {
            System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
            galleonEnv.getProvisioningManager().provision(metadata.getGalleonProvisioningConfig());
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }

        // TODO: handle errors - write final state? revert rollback?
    }
}
