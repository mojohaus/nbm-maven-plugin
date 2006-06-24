/* ==========================================================================
 * Copyright 2003-2004 Mevenide Team
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.nbm.model.NbmResource;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.LoadProperties;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.util.FileUtils;
import org.netbeans.nbbuild.CreateModuleXML;
import org.netbeans.nbbuild.MakeListOfNBM;
import org.netbeans.nbbuild.MakeNBM;
import org.netbeans.nbbuild.MakeNBM.Blurb;
import org.netbeans.nbbuild.MakeNBM.Signature;
import org.codehaus.mojo.nbm.model.NetbeansModule;

/**
 * Create the Netbeans module artifact (nbm file)
 * <p/>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal nbm
 * @phase package
 * @requiresDependencyResolution runtime
 * @requiresProject
 *
 */
public class CreateNbmMojo
        extends AbstractNbmMojo {
    /**
     * directory where the the netbeans jar and nbm file will be constructed
     * @parameter expression="${project.build.directory}/nbm"
     * @required
     */
    protected String nbmBuildDir;
    
    /**
     * @todo Change type to File
     * Build directory
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    private String buildDir;
    
    /**
     * Name of the jar packaged by the jar:jar plugin
     * @parameter alias="jarName" expression="${project.build.finalName}"
     * @required
     */
    private String finalName;
    
    /**
     * keystore location for signing the nbm file
     * @parameter expression="${keystore}"
     */
    private String keystore;
    
    /**
     * keystore password
     * @parameter expression="${keystorepass}"
     */
    private String keystorepassword;
    
    /**
     * keystore alias
     * @parameter expression="${keystorealias}"
     */
    private String keystorealias;
    
    /**
     * a netbeans module descriptor containing dependency information and more
     *
     * @parameter
     * @required
     */
    protected File descriptor;
    
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    
    public void execute()
    throws MojoExecutionException {
        Project antProject = registerNbmAntTasks();
        NetbeansModule module = readModuleDescriptor(descriptor);

        String type = module.getModuleType();
        boolean autoload = "autoload".equals(type);
        boolean eager = "eager".equals(type);
        // 1. initialization
        if (autoload && eager) {
            getLog().error("Module cannot be both eager and autoload");
            throw new MojoExecutionException("Module cannot be both eager and autoload");
        }
        String moduleName = module.getCodeNameBase();
        if (moduleName == null) {
            moduleName = project.getGroupId() + "." + project.getArtifactId();
        }
        String moduleJarName = moduleName.replace('.', '-');
        String cluster = module.getCluster();
        // it can happen the moduleName is in format org.milos/1
        int index = moduleJarName.indexOf('/');
        if (index > -1) {
            moduleJarName = moduleJarName.substring(0, index).trim();
        }
        
        File jarFile = new File( nbmBuildDir, finalName + ".jar");
        File nbmFile = new File( nbmBuildDir, finalName + ".nbm");
        File clusterDir = new File(nbmBuildDir, "netbeans" + File.separator + cluster);
        String moduleLocation = "modules";
        if (eager) {
            moduleLocation = moduleLocation + File.separator + "eager";
        }
        if (autoload) {
            moduleLocation = moduleLocation + File.separator + "autoload";
        }
        File moduleJarLocation = new File(clusterDir, moduleLocation);
        moduleJarLocation.mkdirs();
        
        //2. create nbm resources
        File moduleFile = new File(moduleJarLocation, moduleJarName + ".jar");
        try {
            FileUtils.newFileUtils().copyFile(jarFile, moduleFile);
        } catch (IOException ex) {
            getLog().error("Cannot copy module jar");
            throw new MojoExecutionException("Cannot copy module jar", ex);
        }
        if(module != null) {
            // copy libraries to the designated place..            
            List librList = new ArrayList();
            if (module.getLibraries() != null) {
                librList.addAll(module.getLibraries());
            };
            Set artifacts = project.getArtifacts();
            for ( Iterator iter = artifacts.iterator(); iter.hasNext();) {
                Artifact artifact = (Artifact) iter.next();
                if (matchesLibrary(artifact, librList)) {
                    File source = artifact.getFile();
                    File targetDir = new File(moduleJarLocation, "ext");
                    targetDir.mkdirs();
                    File target = new File(targetDir, source.getName());
                    
                    try {
                        FileUtils.newFileUtils().copyFile(source, target);
                    } catch (IOException ex) {
                        getLog().error("Cannot copy library jar");
                        throw new MojoExecutionException("Cannot copy library jar", ex);
                    }
                }
            }
            // copy additional resources..
            List nbmResources = module.getNbmResources();
            if (nbmResources.size() > 0) {
                Copy cp = (Copy)antProject.createTask("copy");
                cp.setTodir(clusterDir);
                HashMap customPaths = new HashMap();
                Iterator it = nbmResources.iterator();
                while (it.hasNext()) {
                    NbmResource res = (NbmResource)it.next();
                    if (res.getBaseDirectory() != null) {
                        File base = new File(project.getBasedir(), res.getBaseDirectory());
                        FileSet set = new FileSet();
                        set.setDir(base);
                        if (res.getIncludes().size() > 0){
                            Iterator it2 = res.getIncludes().iterator();
                            while (it2.hasNext()) {
                                set.createInclude().setName((String)it2.next());
                            }
                        }
                        if (res.getExcludes().size() > 0) {
                            Iterator it2 = res.getExcludes().iterator();
                            while (it2.hasNext()) {
                                set.createExclude().setName((String)it2.next());
                            }
                        }
                        if (res.getRelativeClusterPath() != null) {
                            File path = new File(clusterDir, res.getRelativeClusterPath());
                            Collection col = (Collection)customPaths.get(path);
                            if (col == null) {
                                col = new ArrayList();
                                customPaths.put(path, col);
                            }
                            col.add(path);
                        } else {
                            cp.addFileset(set);
                        }
                    }
                }
                try {
                    cp.execute();
                    if (customPaths.size() > 0) {
                        Iterator itx = customPaths.entrySet().iterator();
                        while (itx.hasNext()) {
                            Map.Entry ent = (Map.Entry)itx.next();
                            Collection elem = (Collection)ent.getValue();
                            cp = (Copy)antProject.createTask("copy");
                            cp.setTodir((File)ent.getKey());
                            Iterator itz = elem.iterator();
                            while (itz.hasNext()) {
                                FileSet set = (FileSet) itz.next();
                                cp.addFileset(set);
                            }
                            cp.execute();
                        }
                    }
                } catch (BuildException e) {
                    getLog().error( "Cannot copy additional resources into the nbm file" );
                    throw new MojoExecutionException( e.getMessage(), e );
                }
            }
        }
        
        File configDir = new File(clusterDir, "config" + File.separator + "Modules");
        configDir.mkdirs();
        CreateModuleXML moduleXmlTask = (CreateModuleXML)antProject.createTask("createmodulexml");
        moduleXmlTask.setXmldir(configDir);
        FileSet fs = new FileSet();
        fs.setDir(clusterDir);
        fs.setIncludes(moduleLocation + File.separator + moduleJarName + ".jar");
        if (autoload) {
            moduleXmlTask.addAutoload(fs);
        } else if (eager) {
            moduleXmlTask.addEager(fs);
        } else {
            moduleXmlTask.addEnabled(fs);
        }
        try {
            moduleXmlTask.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot generate config file." );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        LoadProperties loadTask = (LoadProperties)antProject.createTask("loadproperties");
        loadTask.setResource("directories.properties");
        try {
            loadTask.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot load properties." );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        MakeListOfNBM makeTask = (MakeListOfNBM)antProject.createTask("genlist");
        antProject.setNewProperty("module.name", finalName);
        antProject.setProperty("cluster.dir", cluster);
        FileSet set = makeTask.createFileSet();
        set.setDir(clusterDir);
        PatternSet pattern = set.createPatternSet();
        pattern.setIncludes("**");
        makeTask.setModule(moduleLocation + File.separator + moduleJarName + ".jar");
        makeTask.setOutputfiledir(clusterDir);
        try {
            makeTask.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot Generate nbm list" );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        
        // 3. generate nbm
        MakeNBM nbmTask = (MakeNBM)antProject.createTask("makenbm");
        nbmTask.setFile(nbmFile);
        nbmTask.setProductDir(clusterDir);
        
        nbmTask.setModule(moduleLocation + File.separator + moduleJarName + ".jar");
        nbmTask.setNeedsrestart(Boolean.toString(module.isRequiresRestart()));
        String moduleAuthor = module.getAuthor();
        if (moduleAuthor == null) {
            moduleAuthor = project.getOrganization() != null ? project.getOrganization().getName() : null;
        }
        nbmTask.setModuleauthor(moduleAuthor);
        if (keystore != null && keystorealias != null && keystorepassword != null) {
            File ks = new File(keystore);
            if (!ks.exists()) {
                getLog().warn("Cannot find keystore file at " + ks.getAbsolutePath());
            } else {
                Signature sig = nbmTask.createSignature();
                sig.setKeystore(ks);
                sig.setAlias(keystorealias);
                sig.setStorepass(keystorepassword);
            }
        } else if (keystore != null || keystorepassword != null || keystorealias != null) {
            getLog().warn("If you want to sign the nbm file, you need to define all three keystore related parameters.");
        }
        String licenseName = module.getLicenseName();
        String licenseFile = module.getLicenseFile();
        if (licenseName != null && licenseFile != null) {
            File lf = new File(project.getBasedir(), licenseFile);
            if (!lf.exists()) {
                getLog().warn("Cannot find license file at " + lf.getAbsolutePath());
            } else {
                Blurb lb = nbmTask.createLicense();
                lb.setFile(lf);
                lb.setName(licenseName);
            }
        } else if (licenseName != null || licenseFile != null) {
            getLog().warn("To add a license to the nbm, you need to specify both licenseName and licenseFile parameters");
        } else {
            Blurb lb = nbmTask.createLicense();
            lb.addText("<Here comes the license>");
            lb.setName("Unknown license agreement");
        }
        String homePageUrl = module.getHomepageUrl();
        if (homePageUrl == null) {
            homePageUrl = project.getUrl();
        }
        if (homePageUrl != null) {
            nbmTask.setHomepage(homePageUrl);
        }
        if (module.getDistributionUrl() != null) {
            nbmTask.setDistribution(module.getDistributionUrl() + (module.getDistributionUrl().endsWith("/") ? "" : "/") + nbmFile.getName());
        } else {
            getLog().warn("You don't define distribution URL in the netbeans module descriptor. That's ok for local installation but f you want to create an autoupdate site, you have to define this property.");
            nbmTask.setDistribution(project.getUrl() + (project.getUrl() != null && project.getUrl().endsWith("/") ? "" :"/") + nbmFile.getName());
            getLog().warn("  Using default value for distribution URL: " + nbmTask.getDescription());
        }
        try {
            nbmTask.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot Generate nbm file" );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        try {
            FileUtils.newFileUtils().copyFile(nbmFile, new File(buildDir, nbmFile.getName()));
        } catch (IOException ex) {
            getLog().error("Cannot copy nbm to build directory");
            throw new MojoExecutionException("Cannot copy nbm to build directory", ex);
        }
    }
}
