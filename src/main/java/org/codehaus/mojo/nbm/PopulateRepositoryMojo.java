/* ==========================================================================
 * Copyright 2003-2006 Mevenide Team
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Input;
import org.apache.tools.ant.taskdefs.PathConvert;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.IOUtil;


/**
 * a goal for identifying netbeans modules from the installation and populationg the local
 * repository with them. Optionally you can also deploy to a remote repository.
   <p/>
 * If you are looking for an existing remote repository for netbeans artifacts, check out
 * http://208.44.201.216:18080/maven/ it contains API artifacts for 4.1, 5.0 and 5.5 releases. 
 * 6.0 Milestone 4 and 6 also included.
 
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal populate-repository
 * @requiresProject false
 * @aggregator
 */
public class PopulateRepositoryMojo
        extends AbstractNbmMojo {
    /**
     * Local maven repository.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * an url where to deploy the netbeans artifacts.. optional, if not specified, the artifacts will be only installed
     * in local repository, if you need to give credentials to access remote repo, the id of the server is hardwired to "netbeans".
     * @parameter expression="${deployUrl}"
     */
    private String deployUrl;
    
    /**
     * Location of netbeans installation
     *
     * @parameter expression="${netbeansInstallDirectory}"
     */
    
    protected String netbeansInstallDirectory;
    
    
    /**
     * If you want to install/deploy also netbeans api javadocs, download the javadoc zip file from netbeans.org
     * expand it to a directory, it should contain multiple zip files. Define this parameter as absolute path to the zip files folder.
     *
     * @parameter expression="${netbeansJavadocDirectory}"
     */
    protected String netbeansJavadocDirectory;
    
    /**
     * Assumes a folder with &lt;code-name-base&gt;.zip files containing sources for modules.
     * @parameter expression="${netbeansSourcesDirectory}"
     */
    
    protected String netbeansSourcesDirectory;
    /**
     * Optional parameter, when specified, will force all modules to have the designated version.
     * Good when depending on releases. Then you would for example specify RELEASE50 in this parameter and
     * all modules get this version in the repository.
     * @parameter expression="${forcedVersion}"
     */
    protected String forcedVersion;
    
    /**
     * Maven ArtifactFactory.
     *
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    private ArtifactFactory artifactFactory;
    
    /**
     * Maven ArtifactInstaller.
     *
     * @parameter expression="${component.org.apache.maven.artifact.installer.ArtifactInstaller}"
     * @required
     * @readonly
     */
    private ArtifactInstaller artifactInstaller;
    
    
    /**
     * Maven ArtifactDeployer.
     *
     * @parameter expression="${component.org.apache.maven.artifact.deployer.ArtifactDeployer}"
     * @required
     * @readonly
     */
    private ArtifactDeployer artifactDeployer;
    
    /**
     * Maven ArtifactRepositoryFactory.
     *
     * @parameter expression="${component.org.apache.maven.artifact.repository.ArtifactRepositoryFactory}"
     * @required
     * @readonly
     */
    private ArtifactRepositoryFactory repositoryFactory;
    
    
    public void execute() throws MojoExecutionException {
        getLog().info("Populate repository with netbeans modules");
        Project antProject = registerNbmAntTasks();
        ArtifactRepository deploymentRepository = null;
        if (deployUrl != null) {
            ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();
            deploymentRepository = repositoryFactory.createDeploymentArtifactRepository("netbeans", deployUrl, layout, true);
        }
        
        
        if (netbeansInstallDirectory == null) {
            Input input = (Input)antProject.createTask("input");
            input.setMessage("Please enter netbeans installation directory:");
            input.setAddproperty("installDir");
            try {
                input.execute();
            } catch (BuildException e) {
                getLog().error( "Cannot run ant:input" );
                throw new MojoExecutionException( e.getMessage(), e );
            }
            String prop = antProject.getProperty("installDir");
            netbeansInstallDirectory = prop;
        }
        
        File rootDir = new File(netbeansInstallDirectory);
        if (!rootDir.exists()) {
            getLog().error("Netbeans installation doesn't exist.");
            throw new MojoExecutionException("Netbeans installation doesn't exist.");
        }
        getLog().info("Copying Netbeans artifacts from " + netbeansInstallDirectory);
        
        PathConvert convert = (PathConvert)antProject.createTask("pathconvert");
        convert.setPathSep(",");
        convert.setProperty("netbeansincludes");
        FileSet set = new FileSet();
        set.setDir(rootDir);
        set.createInclude().setName("**/modules/*.jar");
        set.createInclude().setName("**/modules/autoload/*.jar");
        set.createInclude().setName("**/modules/eager/*.jar");
        set.createInclude().setName("platform*/core/*.jar");
        set.createInclude().setName("platform*/lib/*.jar");
        
        convert.createPath().addFileset(set);
        try {
            convert.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot run ant:pathconvert" );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        
        String prop = antProject.getProperty("netbeansincludes");
        StringTokenizer tok = new StringTokenizer(prop, ",");
        HashMap moduleDefinitions = new HashMap();
        HashMap clusters = new HashMap();
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            File module = new File(token);
            String clust = module.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
            clust = clust.substring(0, clust.indexOf(File.separator));
            ExamineManifest examinator = new ExamineManifest();
            examinator.setPopulateDependencies(true);
            examinator.setJarFile(module);
            examinator.checkFile();
            if (examinator.isNetbeansModule()) {
                //TODO get artifact id from the module's manifest?
                String artifact = module.getName().substring(0, module.getName().indexOf(".jar"));
                String version = forcedVersion == null ? examinator.getSpecVersion() : forcedVersion;
                String group = "org.netbeans." + (examinator.hasPublicPackages() ? "api" : "modules");
                String m = examinator.getModule();
                int slash = m.indexOf("/");
                if (slash > 0) {
                    m = m.substring(0, slash - 1);
                }
                m = m.trim();
                examinator.setModule(m);
                Artifact art = createArtifact(artifact, version, group);
                ModuleWrapper wr = new ModuleWrapper(artifact, version, group, examinator, module);
                moduleDefinitions.put(wr, art);
                Collection col = (Collection)clusters.get(clust);
                if (col == null) {
                    col = new ArrayList();
                    clusters.put(clust, col);
                }
                col.add(wr);
            }
        }
        List wrapperList = new ArrayList(moduleDefinitions.keySet());
        int count = wrapperList.size() + 1;
        int index = 0;
        Iterator it = moduleDefinitions.entrySet().iterator();
        File javadocRoot = null;
        if (netbeansJavadocDirectory != null) {
            javadocRoot = new File(netbeansJavadocDirectory);
            if (!javadocRoot.exists()) {
                javadocRoot = null;
                throw new MojoExecutionException("The netbeansJavadocDirectory parameter doesn't point to an existing folder");
            }
        }
        File sourceRoot = null;
        if (netbeansSourcesDirectory != null) {
            sourceRoot = new File(netbeansSourcesDirectory);
            if (!sourceRoot.exists()) {
                sourceRoot = null;
                throw new MojoExecutionException("The netbeansSourceDirectory parameter doesn't point to an existing folder");
            }
        }
        while (it.hasNext()) {
            Map.Entry elem = (Map.Entry) it.next();
            ModuleWrapper man = (ModuleWrapper)elem.getKey();
            Artifact art = (Artifact)elem.getValue();
            index = index + 1;
            getLog().info("Processing " + index + "/" + count);
            File pom = createMavenProject(man, wrapperList);
            ArtifactMetadata metadata = new ProjectArtifactMetadata(art, pom);
            art.addMetadata( metadata );
            File javadoc = null;
            Artifact javadocArt = null;
            if (javadocRoot != null) {
                File zip = new File(javadocRoot, art.getArtifactId() + ".zip");
                if (zip.exists()) {
                    javadoc = zip;
                    javadocArt = artifactFactory.createArtifactWithClassifier(art.getGroupId(), 
                                                                              art.getArtifactId(),
                                                                              art.getVersion(),
                                                                              "jar", "javadoc");
                }
            }
            File source = null;
            Artifact sourceArt = null;
            if (sourceRoot != null) {
                File zip = new File(sourceRoot, art.getArtifactId() + ".zip");
                if (zip.exists()) {
                    source = zip;
                    sourceArt = artifactFactory.createArtifactWithClassifier(art.getGroupId(), 
                                                                              art.getArtifactId(),
                                                                              art.getVersion(),
                                                                              "jar", "sources");
                }
            }
            
            try {
                if (javadoc != null) {
                    artifactInstaller.install(javadoc, javadocArt, localRepository);
                }
                if (source != null) {
                    artifactInstaller.install(source, sourceArt, localRepository);
                }
                artifactInstaller.install(man.getFile(), art, localRepository );
            } catch ( ArtifactInstallationException e ) {
                // TODO: install exception that does not give a trace
                throw new MojoExecutionException( "Error installing artifact", e );
            }
            try {
                if (deploymentRepository != null) {
                    if (javadoc != null) {
                        artifactDeployer.deploy(javadoc, javadocArt, deploymentRepository, localRepository);
                    }
                    if (source != null) {
                        artifactDeployer.deploy(source, sourceArt, deploymentRepository, localRepository);
                    }
                    artifactDeployer.deploy(man.getFile(), art, deploymentRepository, localRepository);
                }
            } catch (ArtifactDeploymentException ex) {
                throw new MojoExecutionException( "Error Deploying artifact", ex );
            }
            
        }
    }
    
    
    private File createMavenProject(ModuleWrapper wrapper, List wrapperList) {
        Model mavenModel = new Model();
        
        mavenModel.setGroupId(wrapper.getGroup());
        mavenModel.setArtifactId(wrapper.getArtifact());
        mavenModel.setVersion(wrapper.getVersion());
        mavenModel.setPackaging("jar");
        mavenModel.setModelVersion("4.0.0");
        ExamineManifest man = wrapper.getModuleManifest();
        List deps = new ArrayList();
        if (!man.getDependencyTokens().isEmpty()) {
            Iterator it = man.getDependencyTokens().iterator();
            while (it.hasNext()) {
                String elem = (String) it.next();
                // create pseudo wrapper
                ModuleWrapper wr = new ModuleWrapper(elem);
                int index = wrapperList.indexOf(wr);
                if (index > -1) {
                    wr = (ModuleWrapper)wrapperList.get(index);
                    //we don't want the API modules to depend on non-api ones..
                    // otherwise the transitive dependency mechanism pollutes your classpath..
                    if ((wr.getModuleManifest().hasPublicPackages() && wrapper.getModuleManifest().hasPublicPackages())
                                 || !wrapper.getModuleManifest().hasPublicPackages()) {
                        Dependency dep = new Dependency();
                        dep.setArtifactId(wr.getArtifact());
                        dep.setGroupId(wr.getGroup());
                        dep.setVersion(wr.getVersion());
                        dep.setType("jar");
//TODO possible solution to transitivity? 
//                      dep.setOptional(true);
                        deps.add(dep);
                    }
                } else {
                    getLog().warn("No module found for dependency '" + elem + "'");
                }
            }
        }
        //need some generic way to handle Classpath: items.
        if ("org.netbeans.api".equals(wrapper.getGroup()) && "org-jdesktop-layout".equals(wrapper.getArtifact())) {
            //how to figure the right version?
            String cp = wrapper.getModuleManifest().getClasspath();
            if (cp != null) {
                StringTokenizer tok = new StringTokenizer(cp);
                while (tok.hasMoreTokens()) {
                    String path = tok.nextToken();
                    File f = new File(wrapper.getFile().getParentFile(), path);
                    if (f.exists()) {
                        FileInputStream fis = null;
                        DigestOutputStream os = null;
                        try {
                            fis = new FileInputStream(f);
                            MessageDigest md5Dig = MessageDigest.getInstance( "MD5" );
                            os = new DigestOutputStream(new NullOutputStream(), md5Dig);
                            IOUtil.copy(fis, os);
                            IOUtil.close(fis);
                            IOUtil.close(os);
                            String md5 = encode(md5Dig.digest());
                            if ("5deb85c331c5a75d2ea6182e22a7f191".equals(md5)) {
                                Dependency dep = new Dependency();
                                dep.setArtifactId("swing-layout");
                                dep.setGroupId("net.java.dev.swing-layout");
                                dep.setVersion("1.0.1");
                                dep.setType("jar");
                                dep.setScope("provided");
                                deps.add(dep);
                            } else if ("a7a21e91ecaffdda3fb4f4ff0ae338b1".equals(md5)) {
                                Dependency dep = new Dependency();
                                dep.setArtifactId("swing-layout");
                                dep.setGroupId("net.java.dev.swing-layout");
                                dep.setVersion("1.0.2");
                                dep.setType("jar");
                                dep.setScope("provided");
                                deps.add(dep);
                            }
                        } catch (Exception x) {
                            x.printStackTrace();
                        }
                    }
                }
            }
        }
        if ("org.netbeans.api".equals(wrapper.getGroup()) && "org-netbeans-libs-javacapi".equals(wrapper.getArtifact())) {
            //how to figure the right version?
        }
        
        mavenModel.setDependencies(deps);
        FileWriter writer = null;
        File fil = null;
        try {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = File.createTempFile("maven", "pom");
            writer = new FileWriter(fil);
            xpp.write(writer, mavenModel);
        } catch (IOException ex) {
            ex.printStackTrace();
            
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }
        return fil;
    }

    private File createClusterProject(Artifact cluster, Collection mods) {
        Model mavenModel = new Model();
        
        mavenModel.setGroupId(cluster.getGroupId());
        mavenModel.setArtifactId(cluster.getArtifactId());
        mavenModel.setVersion(cluster.getVersion());
        mavenModel.setPackaging("pom");
        mavenModel.setModelVersion("4.0.0");
        List deps = new ArrayList();
        Iterator it = mods.iterator();
        while (it.hasNext()) {
            ModuleWrapper wr = (ModuleWrapper) it.next();
            Dependency dep = new Dependency();
            dep.setArtifactId(wr.getArtifact());
            dep.setGroupId(wr.getGroup());
            dep.setVersion(wr.getVersion());
            dep.setType("jar");
            deps.add(dep);
        }
        mavenModel.setDependencies(deps);
        FileWriter writer = null;
        File fil = null;
        try {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = File.createTempFile("maven", "pom");
            writer = new FileWriter(fil);
            xpp.write(writer, mavenModel);
        } catch (IOException ex) {
            ex.printStackTrace();
            
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }
        return fil;
    }
    
    
    private Artifact createArtifact(String artifact, String version, String group) {
        return artifactFactory.createBuildArtifact(group, artifact, version, "jar");
    }
    
    private Artifact createClusterArtifact(String artifact, String version) {
        return artifactFactory.createBuildArtifact("org.netbeans.cluster", artifact, version, "pom");
    }
    
    private class ModuleWrapper  {
        ExamineManifest man;
        private String artifact;
        private String version;
        private String group;
        private File file;

        String module;
        
        public ModuleWrapper(String module) {
            this.module = module;
        }
        
        public ModuleWrapper(String art, String ver, String grp, ExamineManifest manifest, File fil) {
            man = manifest;
            artifact = art;
            version = ver;
            group = grp;
            file = fil;
        }
        
        public int hashCode() {
            return getModule().hashCode();
        }
        
        public boolean equals(Object obj) {
            return getModule().equals(((ModuleWrapper)obj).getModule());
        }
        
        public String getModule() {
            return module != null ? module : getModuleManifest().getModule();
        }
        
        public ExamineManifest getModuleManifest() {
            return man;
        }
        
        private String getArtifact() {
            return artifact;
        }
        
        private String getVersion() {
            return version;
        }
        
        private String getGroup() {
            return group;
        }

        private File getFile() {
            return file;
        }
    }
    
    private static class NullOutputStream extends OutputStream {
        public void write(int b) throws IOException {
            
        }
        
    }
    
    /**
     * Encodes a 128 bit or 160-bit byte array into a String.
     *
     * @param binaryData Array containing the digest
     * @return Encoded hex string, or null if encoding failed
     */
    private static String encode( byte[] binaryData )
    {
        if ( binaryData.length != 16 && binaryData.length != 20 )
        {
            int bitLength = binaryData.length * 8;
            throw new IllegalArgumentException( "Unrecognised length for binary data: " + bitLength + " bits" );
        }

        String retValue = "";

        for ( int i = 0; i < binaryData.length; i++ )
        {
            String t = Integer.toHexString( binaryData[i] & 0xff );

            if ( t.length() == 1 )
            {
                retValue += ( "0" + t );
            }
            else
            {
                retValue += t;
            }
        }

        return retValue.trim();
    }
    
}
