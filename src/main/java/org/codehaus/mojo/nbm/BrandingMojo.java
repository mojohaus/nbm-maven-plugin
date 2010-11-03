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
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

/**
 * Package branding resources for NetBeans platform/IDE based application.
 * The format of branding resources is the same as in
 * NetBeans Ant-based projects.
 * 
 * The <code>src/main/nbm-branding</code> folder of the project is assumed to 
 * contain the branding content. Within the directory, the following folder structure is assumed:
 * <ul>
 * <li>
 * 1. pick the IDE/platform module which contents you want to brand. eg. org-openide-windows.jar
 * </li><li>
 * 2. locate the jar within the IDE/platform installation and it's cluster, eg. modules/org-openide-windows.jar 
 * </li><li>
 * 3. create the same folder structure in src/main/nbm-branding, make folder with the module's jar name as well.
 * eg. create folder by name modules/org-openide-windows.jar
 * </li><li>
 * 4. within that folder place your branding modifications at the same location, as if they were withn the jar,
 * eg. org/openide/windows/ui/Bundle.properties and place the changed bundle keys there.
 * </li></ul>
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal branding
 * @phase package
 * @requiresProject
 *
 */
public class BrandingMojo
        extends AbstractNbmMojo
{

    /**
     * directory where the the binary content is created.
     * @parameter expression="${project.build.directory}/nbm"
     * @required
     */
    protected File nbmBuildDir;
    /**
     * Location of the branded resources.
     * @parameter expression="${basedir}/src/main/nbm-branding"
     * @required
     */
    private File brandingSources;
    /**
     * The branding token used by the application.
     * Required unless {@code nbmBuildDir} does not exist and the mojo is thus skipped.
     * @parameter expression="${netbeans.branding.token}"
     */
    private String brandingToken;
    /**
     * cluster of the branding.
     *
     * @parameter default-value="extra"
     * @required
     */
    protected String cluster;
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    public void execute() throws MojoExecutionException
    {
        if ( !"nbm".equals( project.getPackaging() ) ) 
        {
            getLog().error( "The nbm:branding goal shall be used within a NetBeans module project only (packaging 'nbm')");
        }
        if ( ! brandingSources.isDirectory() ) {
            getLog().info( "No branding to process." );
            return;
        }
        if ( brandingToken == null )
        {
            throw new MojoExecutionException( "brandingToken must be defined for mojo:branding" );
        }
        try
        {

            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setIncludes( new String[]
                    {
                        "**/*.*"
                    } );
            scanner.addDefaultExcludes();
            scanner.setBasedir( brandingSources );
            scanner.scan();

            File clusterDir = new File( nbmBuildDir,
                    "netbeans" + File.separator + cluster );
            clusterDir.mkdirs();

            // copy all files and see to it that they get the correct names
            for ( String brandingFilePath : scanner.getIncludedFiles() )
            {
                File brandingFile = new File( brandingSources, brandingFilePath );
                String destinationFilePath = destinationFileName(
                        brandingFilePath );
                File brandingDestination = new File( clusterDir,
                        destinationFilePath );
                if ( !brandingDestination.getParentFile().exists() )
                {
                    brandingDestination.getParentFile().mkdirs();
                }
                FileUtils.copyFile( brandingFile, brandingDestination );
            }

            // create jar-files from each toplevel .jar directory
            scanner.setIncludes( new String[]
                    {
                        "*/*.jar",
                        "*/*/*.jar" //MNBMODULE-52 also use the 2nd level *.jar directories
                                // could also use **/*.jar but not sure how to figure the
                                // fine line between the module jar's folder and it's content
                                // in the unlikely event that a module jar contains a folder named *.jar, we might get wrong data..
                    } );
            scanner.setBasedir( clusterDir );
            scanner.scan();

            for ( String jarDirectoryPath : scanner.getIncludedDirectories() )
            {

                // move nnn.jar directory to nnn.jar.tmp
                File jarDirectory = new File( clusterDir, jarDirectoryPath );

                // jars should be placed in locales/ under the same directory the jar-directories are
                File destinationJar =
                        new File( jarDirectory.getParentFile().getAbsolutePath() +
                        File.separator + "locale" +
                        File.separator + destinationFileName(
                        jarDirectory.getName() ) );

                // create nnn.jar archive of contents
                JarArchiver archiver = new JarArchiver();
                archiver.setDestFile( destinationJar );
                archiver.addDirectory( jarDirectory );
                archiver.createArchive();

                FileUtils.deleteDirectory( jarDirectory );
            }

        } catch ( Exception ex )
        {
            throw new MojoExecutionException( "Error creating branding", ex );
        }
    }

    private String destinationFileName( String brandingFilePath )
    {
        // use first underscore in filename 
        int lastSeparator = brandingFilePath.indexOf( File.separator );
        int firstUnderscore = brandingFilePath.indexOf( "_", lastSeparator );

        if ( firstUnderscore != -1 )
        {
            return brandingFilePath.substring( 0, firstUnderscore ) + "_" +
                    brandingToken + "_" +
                    brandingFilePath.substring( firstUnderscore + 1 );
        }

        // no underscores, use dot
        int lastDot = brandingFilePath.lastIndexOf( "." );
        return brandingFilePath.substring( 0, lastDot ) +
                "_" + brandingToken + brandingFilePath.substring( lastDot );
    }
}
