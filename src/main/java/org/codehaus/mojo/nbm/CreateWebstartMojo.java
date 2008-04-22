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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.InterpolationFilterReader;

/**
 * @author <a href="mailto:johan.andren@databyran.se">Johan AndrÃ©n</a>
 * @goal create-webstart
 */
public class CreateWebstartMojo extends AbstractDistributionMojo {

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
     * Distributable zip file of NetBeans platform application
     * 
     * @parameter default-value="${project.build.directory}/${project.artifactId}-webstart-${project.version}.zip"
     * @required
     */
    protected File destinationFile;

    /**
     * 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     * @throws org.apache.maven.plugin.MojoFailureException 
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            File webstartBuildDir = new File(projectBuildDir + File.separator + "webstart" + File.separator + brandingToken);
            if (webstartBuildDir.exists()) {
                FileUtils.deleteDirectory(webstartBuildDir);
            }
            webstartBuildDir.mkdirs();

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

            // copy launcher jar-file and all clusters into build directory
            copyLauncher(webstartBuildDir);
            copyClusters(webstartBuildDir, enabledClusterDirectories);

            // setup filter properties for the jnlp-templates
            Properties filterProperties = new Properties();
            filterProperties.setProperty("app.title", project.getName());
            filterProperties.setProperty("app.vendor", project.getOrganization().getName());
            filterProperties.setProperty("app.description", project.getDescription());
            filterProperties.setProperty("branding.token", brandingToken);

            // split default options into <argument> blocks
            StringBuffer args = new StringBuffer();
            if (defaultOptions != null) {
                for (String arg : defaultOptions.split(" ")) {
                    args.append("<argument>");
                    args.append(arg);
                    args.append("</argument>\n");
                }
            }
            filterProperties.setProperty("app.arguments", args.toString());

            // external library jars first, modules depend on them
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(webstartBuildDir);
            scanner.setIncludes(new String[]{"**/modules/ext/*.jar"});
            scanner.scan();

            StringBuffer externalJnlps = new StringBuffer();

            // one jnlp for each since they could be signed by other certificates
            for (String extJarPath : scanner.getIncludedFiles()) {
                String jarHref = extJarPath.replace(webstartBuildDir.getAbsolutePath(), "");
                String externalName = new File(extJarPath).getName().replace(".jar", "");
                filterProperties.setProperty("external.jar", "<jar href=\"" + jarHref + "\"/>");
                File externalJnlp = new File(webstartBuildDir.getAbsolutePath() + File.separator + externalName + ".jnlp");
                filterCopy("/external.jnlp", externalJnlp, filterProperties);

                externalJnlps.append("<extension name=\"");
                externalJnlps.append(externalName);
                externalJnlps.append("\" href=\"");
                externalJnlps.append(externalJnlp.getName());
                externalJnlps.append("\"/>\n");
            }
            filterProperties.setProperty("application.ext.jnlps", externalJnlps.toString());

            // one jnlp for the platform files
            scanner.setIncludes(new String[]{"platform*/**/*.jar"});
            scanner.scan();

            String platformHrefs = createJarHrefBlock(scanner.getIncludedFiles(), webstartBuildDir);
            filterProperties.setProperty("platform.jars", platformHrefs);

            File platformJnlp = new File(webstartBuildDir.getAbsolutePath() + File.separator + "platform.jnlp");
            filterCopy("/platform.jnlp", platformJnlp, filterProperties);

            // all regular modules and eagerly loaded modules
            scanner.setIncludes(new String[]{"**/modules/*.jar", "**/modules/eager/*.jar"});
            scanner.setExcludes(new String[]{"platform*/**", brandingToken + "/**"});
            scanner.scan();
            String moduleJars = createJarHrefBlock(scanner.getIncludedFiles(), webstartBuildDir);
            filterProperties.setProperty("application.jars", moduleJars);

            File masterJnlp = new File(webstartBuildDir.getAbsolutePath() + File.separator + "master.jnlp");
            filterCopy("/master.jnlp", masterJnlp, filterProperties);

            // branding modules
            // all jars for branding should be directly in the brandingToken-directory
            scanner.setIncludes(new String[]{brandingToken + "/**/*.jar"});
            scanner.setExcludes(new String[]{});
            scanner.scan();
            File brandingDirectory = new File(webstartBuildDir.getAbsolutePath() + File.separator + brandingToken);
            for (String brandingJarPath : scanner.getIncludedFiles()) {
                File brandingJar = new File(webstartBuildDir.getAbsolutePath() + File.separator + brandingJarPath);
                File brandingDirectoryJar = new File(brandingDirectory + File.separator + brandingJar.getName());
                FileUtils.copyFile(brandingJar, brandingDirectoryJar);
                brandingJar.delete();
            }

            scanner.setIncludes(new String[]{brandingToken + "/*.jar"});
            scanner.scan();
            String brandingJarHrefs = createJarHrefBlock(scanner.getIncludedFiles(), webstartBuildDir);
            filterProperties.setProperty("branding.jars", brandingJarHrefs);

            File brandingJnlp = new File(webstartBuildDir.getAbsolutePath() + File.separator + "branding.jnlp");
            filterCopy("/branding.jnlp", brandingJnlp, filterProperties);

            // TODO sign jars

            // create zip archive
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            ZipArchiver archiver = new ZipArchiver();
            archiver.addDirectory(webstartBuildDir);
            archiver.setDestFile(destinationFile);
            archiver.createArchive();

            // attach standalone so that it gets installed/deployed
            projectHelper.attachArtifact(project, "zip", "webstart", destinationFile);

        } catch (Exception ex) {
            throw new MojoExecutionException("", ex);
        }


    }

    /**
     * @param standaloneBuildDir
     * @return The name of the jnlp-launcher jarfile in the build directory
     */
    private String copyLauncher(File standaloneBuildDir) throws IOException {
        File jnlpStarter = new File(netbeansInstallation.getAbsolutePath() +
                File.separator + "harness" +
                File.separator + "jnlp" +
                File.separator + "jnlp-launcher.jar");
        if (!jnlpStarter.exists()) {
            throw new IOException("jnlp-launcher.jar not found at " + jnlpStarter);
        }
        File jnlpDestination = new File(standaloneBuildDir.getAbsolutePath() + File.separator + "startup.jar");

        FileUtils.copyFile(jnlpStarter, jnlpDestination);

        return "startup.jar";
    }

    private void filterCopy(String resourcePath, File destinationFile, Properties filterProperties) throws IOException {
        // buffer so it isn't reading a byte at a time!
        Reader source = null;
        Writer destination = null;
        try {
            InputStream instream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            FileOutputStream outstream = new FileOutputStream(destinationFile);

            source = new BufferedReader(new InputStreamReader(instream, "UTF-8"));
            destination = new OutputStreamWriter(outstream, "UTF-8");

            // support ${token}
            Reader reader = new InterpolationFilterReader(source, filterProperties, "${", "}");

            IOUtil.copy(reader, destination);
        } finally {
            IOUtil.close(source);
            IOUtil.close(destination);
        }
    }

    private String createJarHrefBlock(String[] absolutePaths, File buildDir) {
        String remove = buildDir.getAbsolutePath();
        StringBuffer buffer = new StringBuffer();
        for (String absolutePath : absolutePaths) {
            buffer.append("   <jar href=\"");
            buffer.append(absolutePath.replace(remove, ""));
            buffer.append("\"/>\n");
        }

        return buffer.toString();
    }
}
