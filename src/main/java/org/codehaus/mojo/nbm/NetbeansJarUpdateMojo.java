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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.nbm.model.Dependency;
import org.codehaus.mojo.nbm.model.NetbeansModule;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.util.FileUtils;

/**
 * goal for updating the artifact jar with netbeans specific entries.
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal jar
 * @phase package
 * @requiresDependencyResolution runtime
 * @requiresProject
 */
public class NetbeansJarUpdateMojo extends AbstractNbmMojo {
    /**
     * @parameter expression="${project.build.directory}/nbm"
     * @required
     */
    protected String nbmBuildDir;
    
    /**
     * @todo Change type to File
     *
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    private String buildDir;
    
    /**
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    private String finalName;
    
    /**
     * a netbeans module descriptor containing dependency information and more
     *
     * @parameter default-value="${basedir}/src/main/nbm/module.xml"
     */
    protected File descriptor;
    
    /**
     * maven project
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    
    public void execute()
    throws MojoExecutionException {
        Project antProject = registerNbmAntTasks();
        
        
        // need to delete if exists, otherwise we get the weirdo multiplied Class-Path manifest attribute. -->
        File jarFile = new File( nbmBuildDir, finalName + ".jar" );
        if (jarFile.exists()) {
            jarFile.delete();
        }
        // copy the original jar made by jar:jar goal to target/nbm -->
        File original = new File( buildDir, finalName + ".jar");
        try {
            FileUtils.newFileUtils().copyFile(original, jarFile);
        } catch (IOException ex) {
            getLog().error("Cannot copy module jar");
            throw new MojoExecutionException("Cannot copy module jar", ex);
        }
        NetbeansModule module;
        if (descriptor != null && descriptor.exists() ) {
            module = readModuleDescriptor(descriptor);
        } else {
            module = createDefaultDescriptor(project, false);
        }
        
        String moduleName = module.getCodeNameBase();
        if (moduleName == null) {
            moduleName = project.getGroupId() + "." + project.getArtifactId();
            moduleName = moduleName.replaceAll("-", ".");
        }
//<!-- if a netbeans specific manifest is defined, examine this one, otherwise the already included one.
// ignoring the case when some of the netbeans attributes are already defined in the jar and more is included.
        File specialManifest = null;
        File nbmManifest = (module.getManifest() != null ? new File(project.getBasedir(), module.getManifest()) : null);
        if (nbmManifest != null && nbmManifest.exists()) {
            specialManifest = nbmManifest;
        }
        ExamineManifest examinator = new ExamineManifest();
        if (specialManifest != null) {
            examinator.setManifestFile(specialManifest);
        } else {
            examinator.setJarFile(jarFile);
        }
        examinator.checkFile();
        
        getLog().info("NBM Plugin updates jar.");
        
        Jar jarTask = (Jar)antProject.createTask("jar");
        Manifest manifest = null;
        if (specialManifest != null) {
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(specialManifest));
                manifest = new Manifest(reader);
            } catch (IOException exc) {
                manifest = new Manifest();
                //TODO some reporting
            } catch (ManifestException ex) {
                //TODO some reporting
                ex.printStackTrace();
                manifest = new Manifest();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException io) {
                        io.printStackTrace();
                    }
                }
            }
        } else {
            manifest = new Manifest();
        }
        String specVersion = AdaptNbVersion.adaptVersion(project.getVersion(), AdaptNbVersion.TYPE_SPECIFICATION);
        String implVersion = AdaptNbVersion.adaptVersion(project.getVersion(), AdaptNbVersion.TYPE_IMPLEMENTATION);
        Manifest.Section mainSection = manifest.getMainSection();
        conditionallyAddAttribute(mainSection, "OpenIDE-Module-Specification-Version", specVersion);
        conditionallyAddAttribute(mainSection, "OpenIDE-Module-Implementation-Version", implVersion);
//     create a timestamp value for OpenIDE-Module-Build-Version: manifest entry
        String timestamp = new SimpleDateFormat("yyyyMMddhhmm").format(new Date());
        conditionallyAddAttribute(mainSection, "OpenIDE-Module-Build-Version", timestamp);
        conditionallyAddAttribute(mainSection, "OpenIDE-Module", moduleName);
//        conditionallyAddAttribute(mainSection, "OpenIDE-Module-IDE-Dependencies", "IDE/1 > 3.40");
        // localization items
        if (!examinator.isLocalized()) {
            conditionallyAddAttribute(mainSection, "OpenIDE-Module-Display-Category", project.getGroupId());
            conditionallyAddAttribute(mainSection, "OpenIDE-Module-Name", project.getName());
            conditionallyAddAttribute(mainSection, "OpenIDE-Module-Short-Description", project.getDescription());
            conditionallyAddAttribute(mainSection, "OpenIDE-Module-Long-Description", project.getDescription());
        }
        getLog().debug("module =" + module);
        if (module != null) {
            String classPath = "";
            String dependencies = "";
            String depSeparator = " ";
            List librList = new ArrayList();
            if (module.getLibraries() != null) {
                librList.addAll(module.getLibraries());
            };
            List deps = module.getDependencies();
            List artifacts = project.getCompileArtifacts();
            for ( Iterator iter = artifacts.iterator(); iter.hasNext();) {
                Artifact artifact = (Artifact) iter.next();
                ExamineManifest depExaminator = new ExamineManifest();
                depExaminator.setJarFile(artifact.getFile());
                depExaminator.checkFile();
                if (!depExaminator.isNetbeansModule() && matchesLibrary(artifact, librList)) {
                    classPath = classPath + " ext/" + artifact.getFile().getName();
                }
                Dependency dep = resolveNetbeansDependency(artifact, deps, depExaminator);
                if (dep != null) {
                    String type = dep.getType();
                    String depToken = dep.getExplicitValue();
                    if (depToken == null) {
                        if ("loose".equals(type)) {
                            depToken = depExaminator.getModule();
                        } else if ("spec".equals(type)) {
                            depToken = depExaminator.getModule() + " > " +
                                    (depExaminator.isNetbeansModule() ?
                                        depExaminator.getSpecVersion() :
                                        AdaptNbVersion.adaptVersion(depExaminator.getSpecVersion(), AdaptNbVersion.TYPE_SPECIFICATION));
                        } else if ("impl".equals(type)) {
                            depToken = depExaminator.getModule() + " = " +
                                    (depExaminator.isNetbeansModule() ?
                                        depExaminator.getImplVersion() :
                                        AdaptNbVersion.adaptVersion(depExaminator.getImplVersion(), AdaptNbVersion.TYPE_IMPLEMENTATION));
                        } else {
                            throw new MojoExecutionException("Wrong type of Netbeans dependency: " + type + " Allowed values are: loose, spec, impl.");
                        }
                    }
                    if (depToken == null) {
                        //TODO report
                        getLog().error("Cannot properly resolve the netbeans dependency for " + dep.getId());
                    } else {
                        dependencies = dependencies + depSeparator + depToken;
                        depSeparator = ", ";
                    }
                }
            }
            if (classPath.length() > 0) {
                conditionallyAddAttribute(mainSection, "Class-Path", classPath.trim());
            }
            if (dependencies.length() > 0) {
                conditionallyAddAttribute(mainSection, "OpenIDE-Module-Module-Dependencies", dependencies);
            }
            if (librList.size() > 0) {
                String list = "";
                for (int i = 0; i < librList.size(); i++) {
                    list = list + " " + librList.get(i);
                }
                getLog().warn("Some libraries could not be found in the dependency chain: " + list);
            }
        }
        try {
            jarTask.setDestFile(jarFile);
            jarTask.addConfiguredManifest(manifest);
            jarTask.setUpdate(true);
            jarTask.execute();
        } catch (ManifestException ex) {
            getLog().error( "Cannot set updated manifest" );
            throw new MojoExecutionException( ex.getMessage(), ex );
        } catch (BuildException e) {
            getLog().error( "Cannot update jar" );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        try {
            original.delete();
            FileUtils.newFileUtils().copyFile(jarFile, original);
        } catch (IOException ex) {
            getLog().error("Cannot copy module jar to original location");
            throw new MojoExecutionException("Cannot copy module jar to original location", ex);
        }
    }
    
    private void conditionallyAddAttribute(Manifest.Section section, String key, String value) {
        Manifest.Attribute attr = section.getAttribute(key);
        if (attr == null) {
            attr = new Manifest.Attribute();
            attr.setName(key);
            attr.setValue(value != null ? value : "To be set.");
            try {
                section.addConfiguredAttribute(attr);
            } catch (ManifestException ex) {
                getLog().error( "Cannot update manifest (key="  + key + ")");
                ex.printStackTrace();
            }
        }
    }

}
