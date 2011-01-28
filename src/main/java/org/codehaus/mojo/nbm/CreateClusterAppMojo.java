/* ==========================================================================
 * Copyright Milos Kleint
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.StringInputStream;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.InputStreamFacade;
import org.netbeans.nbbuild.MakeListOfNBM;

/**
 * Create the Netbeans module clusters/application for the 'nbm-application' packaging
 * projects
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal cluster-app
 * @phase package
 * @requiresDependencyResolution runtime
 * @requiresProject
 */
public class CreateClusterAppMojo
    extends AbstractNbmMojo
{

    /**
     * output directory where the the netbeans application will be created.
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;

    /**
     * Optional path to custom etc/${brandingToken}.conf file. If not defined,
     * a default template will be used.
     * @parameter expression="${netbeans.conf.file}"
     */
    private File etcConfFile;

    /**
     * Optional path to custom etc/${brandingToken}.clusters file. If not defined,
     * a default one will be generated.
     * @parameter expression="${netbeans.clusters.file}"
     */
    private File etcClustersFile;

    /**
     * Directory which contains the executables that will be copied to
     * the final application's bin/ directory.
     * Please note that the name of the executables shall generally
     * match the brandingToken parameter. Otherwise the application can be wrongly branded.
     * @parameter expression="${netbeans.bin.directory}"
     */
    private File binDirectory;

    /**
     * If the depending NBM file doesn't contain any application cluster information,
     * use this value as default location for such module NBMs.
     * @parameter default-value="extra"
     * @since 3.2
     */
    private String defaultCluster;


    // <editor-fold defaultstate="collapsed" desc="Component parameters">
    /**
     * @component
     * @readonly
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     * @readonly
     */
    private ArtifactResolver artifactResolver;

    /**
     * Local maven repository.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

// end of component params custom code folding
// </editor-fold>

    public void execute() throws MojoExecutionException, MojoFailureException
    {

        File nbmBuildDirFile = new File( outputDirectory, brandingToken );
        if ( !nbmBuildDirFile.exists() )
        {
            nbmBuildDirFile.mkdirs();
        }

        if ( "nbm-application".equals( project.getPackaging() ) )
        {
            Project antProject = registerNbmAntTasks();

            Set<String> knownClusters = new HashSet<String>();
            Set<String> wrappedBundleCNBs = new HashSet<String>();
            Map<Artifact, ExamineManifest> bundles = new HashMap<Artifact, ExamineManifest>();

            @SuppressWarnings( "unchecked" )
            Set<Artifact> artifacts = project.getArtifacts();
            for ( Artifact art : artifacts )
            {
                ArtifactResult res = turnJarToNbmFile( art, artifactFactory, artifactResolver, project, localRepository );
                if (res.hasConvertedArtifact()) {
                    art = res.getConvertedArtifact();
                }

                if ( art.getType().equals( "nbm-file" ) )
                {
                    JarFile jf = null;
                    try
                    {
                        jf = new JarFile( art.getFile() );
                        String clusterName = findCluster( jf );
                        ClusterTuple cluster = processCluster( clusterName, knownClusters, nbmBuildDirFile, art );
                        if ( cluster.newer )
                        {
                            getLog().debug(
                                "Copying " + art.getId() + " to cluster " + clusterName );
                            Enumeration<JarEntry> enu = jf.entries();

                            //we need to trigger this ant task to generate the update_tracking file.
                            MakeListOfNBM makeTask = (MakeListOfNBM) antProject.createTask(
                                    "genlist" );
                            antProject.setNewProperty( "module.name", art.getFile().getName() ); //TODO
                            antProject.setProperty( "cluster.dir", clusterName );
                            FileSet set = makeTask.createFileSet();
                            set.setDir( cluster.location );
                            makeTask.setOutputfiledir( cluster.location );

                            while ( enu.hasMoreElements() )
                            {
                                JarEntry ent = enu.nextElement();
                                String name = ent.getName();
                                if ( name.startsWith( "netbeans/" ) )
                                { //ignore everything else.
                                    String path = clusterName + name.substring( "netbeans".length() );
                                    boolean ispack200 = path.endsWith( ".jar.pack.gz" );
                                    if ( ispack200 )
                                    {
                                        path = path.replace( ".jar.pack.gz", ".jar" );
                                    }
                                    File fl = new File( nbmBuildDirFile,
                                        path.replace( "/", File.separator ) );
                                    if ( ent.isDirectory() )
                                    {
                                        fl.mkdirs();
                                    }
                                    else
                                    {
                                        String part = name.substring( "netbeans/".length() );
                                        if ( ispack200 )
                                        {
                                            part = part.replace( ".jar.pack.gz", ".jar" );
                                        }
                                        set.appendIncludes( new String[] { part });

                                        fl.getParentFile().mkdirs();
                                        fl.createNewFile();
                                        BufferedOutputStream outstream = null;
                                        try
                                        {
                                            outstream = new BufferedOutputStream( new FileOutputStream(
                                                fl ) );
                                            InputStream instream = jf.getInputStream(
                                                ent );
                                            if ( ispack200 )
                                            {
                                                Pack200.Unpacker unp = Pack200.newUnpacker();
                                                JarOutputStream jos = new JarOutputStream( outstream );
                                                GZIPInputStream gzip = new GZIPInputStream( instream );
                                                try {
                                                    unp.unpack( gzip, jos );
                                                } finally {
                                                    jos.close();
                                                }
                                            } else {
                                                IOUtil.copy( instream, outstream );
                                            }
                                        }
                                        finally
                                        {
                                            IOUtil.close( outstream );
                                        }
                                        //now figure which one of the jars is the module jar..
                                        if ( path.endsWith( ".jar" ) &&
                                            !path.contains( "locale/" ) &&
                                            !path.contains( "docs/" ))
                                        {
                                            ExamineManifest ex  = new ExamineManifest( getLog() );
                                            ex.setJarFile( fl );
                                            ex.checkFile();
                                            if ( ex.isNetbeansModule() )
                                            {
                                                makeTask.setModule( part );
                                            }
                                            else if ( ex.isOsgiBundle() )
                                            {
                                                wrappedBundleCNBs.add( ex.getModule() );
                                            }
                                        }
                                    }
                                }
                            }

  
                            try
                            {
                                makeTask.execute();
                            } catch ( BuildException e )
                            {
                                getLog().error( "Cannot Generate update_tracking xml file" );
                                throw new MojoExecutionException( e.getMessage(), e );
                            }


                        }
                    }
                    catch ( IOException ex )
                    {
                        getLog().error( art.getFile().getAbsolutePath(), ex );
                    }
                    finally
                    {
                        try
                        {
                            jf.close();
                        }
                        catch ( IOException ex )
                        {
                            getLog().error( ex );
                        }
                    }
                }
                if (res.isOSGiBundle()) {
                    bundles.put( art, res.getExaminedManifest() );
                }
            }
            for (Map.Entry<Artifact, ExamineManifest> ent : bundles.entrySet()) {
                Artifact art = ent.getKey();
                ExamineManifest ex = ent.getValue();
                
                String spec = ex.getModule();
                if (wrappedBundleCNBs.contains( spec )) 
                {
                    //we already have this one as a wrapped module.
                    getLog().debug( "Not including bundle " + art.getDependencyConflictId() + ". It is already included in a NetBeans module." );
                    continue;
                }
                ClusterTuple cluster = processCluster( defaultCluster, knownClusters, nbmBuildDirFile, art );
                if ( cluster.newer )
                {
                    getLog().debug( "Copying " + art.getId() + " to cluster " + defaultCluster );
                    File modules = new File(cluster.location, "modules");
                    modules.mkdirs();
                    File config = new File(cluster.location, "config");
                    File confModules = new File(config, "Modules");
                    confModules.mkdirs();
                    File updateTracking = new File(cluster.location, "update_tracking");
                    updateTracking.mkdirs();
                    final String cnb = ex.getModule();
                    final String cnbDashed = cnb.replace( ".", "-");
                    final File moduleArt = new File(modules, cnbDashed + ".jar" ); //do we need the file in some canotical name pattern?
                    final String specVer = ex.getSpecVersion();
                    try
                    {
                        FileUtils.copyFile( art.getFile(), moduleArt );
                        final File moduleConf = new File(confModules, cnbDashed + ".xml");
                        FileUtils.copyStreamToFile( new InputStreamFacade() {
                            public InputStream getInputStream() throws IOException
                            {
                                return new StringInputStream( createBundleConfigFile(cnb), "UTF-8");
                            }
                        }, moduleConf);
                        FileUtils.copyStreamToFile( new InputStreamFacade() {
                            public InputStream getInputStream() throws IOException
                            {
                                return new StringInputStream( createBundleUpdateTracking(cnb, moduleArt, moduleConf, specVer), "UTF-8");
                            }
                        }, new File(updateTracking, cnbDashed + ".xml"));
                    }
                    catch ( IOException exc )
                    {
                        getLog().error( exc );
                    }
                }
            }

            getLog().info(
                "Created NetBeans module cluster(s) at " + nbmBuildDirFile.getAbsoluteFile() );

        }
        else
        {
            throw new MojoExecutionException(
                "This goal only makes sense on project with nbm-application packaging" );
        }
        //in 6.1 the rebuilt modules will be cached if the timestamp is not touched.
        File[] files = nbmBuildDirFile.listFiles();
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
                    }
                    catch ( IOException ex )
                    {
                        ex.printStackTrace();
                    }
                }
                stamp.setLastModified( new Date().getTime() );
            }
        }
        try
        {
            createBinEtcDir( nbmBuildDirFile, brandingToken );
        }
        catch ( IOException ex )
        {
            throw new MojoExecutionException(
                "Cannot process etc folder content creation.", ex );
        }
    }
    private final static Pattern patt = Pattern.compile(
        ".*targetcluster=\"([a-zA-Z0-9_\\.\\-]+)\".*", Pattern.DOTALL );

    private String findCluster( JarFile jf ) throws MojoFailureException, IOException
    {
        ZipEntry entry = jf.getEntry( "Info/info.xml" );
        InputStream ins = jf.getInputStream( entry );
        String str = IOUtil.toString( ins, "UTF8" );
        Matcher m = patt.matcher( str );
        if ( !m.matches() )
        {
            getLog().warn( "Cannot find cluster for " + jf.getName() + " Falling back to default value - '" +defaultCluster +  "'.");
            return defaultCluster;
        }
        else
        {
            return m.group( 1 );
        }
    }

    /**
     * 
     * @param buildDir Directory where the platform bundle is built
     * @param harnessDir "harness" directory of the netbeans installation
     * @param enabledClusters The names of all enabled clusters
     * @param defaultOptions Options for the netbeans platform to be placed in config file
     * @param brandingToken 
     * 
     * @throws java.io.IOException
     */
    private void createBinEtcDir( File buildDir, String brandingToken ) throws IOException, MojoExecutionException
    {
        File etcDir = new File( buildDir + File.separator + "etc" );
        etcDir.mkdir();

        // create app.clusters which contains a list of clusters to include in the application

        File clusterConf = new File(
            etcDir + File.separator + brandingToken + ".clusters" );
        String clustersString;
        if ( etcClustersFile != null )
        {
            clustersString = FileUtils.fileRead( etcClustersFile, "UTF-8" );
        }
        else
        {
            clusterConf.createNewFile();
            StringBuffer buffer = new StringBuffer();
            File[] clusters = buildDir.listFiles( new FileFilter()
            {

                public boolean accept( File pathname )
                {
                    return new File( pathname, ".lastModified" ).exists();
                }
            } );
            for ( File cluster : clusters )
            {
                buffer.append( cluster.getName() );
                buffer.append( "\n" );
            }
            clustersString = buffer.toString();
        }

        FileUtils.fileWrite( clusterConf.getAbsolutePath(), clustersString );

        File confFile = etcConfFile;
        String str;
        if ( confFile == null )
        {
            File harnessDir = new File( buildDir, "harness" );
            if ( !harnessDir.exists() )
            {
                getLog().debug( "Using fallback app.conf shipping with the nbm-maven-plugin." );
                InputStream instream = null;
                try
                {
                    instream = getClass().getClassLoader().getResourceAsStream( "harness/etc/app.conf" );
                    str = IOUtil.toString( instream, "UTF-8" );
                }
                finally
                {
                    IOUtil.close( instream );
                }
            }
            else
            {
                // app.conf contains default options and other settings
                confFile = new File(
                    harnessDir.getAbsolutePath() + File.separator + "etc" + File.separator + "app.conf" );
                str = FileUtils.fileRead( confFile, "UTF-8" );
            }
        }
        else
        {
            str = FileUtils.fileRead( confFile, "UTF-8" );
        }
        File confDestFile = new File(
            etcDir.getAbsolutePath() + File.separator + brandingToken + ".conf" );

        str = str.replace( "${branding.token}", brandingToken );
        FileUtils.fileWrite( confDestFile.getAbsolutePath(), "UTF-8", str );

        File destBinDir = new File( buildDir + File.separator + "bin" );
        destBinDir.mkdir();

        File binDir;
        File destExeW = new File( destBinDir, brandingToken + "_w.exe" );
        File destExe = new File( destBinDir, brandingToken + ".exe" );
        File destSh = new File( destBinDir, brandingToken );

        if ( binDirectory != null )
        {
            binDir = binDirectory;
            File[] fls = binDir.listFiles();
            if ( fls == null )
            {
                throw new MojoExecutionException( "Parameter 'binDirectory' has to point to an existing folder." );
            }
            for ( File fl : fls )
            {
                String name = fl.getName();
                File dest = null;
                if ( name.endsWith( "_w.exe" ) ) 
                {
                    dest = destExeW;
                }
                else if ( name.endsWith( ".exe" ) )
                {
                    dest = destExe;
                }
                else if ( !name.contains( "." ) || name.endsWith( ".sh" ) )
                {
                    dest = destSh;
                }
                if ( dest != null  && fl.exists() ) //in 6.7 the _w.exe file is no more.
                {
                    FileUtils.copyFile( fl, dest );
                }
                else
                {
                    //warn about file not being copied
                }
            }
        }
        else
        {
            File harnessDir = new File( buildDir, "harness" );
            if ( harnessDir.exists() )
            {
                binDir = new File(
                    harnessDir.getAbsolutePath() + File.separator + "launchers" );
                File exe = new File( binDir, "app.exe" );
                FileUtils.copyFile( exe, destExe );
                File exew = new File( binDir, "app_w.exe" );
                if ( exew.exists() ) //in 6.7 the _w.exe file is no more.
                {
                    FileUtils.copyFile( exew, destExeW );
                }
                File sh = new File( binDir, "app.sh" );
                FileUtils.copyFile( sh, destSh );
            }
            else
            {
                getLog().debug( "Using fallback executables shipping with the nbm-maven-plugin. (from 6.9main NetBeans Platform)" );
                writeFile( "harness/launchers/app.sh", destSh );
                writeFile( "harness/launchers/app.exe", destExe );
            }
        }

        Project antProject = new Project();
        antProject.init();

        Chmod chmod = (Chmod) antProject.createTask( "chmod" );
        FileSet fs = new FileSet();
        fs.setDir( destBinDir );
        fs.setIncludes( "*" );
        chmod.addFileset( fs );
        chmod.setPerm( "755" );
        chmod.execute();
    }

    private void writeFile( String path, File destSh ) throws IOException
    {
        InputStream instream = null;
        OutputStream output = null;
        try
        {
            instream = getClass().getClassLoader().getResourceAsStream( path );
            destSh.createNewFile();
            output = new BufferedOutputStream( new FileOutputStream( destSh ) );
            IOUtil.copy( instream, output );
        }
        finally
        {
            IOUtil.close( instream );
            IOUtil.close( output );
        }
    }

    private ClusterTuple processCluster( String cluster, Set<String> knownClusters, File nbmBuildDirFile, Artifact art )
    {
        if ( !knownClusters.contains( cluster ) )
        {
            getLog().info(
                    "Processing cluster '" + cluster + "'" );
            knownClusters.add( cluster );
        }
        File clusterFile = new File( nbmBuildDirFile, cluster );
        boolean newer = false;
        if ( !clusterFile.exists() )
        {
            clusterFile.mkdir();
            newer = true;
        }
        else
        {
            File stamp = new File( clusterFile, ".lastModified" );
            if ( stamp.lastModified() < art.getFile().lastModified() )
            {
                newer = true;
            }
        }
        return new ClusterTuple( clusterFile, newer );
    }

    private class ClusterTuple {
        final File location;
        final boolean newer;

        private ClusterTuple( File clusterFile, boolean newer )
        {
            location = clusterFile;
            this.newer = newer;
        }
    }

    static String createBundleConfigFile( String cnb )
    {
        return
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<!DOCTYPE module PUBLIC \"-//NetBeans//DTD Module Status 1.0//EN\"\n" +
"                        \"http://www.netbeans.org/dtds/module-status-1_0.dtd\">\n" +
"<module name=\"" + cnb +"\">\n" +
"    <param name=\"autoload\">true</param>\n" +
"    <param name=\"eager\">false</param>\n" +
//"    <param name=\"enabled\">true</param>\n" +
"    <param name=\"jar\">modules/" + cnb.replace( ".", "-") + ".jar</param>\n" +
"    <param name=\"reloadable\">false</param>\n" +
"</module>\n";
    }

    static String createBundleUpdateTracking( String cnb, File moduleArt, File moduleConf, String specVersion ) throws FileNotFoundException, IOException
    {

        return
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<module codename=\"" + cnb + "\">\n" +
"    <module_version install_time=\"" + System.currentTimeMillis() + "\" last=\"true\" origin=\"installer\" specification_version=\"" + specVersion + "\">\n" +
"        <file crc=\"" + crcForFile(moduleConf).getValue() + "\" name=\"config/Modules/" + cnb.replace( ".", "-") + ".xml\"/>\n" +
"        <file crc=\"" + crcForFile(moduleArt).getValue() + "\" name=\"modules/" + cnb.replace( ".", "-") + ".jar\"/>\n" +
"    </module_version>\n" +
"</module>";

    }

    static CRC32 crcForFile(File inFile) throws FileNotFoundException, IOException {
        FileInputStream inFileStream = null;
        CRC32 crc = new CRC32();
        try {
        inFileStream = new FileInputStream(inFile);
        byte[] array = new byte[(int) inFile.length()];
        int len = inFileStream.read(array);
        if (len != array.length) {
            throw new IOException("Cannot fully read " + inFile);
        }
        crc.update(array);
        } finally {
            inFileStream.close();
        }

        return crc;
    }

}
