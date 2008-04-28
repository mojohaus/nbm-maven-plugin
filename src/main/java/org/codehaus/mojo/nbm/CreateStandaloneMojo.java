/*
 *  Copyright 2008 Johan AndrÃ©n.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.codehaus.mojo.nbm;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;

/**
 * Create a standalone application out of the available clusters and netbeans
 * installation.
 *
 * @author <a href="mailto:johan.andren@databyran.se">Johan Andren</a>
 * @goal standalone-zip
 * @aggregator
 */
public class CreateStandaloneMojo extends AbstractDistributionMojo {

    /**
     * Distributable zip file of NetBeans platform application
     * 
     * @parameter default-value="${project.build.directory}/${project.artifactId}-standalone-${project.version}.zip"
     * @required
     */
    protected File destinationFile;

    /**
     * The Maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    protected MavenProjectHelper projectHelper;

    /**
     * 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     * @throws org.apache.maven.plugin.MojoFailureException 
     */
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            File harnessDir = new File(netbeansInstallation + File.separator + "harness");
            File standaloneBuildDir = new File(projectBuildDir + File.separator + "standalone" + File.separator + brandingToken);
            if (standaloneBuildDir.exists()) {
                FileUtils.deleteDirectory(standaloneBuildDir);
            }
            standaloneBuildDir.mkdirs();

            List<File> enabledClusterDirectories = findClusterDirectories();
            File platformClusterDirectory = findPlatformClusterDirectory(enabledClusterDirectories);
            if (platformClusterDirectory == null) {
                throw new MojoExecutionException("Cannot find platform* cluster within NetBeans installation at " + netbeansInstallation);
            }

            if (enabledClusterDirectories.size() != enabledClusters.size()) {
                getLog().error("Cannot find cluster directories for all enabled clusters:");
                getLog().error("enabled clusters: " + enabledClusters);
                getLog().error("found directories: " + enabledClusterDirectories);
                throw new MojoFailureException("Not all clusters found");
            }

            // create etc/ with cluster and startup config
            createBundleEtcDir(standaloneBuildDir, harnessDir, enabledClusters, defaultOptions, brandingToken);
            createLauncherDir(standaloneBuildDir, harnessDir);
            copyClusters(standaloneBuildDir, enabledClusterDirectories);

            // create zip archive
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            ZipArchiver archiver = new ZipArchiver();
            archiver.addDirectory(standaloneBuildDir);
            archiver.setDestFile(destinationFile);
            archiver.createArchive();

            // attach standalone so that it gets installed/deployed
            projectHelper.attachArtifact(project, "zip", "standalone", destinationFile);

        } catch (Exception ex) {
            throw new MojoExecutionException("", ex);
        }

    }

    private void createLauncherDir(File standaloneBuildDir, File harnessDir) throws IOException {
        // copy application startup files to bin/ and rename to branding token
        File binDir = new File(standaloneBuildDir + File.separator + "bin");
        binDir.mkdir();
        File harnessBinDir = new File(harnessDir.getAbsolutePath() + File.separator + "launchers");

        for (File launcher : harnessBinDir.listFiles()) {
            // brand executable and remove .sh from sh-launcher
            File newLauncher = new File(binDir.getAbsolutePath() + File.separator +
                    launcher.getName().replace("app", brandingToken).replace(".sh", ""));
            FileUtils.copyFile(launcher, newLauncher);
        }

    }
}
