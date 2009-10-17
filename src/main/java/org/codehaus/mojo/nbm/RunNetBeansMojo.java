/* ==========================================================================
 * Copyright 2003-2007 Mevenide Team
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Run NetBeans IDE with additional custom module clusters, 
 * to be used in conjunction with nbm:cluster.
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal run-ide
 * @aggregator
 * @requiresDependencyResolution runtime
 *
 */
public class RunNetBeansMojo
        extends AbstractMojo
{

    /**
     * directory where the module(s)' netbeans cluster(s) are located.
     * is related to nbm:cluster goal.
     * @parameter default-value="${project.build.directory}/netbeans_clusters"
     * @required
     */
    protected File clusterBuildDir;
    /**
     * directory where the the netbeans platform/IDE installation is,
     * denotes the root directory of netbeans installation.
     * @parameter expression="${netbeans.installation}"
     * @required
     */
    protected File netbeansInstallation;
    /**
     * netbeans user directory for the executed instance.
     * @parameter default-value="${project.build.directory}/userdir" expression="${netbeans.userdir}"
     * @required
     */
    protected File netbeansUserdir;
    /**
     * additional command line arguments. Eg. 
     * -J-Xdebug -J-Xnoagent -J-Xrunjdwp:transport=dt_socket,suspend=n,server=n,address=8888
     * can be used to debug the IDE.
     * @parameter expression="${netbeans.run.params}"
     */
    protected String additionalArguments;
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     * @throws org.apache.maven.plugin.MojoFailureException 
     */
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        netbeansUserdir.mkdirs();

        List clusters = new ArrayList();
        if (!clusterBuildDir.exists() || clusterBuildDir.listFiles() == null) {
            throw new MojoExecutionException("No clusters to include in execution found. Please run the nbm:cluster or nbm:cluster-app goals before this one.");
        }
        File[] fls = clusterBuildDir.listFiles();
        for ( int i = 0; i < fls.length; i++ )
        {
            if ( fls[i].isDirectory() )
            {
                clusters.add( fls[i] );
            }
        }
        StringBuffer buff = new StringBuffer();
        buff.append( "netbeans_extraclusters=\"" );
        Iterator it = clusters.iterator();
        while ( it.hasNext() )
        {
            File cluster = (File) it.next();
            buff.append( cluster.getAbsolutePath() );
            buff.append( ":" );
        }
        if (buff.lastIndexOf( ":") > -1) {
            buff.deleteCharAt( buff.lastIndexOf( ":" ) );
        }
        buff.append( "\"" );
        StringReader sr = new StringReader( buff.toString() );


        //now check what the exec names are to figure the right XXX.clusters name
        File binDir = new File( netbeansInstallation, "bin" );
        File[] execs = binDir.listFiles();
        String clust = null;
        if ( execs != null )
        {
            for ( File f : execs )
            {
                String name = f.getName();
                System.out.println( "filename=" + name );
                if ( name.contains( "_w.exe" ) ) {
                    continue;
                }
                if ( name.contains( ".exe" ) )
                {
                    name = name.substring( 0, name.length() - ".exe".length() );
                }
                if ( !name.contains( "." ) )
                {
                    if ( clust == null )
                    {
                        clust = name;
                    }
                    else
                    {
                        if ( !clust.equals( name ) )
                        {
                            getLog().debug( "When examining executable names, found clashing results " + f.getName() + " " + clust);
                        }
                    }
                }
            }
        }
        if ( clust == null) {
            clust = "netbeans";
        }

        // write XXX.conf file with cluster information...
        File etc = new File( netbeansUserdir, "etc" );
        etc.mkdirs();
        File confFile = new File( etc, clust + ".conf" );
        FileOutputStream conf = null;
        try
        {
            conf = new FileOutputStream( confFile );
            IOUtil.copy( sr, conf );
        } catch ( IOException ex )
        {
            throw new MojoExecutionException( "Error writing " + confFile, ex );
        } finally
        {
            IOUtil.close( conf );
        }

        boolean windows = Os.isFamily( "windows" );
        Commandline cmdLine = new Commandline();
        File exec;
        if (windows) {
            exec = new File( netbeansInstallation, "bin\\nb.exe" );
            if (!exec.exists()) {
                // in 6.7 and onward, there's no nb.exe file.
               exec = new File( netbeansInstallation, "bin\\" + clust + ".exe" );
            }
        } else {
            exec = new File(netbeansInstallation, "bin/" + clust );
        }
        cmdLine.setExecutable( exec.getAbsolutePath() );

        try
        {
            String[] args = new String[]
            {
                //TODO --jdkhome
                "--userdir",
                Commandline.quoteArgument( netbeansUserdir.getAbsolutePath() ),
                "-J-Dnetbeans.logger.console=true",
                "-J-ea",
            };
            cmdLine.addArguments( args );
            getLog().info( "Additional arguments=" + additionalArguments );
            cmdLine.addArguments( cmdLine.translateCommandline(
                    additionalArguments ) );
            for ( int i = 0; i < cmdLine.getArguments().length; i++ )
            {
                getLog().info( "      " + cmdLine.getArguments()[i] );
            }
            getLog().info( "Executing: " + cmdLine.toString() );
            StreamConsumer out = new StreamConsumer()
            {

                public void consumeLine( String line )
                {
                    getLog().info( line );
                }
            };
            CommandLineUtils.executeCommandLine( cmdLine, out, out );

        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Failed executing NetBeans", e );
        }
    }
}
