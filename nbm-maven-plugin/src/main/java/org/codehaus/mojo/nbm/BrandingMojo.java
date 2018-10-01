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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
 *
 *
 */
@Mojo(name="branding",
        requiresProject=true,
        threadSafe = true,
        defaultPhase= LifecyclePhase.PACKAGE)
public class BrandingMojo
        extends AbstractNbmMojo
{

    /**
     * directory where the the binary content is created.
     */
    @Parameter(required=true, defaultValue="${project.build.directory}/nbm")
    protected File nbmBuildDir;
    
    /**
    * output directory.
    */
    @Parameter(defaultValue="${project.build.directory}", required=true)
    protected File outputDirectory;
    
    /**
     * Location of the branded resources.
     */
    @Parameter(required=true, defaultValue="${basedir}/src/main/nbm-branding")
    private File brandingSources;
    /**
     * The branding token used by the application.
     * Required unless {@code nbmBuildDir} does not exist and the mojo is thus skipped.
     */
    @Parameter(property="netbeans.branding.token")
    private String brandingToken;
    /**
     * cluster of the branding.
     */
    @Parameter(required=true, defaultValue="extra")
    protected String cluster;
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    @Parameter(required=true, readonly=true, property="project")
    private MavenProject project;

    public void execute()
        throws MojoExecutionException
    {
        if ( !"nbm".equals( project.getPackaging() ) ) 
        {
            getLog().error( "The nbm:branding goal shall be used within a NetBeans module project only (packaging 'nbm')" );
        }
        if ( !brandingSources.isDirectory() )
        {
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

            final String clusterPathPart = "netbeans" + File.separator + cluster;
            File outputDir = new File(outputDirectory, "branding_and_locales");
            outputDir.mkdirs();
            File clusterDir = new File( nbmBuildDir, clusterPathPart );
            clusterDir.mkdirs();

            // copy all files and see to it that they get the correct names
            for ( String brandingFilePath : scanner.getIncludedFiles() )
            {
                File brandingFile = new File( brandingSources, brandingFilePath );
                String[] locale = getLocale( brandingFile.getName());
                String token = locale[1] == null ? brandingToken : brandingToken + "_" + locale[1];
                File root = new File(outputDir, token);
                root.mkdirs();
                String destinationName = locale[0] + "_" + token + locale[2];
                File brandingDestination = new File( root, brandingFilePath.replace( brandingFile.getName(), destinationName) );
                if ( !brandingDestination.getParentFile().exists() )
                {
                    brandingDestination.getParentFile().mkdirs();
                }
                FileUtils.copyFile( brandingFile, brandingDestination );
            }
            for (File rootDir : outputDir.listFiles()) {
                if (!rootDir.isDirectory()) {
                    continue;
                }
                String effectiveBranding = rootDir.getName();
                // create jar-files from each toplevel .jar directory
                scanner.setIncludes( new String[]
                    {
                        "**/*.jar"
                    } );
                scanner.setBasedir( rootDir );
                scanner.scan();
                for ( String jarDirectoryPath : scanner.getIncludedDirectories() )
                {
                    // move nnn.jar directory to nnn.jar.tmp
                    File jarDirectory = new File( rootDir, jarDirectoryPath );
                    File destinationLocation = new File(clusterDir, jarDirectoryPath).getParentFile();
                    destinationLocation.mkdirs();
                    // jars should be placed in locales/ under the same directory the jar-directories are
                    File destinationJar =
                        new File( destinationLocation + File.separator + "locale"
                            + File.separator + destinationFileName( jarDirectory.getName(), effectiveBranding ) );

                    // create nnn.jar archive of contents
                    JarArchiver archiver = new JarArchiver();
                    archiver.setDestFile( destinationJar );
                    archiver.addDirectory( jarDirectory );
                    archiver.createArchive();
                }
            }

        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "Error creating branding", ex );
        }
    }

    static  String destinationFileName( String brandingFilePath, String branding )
    {
        // use first underscore in filename 
        int lastSeparator = brandingFilePath.lastIndexOf( File.separator );
        String infix = "_" + branding;

        // no underscores, use dot
        int lastDot = brandingFilePath.lastIndexOf( "." );
        if (lastDot == -1 || lastDot < lastSeparator) {
            return brandingFilePath + infix;
        }
        return brandingFilePath.substring( 0, lastDot ) + infix + brandingFilePath.substring( lastDot );
    }
    
    //[0] prefix
    //[1] locale
    //[2] suffix
    static String[] getLocale(String name) {
        String suffix = "";
        int dot = name.indexOf( ".");
        if (dot > -1) { //remove file extension
            suffix = name.substring( dot );
            name = name.substring( 0, dot);
        }
        String locale = null;
        int count = 1;
        //iterate from back of the string, max 3 times and see if the pattern patches local pattern
        while (count <= 3) {
            int underscore = name.lastIndexOf( '_');
            if (underscore > -1) {
                String loc1 = name.substring( underscore  + 1);
                if (loc1.length() != 2) {
                    break;
                } 
                locale = loc1 + (locale == null ? "" : "_" + locale);
                name = name.substring( 0, underscore);
            } else {
                break;
            }
            count = count + 1;
        }
        return new String[] {name, locale, suffix};
    }
}
