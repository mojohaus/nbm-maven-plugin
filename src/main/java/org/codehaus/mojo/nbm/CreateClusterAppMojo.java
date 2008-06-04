/* ==========================================================================
 * Copyright Milos Kleint
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */
package org.codehaus.mojo.nbm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

/**
 * Create the Netbeans module clusters from reactor
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal cluster-app
 * @requiresDependencyResolution runtime
 * @requiresProject
 */
    public class CreateClusterAppMojo
        extends AbstractNbmMojo {

    /**
     * output directory where the the netbeans application will be created.
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    private File buildDirectory;
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;
    

    public void execute() throws MojoExecutionException, MojoFailureException {
        
        File nbmBuildDirFile = new File(buildDirectory, brandingToken);
        if (!nbmBuildDirFile.exists()) {
            nbmBuildDirFile.mkdirs();
        }

        if ("nbm-application".equals(project.getPackaging())) {
            Set knownClusters = new HashSet();
            Set artifacts = project.getArtifacts();
            Iterator it = artifacts.iterator();
            while (it.hasNext()) {
                Artifact art = (Artifact) it.next();
                if (art.getType().equals("nbm-file")) {
                    JarFile jf = null;
                    try {
                        jf = new JarFile(art.getFile());
                        String cluster = findCluster(jf);
                        if (!knownClusters.contains(cluster)) {
                            getLog().info("Processing cluster '" + cluster + "'");
                            knownClusters.add(cluster);
                        }
                        File clusterFile = new File(nbmBuildDirFile, cluster);
                        boolean newer = false;
                        if (!clusterFile.exists()) {
                            clusterFile.mkdir();
                            newer = true;
                        } else {
                            File stamp = new File(clusterFile, ".lastModified");
                            if (stamp.lastModified() < art.getFile().lastModified()) {
                                newer = true;
                            }
                        }
                        if (newer) {
                            getLog().debug("Copying " + art.getId() + " to cluster " + cluster);
                            Enumeration enu = jf.entries();
                            while (enu.hasMoreElements()) {
                                ZipEntry ent = (ZipEntry) enu.nextElement();
                                String name = ent.getName();
                                if (name.startsWith("netbeans/")) { //ignore everything else.
                                    String path = name.replace("netbeans/", cluster + "/");
                                    File fl = new File(nbmBuildDirFile, path.replace("/", File.separator));
                                    if (ent.isDirectory()) {
                                        fl.mkdirs();
                                    } else {
                                        fl.getParentFile().mkdirs();
                                        fl.createNewFile();
                                        BufferedOutputStream outstream = null;
                                        try {
                                            outstream = new BufferedOutputStream(new FileOutputStream(fl));
                                            InputStream instream = jf.getInputStream(ent);
                                            IOUtil.copy(instream, outstream);
                                        } finally {
                                            IOUtil.close(outstream);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ex) {
                        getLog().error(ex);
                    } finally {
                        try {
                            jf.close();
                        } catch (IOException ex) {
                            getLog().error(ex);
                        }
                    }
                }
            }
            getLog().info("Created NetBeans module cluster(s) at " + nbmBuildDirFile.getAbsoluteFile());
        } else {
            throw new MojoExecutionException("This goal only makes sense on reactor projects or project with nbm-application packaging");
        }
        //in 6.1 the rebuilt modules will be cached if the timestamp is not touched.
        File[] files = nbmBuildDirFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                File stamp = new File(files[i], ".lastModified");
                if (!stamp.exists()) {
                    try {
                        stamp.createNewFile();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                stamp.setLastModified(new Date().getTime());
            }
        }
    }
    private final static Pattern patt = Pattern.compile(".*targetcluster=\"([a-zA-Z0-9_\\.\\-]+)\".*", Pattern.DOTALL);

    private String findCluster(JarFile jf) throws MojoFailureException, IOException {
        ZipEntry entry = jf.getEntry("Info/info.xml");
        InputStream ins = jf.getInputStream(entry);
        String str = IOUtil.toString(ins, "UTF8");
        Matcher m = patt.matcher(str);
        if (!m.matches()) {
            getLog().error("Cannot find cluster for " + jf.getName());
        } else {
            return m.group(1);
        }
        return "extra";
    }
    
    /**
     * 
     * @param buildDir Directory where the platform bundle is built
     * @param harnessDir "harness" directory of the netbeans installation
     * @param enabledClusters The names of all enabled clusters
     * @param defaultOptions Options for the netbeans platform to be placed in config file
     * @param brandingToken 
     * 
     * @throws java.io.IOException
     */
    private void createBundleEtcDir(File buildDir, File harnessDir, List<String> enabledClusters, String defaultOptions, String brandingToken)
            throws IOException {
        File etcDir = new File(buildDir + File.separator + "etc");
        etcDir.mkdir();

        // create app.clusters which contains a list of clusters to include in the application

        File clusterConf = new File(etcDir + File.separator + brandingToken + ".clusters");
        clusterConf.createNewFile();
        StringBuffer buffer = new StringBuffer();
        for (String clusterName : enabledClusters) {
            buffer.append(clusterName);
            buffer.append("\n");
        }

        FileUtils.fileWrite(clusterConf.getAbsolutePath(), buffer.toString());

        // app.conf contains default options and other settings
        File confFile = new File(harnessDir.getAbsolutePath() + File.separator + "etc" + File.separator + "app.conf");
        File confDestFile = new File(etcDir.getAbsolutePath() + File.separator + brandingToken + ".conf");

        FileUtils.copyFile(confFile, confDestFile);

        // add default options from pom-file to app.conf
        String contents = FileUtils.fileRead(confDestFile);
        contents = contents.replace("default_options=\"", "default_options=\"" + defaultOptions + " ");
        FileUtils.fileWrite(confDestFile.getAbsolutePath(), contents);

    }
    
}
