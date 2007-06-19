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
import java.util.Iterator;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.netbeans.nbbuild.MakeUpdateDesc;

/**
 * Create the Netbeans autupdate site
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal autoupdate
 * @aggregator
 * @requiresDependencyResolution runtime
 *
 */
public class CreateUpdateSiteMojo
        extends AbstractNbmMojo {
    /**
     * directory where the the netbeans autoupdate site will be created.
     * @parameter expression="${project.build.directory}/netbeans_site"
     * @required
     */
    protected String nbmBuildDir;
    
    /**
     * autoupdate site xml file name.
     * @parameter expression="${maven.nbm.updatesitexml}" default-value="Autoupdate_Site.xml"
     * @required
     */
    protected String fileName;
    
    /**
     * A custom distribution base for the nbms in the update site.
     * The actual download URL for the module's nbm is composed of
     * ${distBase}/${name of module}.nbm
     *@parameter expression="${maven.nbm.customDistBase}"
     */
    protected String distBase;
    
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    
    /**
     * If the executed project is a reactor project, this will contains the full list of projects in the reactor.
     *
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    private List reactorProjects;
    
    
    public void execute() throws MojoExecutionException {
        Project antProject = registerNbmAntTasks();
        
        
        if (reactorProjects != null && reactorProjects.size() > 0) {
            File nbmBuildDirFile = new File(nbmBuildDir);
            if (!nbmBuildDirFile.exists()) {
                nbmBuildDirFile.mkdirs();
            }
            Iterator it = reactorProjects.iterator();
            while (it.hasNext()) {
                MavenProject proj = (MavenProject)it.next();
                //TODO how to figure where the the buildDir/nbm directory is
                File moduleDir = proj.getFile().getParentFile();
                if (moduleDir != null && moduleDir.exists()) {
                    Copy copyTask = (Copy)antProject.createTask("copy");
                    copyTask.setTodir(nbmBuildDirFile);
                    FileSet fs = new FileSet();
                    fs.setDir(moduleDir);
                    fs.createInclude().setName("target/*.nbm");
                    copyTask.addFileset(fs);
                    copyTask.setOverwrite(true);
                    copyTask.setFlatten(true);
                    try {
                        copyTask.execute();
                    } catch (BuildException ex) {
                        getLog().error("Cannot merge nbm files into autoupdate site");
                        throw new MojoExecutionException("Cannot merge nbm files into autoupdate site", ex);
                    }
                }
            }
            MakeUpdateDesc descTask = (MakeUpdateDesc)antProject.createTask("updatedist");
            descTask.setDesc(new File(nbmBuildDirFile, fileName).toString());
            if (distBase !=null) {
                descTask.setDistBase(distBase);
            }
            FileSet fs = new FileSet();
            fs.setDir(nbmBuildDirFile);
            fs.createInclude().setName("**/*.nbm");
            descTask.addFileset(fs);
            try {
                descTask.execute();
            } catch (BuildException ex) {
                getLog().error("Cannot create autoupdate site xml file");
                throw new MojoExecutionException("Cannot create autoupdate site xml file", ex);
            }
            getLog().info("Generated autoupdate site content at " + nbmBuildDir);
        } else {
            throw new MojoExecutionException("This goal only makes sense on reactor projects.");
        }
    }
}
