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
import org.apache.maven.artifact.Artifact;
//import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.mojo.nbm.model.NbmResource;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.LoadProperties;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.util.FileUtils;
import org.netbeans.nbbuild.CreateModuleXML;
import org.netbeans.nbbuild.MakeListOfNBM;
import org.codehaus.mojo.nbm.model.NetbeansModule;
import org.netbeans.nbbuild.JHIndexer;

/**
 * Create the Netbeans module directory structure, a prerequisite for nbm creation and cluster creation.
 * <p/>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal directory
 * @phase package
 * @requiresDependencyResolution runtime
 * @requiresProject
 *
 */
public class CreateNetbeansFileStructureMojo
        extends AbstractNbmMojo {
    /**
     * Netbeans module assembly build directory.
     * directory where the the netbeans jar and nbm file get constructed.
     * @parameter default-value="${project.build.directory}/nbm" expression="${maven.nbm.buildDir}"
     */
    protected String nbmBuildDir;
    
    /**
     * @todo Change type to File
     * Build directory
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    protected String buildDir;
    
    /**
     * Name of the jar packaged by the jar:jar plugin
     * @parameter alias="jarName" expression="${project.build.finalName}"
     */
    protected String finalName;
    
    
    /**
     * a netbeans module descriptor containing dependency information and more..
     *
     * @parameter default-value="${basedir}/src/main/nbm/module.xml"
     */
    protected File descriptor;

    /**
     * The location of JavaHelp sources for the project. The documentation
     * itself is expected to be in the directory structure based on codenamebase of the module.
     * eg. if your codenamebase is "org.netbeans.modules.apisupport", the the actual docs
     * files shall go to ${basedir}/src/main/javahelp/org/netbeans/modules/apisupport/docs
     * <br/>

     * Additionally if you provide docs, you will need to place the JavaHelp jar on the classpath 
     * of the nbm-plugin for the project. The jar is to be found in the netbeans/harness directory 
     * of any NetBeans installation. <br/>
<code>
&lt;plugin&gt;<br/>
  &lt;groupId&gt;org.codehaus.mojo&lt;/groupId&gt;<br/>
  &lt;artifactId&gt;nbm-maven-plugin&lt;/artifactId&gt;<br/>
  &lt;extensions&gt;true&lt;/extensions&gt;<br/>
  &lt;dependencies&gt;<br/>
    &lt;dependency&gt;<br/>
      &lt;groupId&gt;javax.help&lt;/groupId&gt;<br/>
      &lt;artifactId&gt;search&lt;/artifactId&gt;<br/>
      &lt;version&gt;2.0&lt;/version&gt;<br/>
      &lt;!--scope&gt;system&lt;/scope&gt;<br/>
      &lt;systemPath&gt;/home/mkleint/netbeans/harness/jsearch-2.0_04.jar&lt;/systemPath--&gt;<br/>
    &lt;/dependency&gt;<br/>
  &lt;/dependencies&gt;<br/>
&lt;/plugin&gt;<br/>
<br/>
</code>
     *
     * @parameter default-value="${basedir}/src/main/javahelp"
     * @since 2.7
     */
    protected File nbmJavahelpSource;
    
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;
    
//    /**
//     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
//     * @required
//     * @readonly
//     */
//    private ArtifactFactory artifactFactory;
    
    /**
     * custom distribution URL, overriding the value in the nbm descriptor
     * 
     * @parameter expression="${maven.nbm.distributionURL}"
     */
    private String distributionUrl;

    //items used by the CreateNBMMojo.
    protected Project antProject;
    protected NetbeansModule module;
    protected File clusterDir;
    protected String moduleJarName;
    protected String moduleLocation;
    
    public void execute()
    throws MojoExecutionException, MojoFailureException {
        if (!"nbm".equals(project.getPackaging())) {
            getLog().debug("Skipping " + project.getId() + " skipped, not NBM packaging");
            return;
        }
        antProject = registerNbmAntTasks();
        if (descriptor != null && descriptor.exists()) {
            module = readModuleDescriptor(descriptor);
        } else {
            module = createDefaultDescriptor(project, false);
        }
        if (distributionUrl != null) {
            module.setDistributionUrl(distributionUrl);
        }
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
            moduleName = moduleName.replaceAll("-", ".");
        }
        moduleJarName = moduleName.replace('.', '-');
        String cluster = module.getCluster();
        // it can happen the moduleName is in format org.milos/1
        int index = moduleJarName.indexOf('/');
        if (index > -1) {
            moduleJarName = moduleJarName.substring(0, index).trim();
        }
        
        File jarFile = new File( buildDir, finalName + ".jar");
        clusterDir = new File(nbmBuildDir, "netbeans" + File.separator + cluster);
        moduleLocation = "modules";
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
        
        getLog().info("Copying module jar to " + moduleJarLocation);
        try {
            FileUtils.newFileUtils().copyFile(jarFile, moduleFile, null, true, false);
        } catch (IOException ex) {
            getLog().error("Cannot copy module jar");
            throw new MojoExecutionException("Cannot copy module jar", ex);
        }
        
        ExamineManifest modExaminator = new ExamineManifest(getLog());
        modExaminator.setJarFile(jarFile);
        modExaminator.checkFile();
        String classpathValue = modExaminator.getClasspath();

        if(module != null) {
            // copy libraries to the designated place..            
            List librList = new ArrayList();
            if (module.getLibraries() != null) {
                librList.addAll(module.getLibraries());
            }
            List artifacts = project.getCompileArtifacts();
            for ( Iterator iter = artifacts.iterator(); iter.hasNext();) {
                Artifact artifact = (Artifact) iter.next();
                File source = artifact.getFile();
                if (classpathValue.contains("ext/" + source.getName())) {
                    File targetDir = new File(moduleJarLocation, "ext");
                    targetDir.mkdirs();
                    File target = new File(targetDir, source.getName());
                    
                    try {
                        FileUtils.newFileUtils().copyFile(source, target, null, true, false);
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
                boolean hasStandard = false;
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
                            col.add(set);
                        } else {
                            cp.addFileset(set);
                            hasStandard = true;
                        }
                    }
                }
               try {
                    if (hasStandard) {
                        cp.execute();
                    }
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
        
        //javahelp stuff.
        if (nbmJavahelpSource.exists()) {
            if (!hasJavaHelp) {
                getLog().error("jsearch.jar required on plugin classpath to generate JavaHelp.Please add this section to your nbm-maven-plugin configuration in pom.xml:" +
                        "\n\n<plugin>" +
                        "\n  <groupId>org.codehaus.mojo</groupId>" +
                        "\n  <artifactId>nbm-maven-plugin</artifactId>" +
                        "\n  <extensions>true</extensions>" +
                        "\n  <dependencies>" +
                        "\n    <dependency>" +
                        "\n      <groupId>javax.help</groupId>" +
                        "\n      <artifactId>search</artifactId>" +
                        "\n      <version>2.0</version>" +
                        "\n      <!--scope>system</scope>" +
                        "\n      <systemPath>/home/mkleint/netbeans/harness/jsearch-2.0_04.jar</systemPath-->" +
                        "\n    </dependency>" +
                        "\n  </dependencies>" +
                        "\n</plugin>"
                        );
                throw new MojoExecutionException("Required JavaHelp jar not found on nbm-maven-plugin's classpath.");
            }
            File javahelp_target = new File(buildDir, "javahelp");
            String javahelpbase = moduleJarName.replace('-', File.separatorChar) + File.separator + "docs";
            String javahelpSearch = "JavaHelpSearch";
            File b = new File(javahelp_target, javahelpbase);
            File p = new File(b, javahelpSearch);
            p.mkdirs();
            Copy cp = (Copy)antProject.createTask("copy");
            cp.setTodir(javahelp_target);
            FileSet set = new FileSet();
            set.setDir(nbmJavahelpSource);
            cp.addFileset(set);
            cp.execute();
            getLog().info("Generating JavaHelp Index...");
            
            JHIndexer jhTask = (JHIndexer)antProject.createTask("jhindexer");
            jhTask.setBasedir(b);
            jhTask.setDb(p);
            jhTask.setIncludes("**/*.html");
            jhTask.setExcludes(javahelpSearch);
            Path path = new Path(antProject);
            jhTask.setClassPath(path);
            try {
                jhTask.execute();
            } catch (BuildException e) {
                getLog().error( "Cannot generate JavaHelp index." );
                throw new MojoExecutionException( e.getMessage(), e );
            }
            File helpJarLocation = new File(clusterDir, "modules/docs");
            helpJarLocation.mkdirs();
            Jar jar = (Jar)antProject.createTask("jar");
            jar.setDestFile(new File(helpJarLocation, moduleJarName + ".jar"));
            set = new FileSet();
            set.setDir(javahelp_target);
            jar.addFileset(set);
            jar.execute();
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
        
    }
}
