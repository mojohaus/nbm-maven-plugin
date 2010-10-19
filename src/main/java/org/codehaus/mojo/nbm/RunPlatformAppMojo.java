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
import java.util.List;
import java.util.ArrayList;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Run a branded application on top of NetBeans Platform. To be used with projects
 * with nbm-application packaging only and the project needs to be built first.
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal run-platform
 * @requiresDependencyResolution runtime
 *
 */
public class RunPlatformAppMojo
        extends AbstractMojo
{

    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;
    /**
     * output directory where the the netbeans application is created.
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

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
        if ( !"nbm-application".equals( project.getPackaging() ) )
        {
            throw new MojoFailureException( "The nbm:run-platform goal shall be used within a NetBeans Application project only ('nbm-application' packaging)");
        }

        netbeansUserdir.mkdirs();

        File appbasedir = new File( outputDirectory, brandingToken );

        if (!appbasedir.exists()) {
            throw new MojoExecutionException( "The directory that shall contain built application, doesn't exist (" + appbasedir.getAbsolutePath()  + ")\n Please invoke 'mvn install' on the project first");
        }

        boolean windows = Os.isFamily( "windows" );

        Commandline cmdLine = new Commandline();
        File exec;
        if (windows) {
            exec = new File(appbasedir, "bin" + brandingToken + "_w.exe");
            if (!exec.exists()) { // Was removed as of nb 6.7
                exec = new File(appbasedir, "bin\\" + brandingToken + ".exe");
                cmdLine.addArguments( new String[] { "--console", "suppress" } );
            }
        } else {
            exec = new File(appbasedir, "bin/" + brandingToken);
        }

        cmdLine.setExecutable( exec.getAbsolutePath() );

        try
        {

            List<String> args = new ArrayList<String>();
            args.add("--userdir");
            args.add(Commandline.quoteArgument( netbeansUserdir.getAbsolutePath()));
            args.add("-J-Dnetbeans.logger.console=true");
            args.add("-J-ea");
            args.add("--branding");
            args.add(brandingToken);

            // use JAVA_HOME if set
            if (System.getenv("JAVA_HOME") != null) {
                args.add("--jdkhome");
                args.add(System.getenv("JAVA_HOME"));
            }

            cmdLine.addArguments( args.toArray(new String[0]) );
            cmdLine.addArguments( CommandLineUtils.translateCommandline(
                    additionalArguments ) );
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
