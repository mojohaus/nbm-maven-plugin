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
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.StringInputStream;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.io.InputStreamFacade;

/**
 * Create the Netbeans module clusters from reactor
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal cluster
 * @aggregator
 * @requiresDependencyResolution runtime
 *
 */
public class CreateClusterMojo
        extends AbstractNbmMojo
{

    /**
     * directory where the the netbeans cluster will be created.
     * @parameter default-value="${project.build.directory}/netbeans_clusters"
     * @required
     */
    protected File nbmBuildDir;

    /**
     * default cluster value for reactor projects without cluster information,
     * typically OSGi bundles
     * @parameter default-value="extra"
     * @since 3.2
     */
    private String defaultCluster;
    /**
     * If the executed project is a reactor project, this will contains the full list of projects in the reactor.
     *
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    private List<MavenProject> reactorProjects;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Project antProject = registerNbmAntTasks();

        if ( !nbmBuildDir.exists() )
        {
            nbmBuildDir.mkdirs();
        }

        if ( reactorProjects != null && reactorProjects.size() > 0 )
        {
            for ( MavenProject proj : reactorProjects )
            {
                //TODO how to figure where the the buildDir/nbm directory is
                File nbmDir = new File( proj.getBasedir(),
                        "target" + File.separator + "nbm" + File.separator + "netbeans" );
                if ( nbmDir.exists() )
                {
                    Copy copyTask = (Copy) antProject.createTask( "copy" );
                    copyTask.setTodir( nbmBuildDir );
                    copyTask.setOverwrite( true );
                    FileSet set = new FileSet();
                    set.setDir( nbmDir );
                    set.createInclude().setName( "**" );
                    copyTask.addFileset( set );

                    try
                    {
                        copyTask.execute();
                    } catch ( BuildException ex )
                    {
                        getLog().error( "Cannot merge modules into cluster" );
                        throw new MojoExecutionException(
                                "Cannot merge modules into cluster", ex );
                    }
                } else
                {
                    if ( "nbm".equals( proj.getPackaging() ) )
                    {
                        String error = "The NetBeans binary directory structure for " + proj.getId() + " is not created yet." +
                                "\n Please execute 'mvn install nbm:cluster' to build all relevant projects in the reactor.";
                        throw new MojoFailureException( error );
                    }
                    if ("bundle".equals(  proj.getPackaging() ))
                    {
                        Artifact art = proj.getArtifact();
                        ExamineManifest mnf = new ExamineManifest( getLog() );

                        File jar = new File(proj.getBuild().getDirectory(), proj.getBuild().getFinalName() + ".jar");
                        if ( !jar.exists() )
                        {
                            getLog().error( "Skipping " + proj.getId() + ". Cannot find the main artifact in output directory.");
                            continue;
                        }
                        mnf.setJarFile( jar );
                        mnf.checkFile();

                        File cluster = new File(nbmBuildDir, defaultCluster);
                        getLog().debug( "Copying " + art.getId() + " to cluster " + defaultCluster );
                        File modules = new File(cluster, "modules");
                        modules.mkdirs();
                        File config = new File(cluster, "config");
                        File confModules = new File(config, "Modules");
                        confModules.mkdirs();
                        File updateTracting = new File(cluster, "update_tracking");
                        updateTracting.mkdirs();

                        final String cnb = mnf.getModule();
                        final String cnbDashed = cnb.replace( ".", "-");
                        final File moduleArt = new File(modules, cnbDashed + ".jar" ); //do we need the file in some canotical name pattern?
                        final String specVer = mnf.getSpecVersion();
                        try
                        {
                            FileUtils.copyFile( jar, moduleArt );
                            final File moduleConf = new File(confModules, cnbDashed + ".xml");
                            FileUtils.copyStreamToFile( new InputStreamFacade() {
                                public InputStream getInputStream() throws IOException
                                {
                                    return new StringInputStream( CreateClusterAppMojo.createBundleConfigFile(cnb), "UTF-8");
                                }
                            }, moduleConf);
                            FileUtils.copyStreamToFile( new InputStreamFacade() {
                                public InputStream getInputStream() throws IOException
                                {
                                    return new StringInputStream( CreateClusterAppMojo.createBundleUpdateTracking(cnb, moduleArt, moduleConf, specVer), "UTF-8");
                                }
                            }, new File(updateTracting, cnbDashed + ".xml"));
                        }
                        catch ( IOException exc )
                        {
                            getLog().error( exc );
                        }

                    }
                }
            }
            //in 6.1 the rebuilt modules will be cached if the timestamp is not touched.
            File[] files = nbmBuildDir.listFiles();
            for ( int i = 0; i < files.length; i++ )
            {
                if ( files[i].isDirectory() )
                {
                    File stamp = new File( files[i], ".lastModified" );
                    if ( !stamp.exists() )
                    {
                        try
                        {
                            stamp.createNewFile();
                        } catch ( IOException ex )
                        {
                            ex.printStackTrace();
                        }
                    }
                    stamp.setLastModified( new Date().getTime() );
                }
            }
            getLog().info(
                    "Created NetBeans module cluster(s) at " + nbmBuildDir );
        } else
        {
            throw new MojoExecutionException(
                    "This goal only makes sense on reactor projects." );
        }
    }
}
