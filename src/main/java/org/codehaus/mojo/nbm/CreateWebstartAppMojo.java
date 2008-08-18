/*
 *  Copyright 2008 Johan AndrÃ©n.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.codehaus.mojo.nbm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;
import java.util.jar.JarFile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.selectors.AndSelector;
import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.apache.tools.ant.types.selectors.OrSelector;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.InterpolationFilterReader;
import org.netbeans.nbbuild.MakeJNLP;
import org.netbeans.nbbuild.ModuleSelector;

/**
 * @author <a href="mailto:johan.andren@databyran.se">Johan Andren</a>
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal webstart-app
 * @phase packaging
 */
public class CreateWebstartAppMojo
        extends AbstractMojo
{

    /**
     * The Maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    /**
     * @component
     */
    protected MavenProjectHelper projectHelper;

    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;
    
    /**
     * output directory where the the netbeans application will be created.
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    private File buildDirectory;
    
    /**
     * Distributable zip file of NetBeans platform application
     * 
     * @parameter default-value="${project.build.directory}/${project.artifactId}-${project.version}-webstart.zip"
     * @required
     */
    private File destinationFile;

    /**
     * @parameter expression="${nbm.webstart.codebase}"
     * 
     */
    private String codebase = "$$codebase";

    /**
     * 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     * @throws org.apache.maven.plugin.MojoFailureException 
     */
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if ( ! "nbm-application".equals( project.getPackaging() ) )
        {
            throw new MojoExecutionException(
                    "This goal only makes sense on project with nbm-application packaging." );
        }
        
        Project antProject = new Project();
        antProject.init();

        Taskdef taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.MakeJNLP" );
        taskdef.setName( "makejnlp" );
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.MakeMasterJNLP" );
        taskdef.setName( "makemasterjnlp" );
        taskdef.execute();

        
        try
        {
            File webstartBuildDir = new File(
                    buildDirectory + File.separator + "webstart" + File.separator + brandingToken );
            if ( webstartBuildDir.exists() )
            {
                FileUtils.deleteDirectory( webstartBuildDir );
            }
            webstartBuildDir.mkdirs();
            File nbmBuildDirFile = new File( buildDirectory, brandingToken );
            
//            FileUtils.copyDirectoryStructureIfModified( nbmBuildDirFile, webstartBuildDir );
            
            MakeJNLP jnlpTask = (MakeJNLP) antProject.createTask( "makejnlp" );
            jnlpTask.setDir(webstartBuildDir);
            jnlpTask.setCodebase( codebase );
            //TODO, how to figure verify excludes..
            jnlpTask.setVerify(false);
            jnlpTask.setPermissions("<security><all-permissions/></security>");
            jnlpTask.setSignJars(false);
            FileSet fs = jnlpTask.createModules();
            fs.setDir(nbmBuildDirFile);
            OrSelector or = new OrSelector();
            AndSelector and = new AndSelector();
            FilenameSelector inc = new FilenameSelector();
            inc.setName( "*/modules/**/*.jar" );
            or.addFilename( inc );
            inc = new FilenameSelector();
            inc.setName( "*/lib/**/*.jar" );
            or.addFilename( inc );
            inc = new FilenameSelector();
            inc.setName( "*/core/**/*.jar" );
            or.addFilename( inc );
            
            ModuleSelector ms = new ModuleSelector();
            Parameter included = new Parameter();
            included.setName( "includeClusters");
            included.setValue( "");
            Parameter excluded = new Parameter();
            excluded.setName( "excludeClusters");
            excluded.setValue( "");
            Parameter exModules = new Parameter();
            exModules.setName( "excludeModules");
            exModules.setValue( "");
            ms.setParameters( new Parameter[] {
                included,
                excluded,
                exModules
            });
            and.add( or);
            and.add( ms );
            fs.addAnd( and );
            jnlpTask.execute();

            //TODO is it really netbeans/
            String extSnippet = generateExtensions(fs, antProject, "netbeans/"); 

            Properties props = new Properties();
            props.setProperty("jnlp.resources", extSnippet);
            props.setProperty( "jnlp.codebase", codebase);
            props.setProperty( "app.name", brandingToken);
            props.setProperty( "app.title", project.getName() );
            if ( project.getOrganization() != null )
            {
                props.setProperty( "app.vendor",
                        project.getOrganization().getName() );
            } else
            {
                props.setProperty( "app.vendor", "Nobody" );
            }
            String description = project.getDescription() != null ? project.getDescription() : "No Project Description";
            props.setProperty( "app.description", description );
            props.setProperty( "branding.token", brandingToken );
            props.setProperty("netbeans.jnlp.fixPolicy", "true");
            File masterJnlp = new File(
                    webstartBuildDir.getAbsolutePath() + File.separator + "master.jnlp" );
            filterCopy( "/master.jnlp", masterJnlp, props );
            copyLauncher( webstartBuildDir, nbmBuildDirFile );
            
            
//
//            // setup filter properties for the jnlp-templates
//            Properties filterProperties = new Properties();
//            filterProperties.setProperty( "app.title", project.getName() );
//            if ( project.getOrganization() != null )
//            {
//                filterProperties.setProperty( "app.vendor",
//                        project.getOrganization().getName() );
//            } else
//            {
//                filterProperties.setProperty( "app.vendor", "Nobody" );
//            }
//            String description = project.getDescription() != null ? project.getDescription() : "No Project Description";
//            filterProperties.setProperty( "app.description", description );
//            filterProperties.setProperty( "branding.token", brandingToken );
//
//            // split default options into <argument> blocks
//            StringBuffer args = new StringBuffer();
//            //TODO
////            if ( defaultOptions != null )
////            {
////                for ( String arg : defaultOptions.split( " " ) )
////                {
////                    args.append( "<argument>" );
////                    args.append( arg );
////                    args.append( "</argument>\n" );
////                }
////            }
//            filterProperties.setProperty( "app.arguments", args.toString() );
//
//            // external library jars first, modules depend on them
//            DirectoryScanner scanner = new DirectoryScanner();
//            scanner.setBasedir( webstartBuildDir );
//            scanner.setIncludes( new String[]
//                    {
//                        "**/modules/ext/*.jar"
//                    } );
//            scanner.scan();
//
//            StringBuffer externalJnlps = new StringBuffer();
//
//            // one jnlp for each since they could be signed by other certificates
//            for ( String extJarPath : scanner.getIncludedFiles() )
//            {
//                String jarHref = extJarPath.replace(
//                        webstartBuildDir.getAbsolutePath(), "" );
//                String externalName = new File( extJarPath ).getName().replace(
//                        ".jar", "" );
//                filterProperties.setProperty( "external.jar",
//                        "<jar href=\"" + jarHref + "\"/>" );
//                File externalJnlp = new File(
//                        webstartBuildDir.getAbsolutePath() + File.separator + externalName + ".jnlp" );
//                filterCopy( "/external.jnlp", externalJnlp, filterProperties );
//
//                externalJnlps.append( "<extension name=\"" );
//                externalJnlps.append( externalName );
//                externalJnlps.append( "\" href=\"" );
//                externalJnlps.append( externalJnlp.getName() );
//                externalJnlps.append( "\"/>\n" );
//            }
//            filterProperties.setProperty( "application.ext.jnlps",
//                    externalJnlps.toString() );
//
//            // one jnlp for the platform files
//            scanner.setIncludes( new String[]
//                    {
//                        "platform*/**/*.jar"
//                    } );
//            scanner.scan();
//
//            String platformHrefs = createJarHrefBlock(
//                    scanner.getIncludedFiles(), webstartBuildDir );
//            filterProperties.setProperty( "platform.jars", platformHrefs );
//
//            File platformJnlp = new File(
//                    webstartBuildDir.getAbsolutePath() + File.separator + "platform.jnlp" );
//            filterCopy( "/platform.jnlp", platformJnlp, filterProperties );
//
//            // all regular modules and eagerly loaded modules
//            scanner.setIncludes( new String[]
//                    {
//                        "**/modules/*.jar", "**/modules/eager/*.jar"
//                    } );
//            scanner.setExcludes( new String[]
//                    {
//                        "platform*/**", brandingToken + "/**"
//                    } );
//            scanner.scan();
//            String moduleJars = createJarHrefBlock( scanner.getIncludedFiles(),
//                    webstartBuildDir );
//            filterProperties.setProperty( "application.jars", moduleJars );
//
//            File masterJnlp = new File(
//                    webstartBuildDir.getAbsolutePath() + File.separator + "master.jnlp" );
//            filterCopy( "/master.jnlp", masterJnlp, filterProperties );
//
//            // branding modules
//            // all jars for branding should be directly in the brandingToken-directory
//            scanner.setIncludes( new String[]
//                    {
//                        brandingToken + "/**/*.jar"
//                    } );
//            scanner.setExcludes( new String[]
//                    {
//                    } );
//            scanner.scan();
//            File brandingDirectory = new File(
//                    webstartBuildDir.getAbsolutePath() + File.separator + brandingToken );
//            for ( String brandingJarPath : scanner.getIncludedFiles() )
//            {
//                File brandingJar = new File(
//                        webstartBuildDir.getAbsolutePath() + File.separator + brandingJarPath );
//                File brandingDirectoryJar = new File(
//                        brandingDirectory + File.separator + brandingJar.getName() );
//                FileUtils.copyFile( brandingJar, brandingDirectoryJar );
//                brandingJar.delete();
//            }
//
//            scanner.setIncludes( new String[]
//                    {
//                        brandingToken + "/*.jar"
//                    } );
//            scanner.scan();
//            String brandingJarHrefs = createJarHrefBlock(
//                    scanner.getIncludedFiles(), webstartBuildDir );
//            filterProperties.setProperty( "branding.jars", brandingJarHrefs );
//
//            File brandingJnlp = new File(
//                    webstartBuildDir.getAbsolutePath() + File.separator + "branding.jnlp" );
//            filterCopy( "/branding.jnlp", brandingJnlp, filterProperties );
//
//            // TODO sign jars
//
//            // create zip archive
//            if ( destinationFile.exists() )
//            {
//                destinationFile.delete();
//            }
//            ZipArchiver archiver = new ZipArchiver();
//            archiver.addDirectory( webstartBuildDir );
//            archiver.setDestFile( destinationFile );
//            archiver.createArchive();
//
//            // attach standalone so that it gets installed/deployed
//            projectHelper.attachArtifact( project, "zip", "webstart",
//                    destinationFile );

        } catch ( Exception ex )
        {
            throw new MojoExecutionException( "Error creating webstartable binary.", ex );
        }


    }

    /**
     * @param standaloneBuildDir
     * @return The name of the jnlp-launcher jarfile in the build directory
     */
    private void copyLauncher( File standaloneBuildDir, File builtInstallation ) throws IOException
    {
        File jnlpStarter = new File( builtInstallation.getAbsolutePath() +
                File.separator + "harness" +
            File.separator + "jnlp" +
            File.separator + "jnlp-launcher.jar" );
        // buffer so it isn't reading a byte at a time!
        InputStream source = null;
        FileOutputStream outstream = null;
        try
        {
            if ( !jnlpStarter.exists() )
            {
                source = getClass().getClassLoader().getResourceAsStream(
                    "webstart/jnlp-launcher.jar" );
            } else {
                source = new FileInputStream(jnlpStarter);
            }
            File jnlpDestination = new File(
                standaloneBuildDir.getAbsolutePath() + File.separator + "startup.jar" );

            outstream = new FileOutputStream( jnlpDestination );
            IOUtil.copy( source, outstream );
        }
        finally
        {
            IOUtil.close( source );
            IOUtil.close( outstream );
        }
    }

    private void filterCopy( String resourcePath, File destinationFile, Properties filterProperties )
            throws IOException
    {
        // buffer so it isn't reading a byte at a time!
        Reader source = null;
        Writer destination = null;
        try
        {
            InputStream instream = getClass().getClassLoader().getResourceAsStream(
                    resourcePath );
            FileOutputStream outstream = new FileOutputStream( destinationFile );

            source = new BufferedReader( new InputStreamReader( instream,
                    "UTF-8" ) );
            destination = new OutputStreamWriter( outstream, "UTF-8" );

            // support ${token}
            Reader reader = new InterpolationFilterReader( source,
                    filterProperties, "${", "}" );

            IOUtil.copy( reader, destination );
        } finally
        {
            IOUtil.close( source );
            IOUtil.close( destination );
        }
    }

    private String createJarHrefBlock( String[] absolutePaths, File buildDir )
    {
        String remove = buildDir.getAbsolutePath();
        StringBuffer buffer = new StringBuffer();
        for ( String absolutePath : absolutePaths )
        {
            buffer.append( "   <jar href=\"" );
            buffer.append( absolutePath.replace( remove, "" ) );
            buffer.append( "\"/>\n" );
        }

        return buffer.toString();
    }
    

    /**
     * copied from MakeMasterJNLP ant task.
     * @param files
     * @param antProject
     * @param masterPrefix
     * @return
     * @throws java.io.IOException
     */
    private String generateExtensions(FileSet files, Project antProject, String masterPrefix) throws IOException {
        StringBuffer buff = new StringBuffer();
        for (String nm : files.getDirectoryScanner(antProject).getIncludedFiles()) {
            File jar = new File (files.getDir(antProject), nm);
            
            if (!jar.canRead()) {
                throw new IOException("Cannot read file: " + jar);
            }
            
            JarFile theJar = new JarFile(jar);
            String codenamebase = theJar.getManifest().getMainAttributes().getValue("OpenIDE-Module");
            if (codenamebase == null) {
                throw new IOException("Not a NetBeans Module: " + jar);
            }
            {
                int slash = codenamebase.indexOf('/');
                if (slash >= 0) {
                    codenamebase = codenamebase.substring(0, slash);
                }
            }
            String dashcnb = codenamebase.replace('.', '-');

            buff.append("    <extension name='" + codenamebase + "' href='" + masterPrefix + dashcnb + ".jnlp' />\n");
            theJar.close();
        }
        return buff.toString();
        
    }
    
}
