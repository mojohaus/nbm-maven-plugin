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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;

/**
 * Create the Netbeans module clusters from reactor
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal cluster
 * @aggregator
 * @requiresDependencyResolution runtime
 *
 */
public class CreateClusterMojo
        extends AbstractNbmMojo {
    /**
     * directory where the the netbeans cluster will be created.
     * @parameter default-value="${project.build.directory}/netbeans_clusters"
     * @required
     */
    protected String nbmBuildDir;
    
    
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
    
    
    public void execute() throws MojoExecutionException, MojoFailureException {
        Project antProject = registerNbmAntTasks();
            
        File nbmBuildDirFile = new File(nbmBuildDir);
        if (!nbmBuildDirFile.exists()) {
            nbmBuildDirFile.mkdirs();
        }
        
        if (reactorProjects != null && reactorProjects.size() > 0) {
            Iterator it = reactorProjects.iterator();
            while (it.hasNext()) {
                MavenProject proj = (MavenProject)it.next();
                //TODO how to figure where the the buildDir/nbm directory is
                File nbmDir = new File(proj.getBasedir(), "target" + File.separator + "nbm" + File.separator + "netbeans");
                if (nbmDir.exists()) {
                    Copy copyTask = (Copy)antProject.createTask("copy");
                    copyTask.setTodir(nbmBuildDirFile);
                    copyTask.setOverwrite(true);
                    FileSet set = new FileSet();
                    set.setDir(nbmDir);
                    set.createInclude().setName("**");
                    copyTask.addFileset(set);
                
                    try {
                        copyTask.execute();
                    } catch (BuildException ex) {
                        getLog().error("Cannot merge modules into cluster");
                        throw new MojoExecutionException("Cannot merge modules into cluster", ex);
                    }
                } else {
                    if ("nbm".equals(proj.getPackaging())) {
                        String error = "Since 2.7, the nbm:nbm goal is not part of the lifecycle. \nTherefore the NetBeans binary directory structure for " + proj.getId() + " is not created yet." +
                                "\n Please execute 'mvn install nbm:directory nbm:cluster' to get the same results as in earlier versions.";
                        throw new MojoFailureException(error);
                    }
                }
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
            getLog().info("Created NetBeans module cluster(s) at " + nbmBuildDir);
        } else {
            throw new MojoExecutionException("This goal only makes sense on reactor projects.");
        }
    }
}
