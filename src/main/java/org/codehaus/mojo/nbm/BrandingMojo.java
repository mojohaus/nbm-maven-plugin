/* ==========================================================================
 * Copyright 2007 Mevenide Team
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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.netbeans.nbbuild.Branding;

/**
 * Package branding for NetBeans platform/IDE based application.
 * TODO more docs.
 * </p>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal branding
 * @phase package
 * @requiresProject
 *
 */
public class BrandingMojo
        extends AbstractNbmMojo {
    
    /**
     * directory where the the netbeans jar and nbm file will be constructed
     * @parameter expression="${project.build.directory}/nbm"
     * @required
     */
    protected String nbmBuildDir;
    
    /**
     * Location of the branded resources.
     * @parameter expression="${basedir}/src/main/nbm-branding"
     * @required
     */
    private String brandingSources;

    /**
     * The branding token used by the application.
     * @parameter 
     * @required
     */
    private String brandingToken;
    
    /**
     * cluster of the branding.
     *
     * @parameter
     * @required
     */
    protected String cluster;
    
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    
    public void execute() throws MojoExecutionException {
        Project antProject = registerNbmAntTasks();

        //load task..
        Taskdef taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname("org.netbeans.nbbuild.Branding" );
        taskdef.setName("branding");
        taskdef.execute();

        Branding brandingTask = (Branding)antProject.createTask("branding");
        brandingTask.setToken(brandingToken);
        
        File clusterDir = new File(nbmBuildDir, "netbeans" + File.separator + cluster);
        clusterDir.mkdirs();
        brandingTask.setCluster(clusterDir);
        
        File brandSourcesDir = new File(brandingSources);
        if (!brandSourcesDir.exists()) {
            throw new MojoExecutionException("brandingSources have to exist at " + brandingSources);
        }
        brandingTask.setOverrides(brandSourcesDir);
        try {
            brandingTask.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot brand application" );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        getLog().info("Created branded jars for branding '" + brandingToken + "'.");
    }
}
