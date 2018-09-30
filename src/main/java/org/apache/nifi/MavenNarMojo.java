/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugin.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * avoid exporting the dependencies jars in Dependencies Directory "META-INF/bundled-dependencies", but use maven URLs instead.
 * 
 * @author Kemix Koo (kemix_koo@163.com)
 *
 */
@Mojo(name = "nar-mvn", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class MavenNarMojo extends NarMojo {

    /**
     * File name of Maven URLs for dependencies, which will be generated in Dependencies Directory "META-INF/bundled-dependencies".
     */
    private static final String MVN_URI_FILENAME = "dependencies.mvn";

    /**
     * Folder of maven repository folder.
     */
    @Parameter(property = "nar.maven.outputRepositoryLocation", defaultValue = "${nar.maven.repository.folder}")
    protected File outputRepositoryLocation;

    /**
     * If true for the snapshot dependencies, will be generated in dependencies file and export the them to maven repository also.
     */
    @Parameter(property = "nar.maven.includeSnapshotInFile", defaultValue = "true")
    protected boolean includeSnapshotInFile;

    /**
     * when existed the maven dependencies file, if true, won't overwrite it.
     */
    @Parameter(property = "nar.maven.fileAppend", defaultValue = "false")
    protected boolean fileAppend;

    @Override
    public void execute() throws MojoExecutionException {
        generateDependenciesFile();
        super.execute();
    }

    protected void copyDependencies() throws MojoExecutionException {
        // won't copy any more
    }

    protected void generateDependenciesFile() throws MojoExecutionException {
        File dependenciesFile = new File(getDependenciesDirectory(), MVN_URI_FILENAME);
        File parentFile = dependenciesFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(dependenciesFile, fileAppend))) {
            DependencyStatusSets dss = getDependencySets(this.failOnMissingClassifierArtifact);
            Set<Artifact> artifacts = dss.getResolvedDependencies();
            List<Artifact> sortedArtifacts = new ArrayList<>(artifacts);
            Collections.sort(sortedArtifacts);
            for (Artifact artifact : sortedArtifacts) {
                if ("jar".equalsIgnoreCase(artifact.getType())) { // only for jar
                    if (artifact.isSnapshot()) {
                        if (includeSnapshotInFile) {
                            writeAndExport(writer, artifact);
                        } else { // copy still
                            copyArtifact(artifact);
                        }
                    } else {
                        writeAndExport(writer, artifact);
                    }
                } else {
                    copyArtifact(artifact);
                }
            }

            // artifacts = dss.getSkippedDependencies();
            // for (Artifact artifact : artifacts) {
            // getLog().info("Skiped: "+artifact);
            // }
        } catch (IOException e) {
            throw new MojoExecutionException("Generate the dependencies file failure", e);
        }
    }

    protected void writeAndExport(PrintWriter writer, Artifact artifact) throws MojoExecutionException {
        writeArtifact(artifact, writer);
        exportArtifactToRepository(outputRepositoryLocation, artifact);

        // pom
        Artifact pomArtifact = getResolvedPomArtifact(artifact);
        exportArtifactToRepository(outputRepositoryLocation, pomArtifact);

    }

    protected void exportArtifactToRepository(File repoFolder, Artifact artifact) throws MojoExecutionException {
        if (repoFolder == null) {// if not set
            return;
        }

        String destFileName = DependencyUtil.getFormattedFileName(artifact, false);
        final File destDir = DependencyUtil.getFormattedOutputDirectory(false, false, false, true, false, repoFolder, artifact);
        final File destFile = new File(destDir, destFileName);
        copyFile(artifact.getFile(), destFile);
    }

    private void writeArtifact(Artifact artifact, PrintWriter writer) {
        // mvn:org.apache.commons/commons-compress/1.8.1/jar
        StringBuilder uri = new StringBuilder();
        uri.append("mvn:");
        uri.append(artifact.getGroupId());
        uri.append('/');
        uri.append(artifact.getArtifactId());
        uri.append('/');
        uri.append(artifact.getVersion());
        uri.append('/');
        uri.append(StringUtils.isBlank(artifact.getType()) ? "jar" : artifact.getType());
        writer.println(uri.toString());
    }
}
