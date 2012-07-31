/*
 * Copyright 2012 Frantisek Mantlik <frantisek at mantlik.cz>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * under the License.
 */
package org.codehaus.mojo.nbm;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.util.StringUtils;

/**
 * Build installers for Mavenized NetBeans application.
 * Creates installers for supported operating systems
 * and packages each installer as a deployable artifact.
 * <p>See a <a href="http://mojo.codehaus.org/nbm-maven/nbm-maven-plugin/buildinstexample.html">how-to</a> on customizing the installer.
 * @author <a href="mailto:frantisek@mantlik.cz">Frantisek Mantlik</a>
 */
@Mojo(name="build-installers", 
        requiresProject=true, 
        requiresDependencyResolution=ResolutionScope.RUNTIME,
        defaultPhase=LifecyclePhase.PACKAGE )
public class BuildInstallersMojo
        extends AbstractNbmMojo
{

    /**
    * output directory.
    */
    @Parameter(defaultValue="${project.build.directory}", required=true)
    protected File outputDirectory;
    /**
    * The branding token for the application based on NetBeans platform.
    */
    @Parameter(property="netbeans.branding.token", required=true)
    protected String brandingToken;
    /**
    * Installation directory name at the destination system
    */
    @Parameter(property="netbeans.branding.token")
    protected String installDirName;
    /**
    * Prefix of all generated installers files
    */
    @Parameter(defaultValue="${project.build.finalName}")
    private String installersFilePrefix;
    /**
     * Create installer for Windows
     */
    @Parameter(defaultValue="true")
    private boolean installerOsWindows;
    /**
     * Create installer for Solaris
     */
    @Parameter(defaultValue="true")
    private boolean installerOsSolaris;
    /**
     * Create installer for Linux
     */
    @Parameter(defaultValue="true")    
    private boolean installerOsLinux;
    /**
     * Create installer for MacOSx
     */
    @Parameter(defaultValue="true")    
    private boolean installerOsMacosx;
    /**
     * Enable Pack200 compression
     */
    @Parameter(defaultValue="true")
    private boolean installerPack200Enable;
    /**
     * License file
     */
    @Parameter(defaultValue="${basedir}/license.txt")
    private File installerLicenseFile;
    /**
     * Custom installer template.
     * This file, if provided, will replace default template from
     * &lt;NetBeansInstallation&gt;/harness/nbi/stub/template.xml
     */
    @Parameter
    private File templateFile;
    /**
     * Parameters passed to templateFile 
     * or to installer/nbi/stub/template.xml 
     * to customize generated installers.
     *
     */
    @Parameter
    private Map<String, String> userSettings;
    
    /**
     * Name of the zip artifact used to produce installers from (without .zip extension)
     */
    @Parameter(defaultValue="${project.build.finalName}")
    private String finalName;

    // <editor-fold defaultstate="collapsed" desc="Component parameters">
    /**
     * Used for attaching the artifact in the project
     */
    @Component
    private MavenProjectHelper projectHelper;
        
    @Parameter(readonly=true, required=true, property="basedir")
    private File basedir;
    /**
    * The Maven Project.
    */
    @Parameter(required=true, readonly=true, property="project")    
    private MavenProject project;    

    // </editor-fold>
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        Project antProject = antProject();

        if ( !"nbm-application".equals( project.getPackaging() ) )
        {
            throw new MojoExecutionException(
                    "This goal only makes sense on project with 'nbm-application' packaging." );
        }

        String zipName = finalName + ".zip";
        File zipFile = new File( outputDirectory, zipName );
        getLog().info( String.format( "Running Build Installers action for (existing=%2$s) zip file %1$s",
                zipFile, zipFile.exists() ) );


        File appIconIcnsFile;

        // Copy Netbeans Installer resources
        FileUrlUtils fu = new FileUrlUtils();
        File harnessDir = new File( outputDirectory, "installer" );
        fu.copyResourcesRecursively( getClass().getClassLoader().getResource( "harness" ), harnessDir );

        // Overwrite template file with modified version to accept branded images etc.
        if ( templateFile != null )
        {
            File template = new File( harnessDir, "nbi/stub/template.xml" );
            fu.copyFile( templateFile, template );
        }

        appIconIcnsFile = new File( harnessDir, "etc" + File.separatorChar + "applicationIcon.icns" );
        getLog().info( "Application icon:" + appIconIcnsFile.getAbsolutePath() );

        Map<String, String> props = new HashMap<String, String> ();

        //TODO mkleint: this is wrong.. why reference the parent directory of current project? what guarantees that something is there.
        props.put( "suite.location", basedir.getParentFile().getAbsolutePath().replace( "\\", "/" ) );
        props.put( "suite.dist.zip", zipFile.getAbsolutePath().replace( "\\", "/" ) );
        props.put( "suite.dist.directory", outputDirectory.getAbsolutePath().replace( "\\", "/" ) );
        props.put( "installer.build.dir", new File( outputDirectory, "installerbuild" ).getAbsolutePath().replace( "\\", "/" ) );
        
        props.put( "installers.file.prefix", installersFilePrefix );

        props.put( "install.dir.name", installDirName );

        //mkleint: this is a flawed pattern! cannot make any assumption on multimodule layout
        String appName = project.getParent().getArtifactId().replace( ".", "" ).replace( "-", "" ).replace( "_", "" ).replaceAll( "[0-9]+", "" );
        props.put( "suite.nbi.product.uid", appName.toLowerCase( Locale.ENGLISH ) );

        props.put( "suite.props.app.title", ( project.getName() + " " + project.getVersion() ).replaceAll( "-SNAPSHOT", "" ) );

        String appVersion = project.getVersion().replaceAll( "-SNAPSHOT", "" );
        props.put( "suite.nbi.product.version.short", appVersion );
        while ( appVersion.split( "\\." ).length < 5 )
        {
            appVersion += ".0";
        }
        props.put( "suite.nbi.product.version", appVersion );

        props.put( "nbi.stub.location", new File( harnessDir, "nbi/stub" ).getAbsolutePath().replace( "\\", "/" ) );

        props.put( "nbi.stub.common.location", new File( harnessDir, "nbi/.common" ).getAbsolutePath().replace( "\\", "/" ) );

        props.put( "nbi.ant.tasks.jar", new File( harnessDir, "modules/ext/nbi-ant-tasks.jar" ).getAbsolutePath().replace( "\\", "/" ) );

        props.put( "nbi.registries.management.jar", new File( harnessDir, "modules/ext/nbi-registries-management.jar" ).getAbsolutePath().replace( "\\", "/" ) );

        props.put( "nbi.engine.jar", new File( harnessDir, "modules/ext/nbi-engine.jar" ).getAbsolutePath().replace( "\\", "/" ) );

        if ( installerLicenseFile != null )
        {
            getLog().info( String.format( "License file is at %1s, exist = %2$s", installerLicenseFile, installerLicenseFile.exists() ) );
            props.put( "nbi.license.file", installerLicenseFile.getAbsolutePath() ); //mkleint: no path replacement here??
        }

        List<String> platforms = new ArrayList<String>();

        if ( this.installerOsLinux )
        {
            platforms.add( "linux" );
            File linuxFile = new File( outputDirectory, installersFilePrefix + "-linux.sh" );
            projectHelper.attachArtifact( project, "sh", "linux", linuxFile );
        }
        if ( this.installerOsSolaris )
        {
            platforms.add( "solaris" );
            File solarisFile = new File( outputDirectory, installersFilePrefix + "-solaris.sh" );
            projectHelper.attachArtifact( project, "sh", "solaris", solarisFile );
        }
        if ( this.installerOsWindows )
        {
            platforms.add( "windows" );
            File windowsFile = new File( outputDirectory, installersFilePrefix + "-windows.exe" );
            projectHelper.attachArtifact( project, "exe", "windows", windowsFile );
        }
        if ( this.installerOsMacosx )
        {
            platforms.add( "macosx" );
            File macosxFile = new File( outputDirectory, installersFilePrefix + "-macosx.tgz" );
            projectHelper.attachArtifact( project, "tgz", "macosx", macosxFile );
        }

        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < platforms.size(); i++ )
        {
            if ( i != 0 )
            {
                sb.append( " " );
            }
            sb.append( platforms.get( i ) );
        }
        if ( sb.length() == 0 )
        {
            //nothing to build
            getLog().warn( "Nothing to build." );
        }

        props.put( "generate.installer.for.platforms", sb.toString() );

        File javaHome = new File( System.getProperty( "java.home" ) );
        if ( new File( javaHome, "lib/rt.jar" ).exists() && javaHome.getName().equals( "jre" ) ) //mkleint: does this work on mac? no rt.jar there
        {
            javaHome = javaHome.getParentFile();
        }
        props.put( "generator-jdk-location-forward-slashes", javaHome.getAbsolutePath().replace( "\\", "/" ) );

        props.put( "pack200.enabled", "" + installerPack200Enable );

        if ( appIconIcnsFile != null )
        {
            props.put( "nbi.dock.icon.file", appIconIcnsFile.getAbsolutePath() );
        }

        try
        {
            antProject.setUserProperty( "ant.file", new File( harnessDir, "nbi/stub/template.xml" ).getAbsolutePath().replace( "\\", "/" ) );
            ProjectHelper helper = ProjectHelper.getProjectHelper();
            antProject.addReference( "ant.projectHelper", helper );
            helper.parse( antProject, new File( harnessDir, "nbi/stub/template.xml" ) );
            for ( Map.Entry<String, String> e : props.entrySet() )
            {
                antProject.setProperty( e.getKey(), e.getValue() );
            }
            if ( userSettings != null )
            {
                for ( Map.Entry<String, String> e : userSettings.entrySet() )
                {
                    antProject.setProperty( e.getKey(), e.getValue() );
                }
            }
            antProject.executeTarget( "build" );
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "Installers creation failed: " + ex, ex );
        }
    }

    //mkleint: could this be replaced by something from plexus-utils?
    private class FileUrlUtils
    {

        boolean copyFile( final File toCopy, final File destFile )
            throws MojoExecutionException
        {
            try
            {
                return copyStream( new FileInputStream( toCopy ), new FileOutputStream( destFile ) );
            }
            catch ( final FileNotFoundException e )
            {
                throw new MojoExecutionException( "Installers creation failed: " + e, e );
            }
        }

        boolean copyFilesRecusively( final File toCopy, final File destDir )
            throws MojoExecutionException
        {
            assert destDir.isDirectory();

            if ( !toCopy.isDirectory() )
            {
                return copyFile( toCopy, new File( destDir, toCopy.getName() ) );
            }
            else
            {
                final File newDestDir = new File( destDir, toCopy.getName() );
                if ( !newDestDir.exists() && !newDestDir.mkdir() )
                {
                    return false;
                }
                for ( final File child : toCopy.listFiles() )
                {
                    if ( !copyFilesRecusively( child, newDestDir ) )
                    {
                        return false;
                    }
                }
            }
            return true;
        }

        boolean copyJarResourcesRecursively( final File destDir, final JarURLConnection jarConnection )
            throws IOException, MojoExecutionException
        {

            final JarFile jarFile = jarConnection.getJarFile();

            for ( final Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); )
            {
                final JarEntry entry = e.nextElement();
                if ( entry.getName().startsWith( jarConnection.getEntryName() ) )
                {
                    final String filename = StringUtils.removePrefix( entry.getName(), //
                            jarConnection.getEntryName() );

                    final File f = new File( destDir, filename );
                    if ( !entry.isDirectory() )
                    {
                        final InputStream entryInputStream = jarFile.getInputStream( entry );
                        if ( !copyStream( entryInputStream, f ) )
                        {
                            return false;
                        }
                        entryInputStream.close();
                    }
                    else
                    {
                        if ( !ensureDirectoryExists( f ) )
                        {
                            throw new IOException( "Could not create directory: "
                                    + f.getAbsolutePath() );
                        }
                    }
                }
            }
            return true;
        }

        boolean copyResourcesRecursively( final URL originUrl, final File destination )
            throws MojoExecutionException
        {
            try
            {
                final URLConnection urlConnection = originUrl.openConnection();
                if ( urlConnection instanceof JarURLConnection )
                {
                    return copyJarResourcesRecursively( destination, (JarURLConnection) urlConnection );
                }
                else
                {
                    return copyFilesRecusively( new File( originUrl.getPath() ), destination );
                }
            }
            catch ( final IOException e )
            {
                throw new MojoExecutionException( "Installers creation failed: " + e, e );
            }
        }

        boolean copyStream( final InputStream is, final File f )
            throws MojoExecutionException
        {
            try
            {
                return copyStream( is, new FileOutputStream( f ) );
            }
            catch ( final FileNotFoundException e )
            {
                throw new MojoExecutionException( "Installers creation failed: " + e, e );
            }
        }

        boolean copyStream( final InputStream is, final OutputStream os )
            throws MojoExecutionException
        {
            try
            {
                final byte[] buf = new byte[1024];

                int len;
                while ( ( len = is.read( buf ) ) > 0 )
                {
                    os.write( buf, 0, len );
                }
                is.close();
                os.close();
                return true;
            }
            catch ( final IOException e )
            {
                throw new MojoExecutionException( "Installers creation failed: " + e, e );
            }
        }

        boolean ensureDirectoryExists( final File f )
        {
            return f.exists() || f.mkdir();
        }
    }
}
