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

import com.google.common.collect.Sets;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import java.util.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.StringInputStream;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.mojo.nbm.utils.ExamineManifest;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.io.InputStreamFacade;
import org.netbeans.nbbuild.MakeListOfNBM;

/**
 * Create the NetBeans module clusters/application for the 'nbm-application' packaging
 * projects
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 */
@Mojo(name="cluster-app", 
        defaultPhase= LifecyclePhase.PACKAGE, 
        requiresProject=true, 
        requiresDependencyResolution= ResolutionScope.RUNTIME )
public class CreateClusterAppMojo
    extends AbstractNbmMojo
{

    /**
     * output directory where the the NetBeans application will be created.
     */
    @Parameter(defaultValue="${project.build.directory}", required=true)
    private File outputDirectory;

    /**
     * The Maven Project.
     */
    @Parameter(required=true, readonly=true, property="project")
    private MavenProject project;

    /**
     * The branding token for the application based on NetBeans platform.
     */
    @Parameter(property="netbeans.branding.token", required=true)
    protected String brandingToken;

    /**
     * Optional path to custom etc/${brandingToken}.conf file. If not defined,
     * a default template will be used.
     */
    @Parameter( property="netbeans.conf.file")
    private File etcConfFile;

    /**
     * Optional path to custom etc/${brandingToken}.clusters file. If not defined,
     * a default one will be generated.
     */
    @Parameter(property="netbeans.clusters.file")
    private File etcClustersFile;

    /**
     * Directory which contains the executables that will be copied to
     * the final application's bin/ directory.
     * Please note that the name of the executables shall generally
     * match the brandingToken parameter. Otherwise the application can be wrongly branded.
     */
    @Parameter(property="netbeans.bin.directory")
    private File binDirectory;

    /**
     * If the depending NBM file doesn't contain any application cluster information,
     * use this value as default location for such module NBMs.
     * @since 3.2
     */
    @Parameter(defaultValue="extra")
    private String defaultCluster;
    
    /**
     * attempts to verify the integrity of module artifacts making sure that all dependencies are included
     * and that all required tokens are provided
     * @since 3.10
     */
    @Parameter(defaultValue = "true", property = "netbeans.verify.integrity")
    private boolean verifyIntegrity;
    
    private final Collection<String> defaultPlatformTokens = Arrays.asList( new String[] {
                    "org.openide.modules.os.Windows",
                    "org.openide.modules.os.Unix",
                    "org.openide.modules.os.MacOSX",
                    "org.openide.modules.os.OS2",
                    "org.openide.modules.os.PlainUnix",    
                    "org.openide.modules.os.Linux",
                    "org.openide.modules.os.Solaris",
                    "org.openide.modules.ModuleFormat1",
                    "org.openide.modules.ModuleFormat2"
    });


    // <editor-fold defaultstate="collapsed" desc="Component parameters">
    
    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver artifactResolver;

    /**
     * Local maven repository.
     *
     */
    @Parameter(required=true, readonly=true, property="localRepository")
    protected ArtifactRepository localRepository;

// end of component params custom code folding
// </editor-fold>

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

        File nbmBuildDirFile = new File( outputDirectory, brandingToken );
        if ( !nbmBuildDirFile.exists() )
        {
            nbmBuildDirFile.mkdirs();
        }

        if ( "nbm-application".equals( project.getPackaging() ) )
        {
            Project antProject = registerNbmAntTasks();

            Set<String> knownClusters = new HashSet<String>(20);
            Set<String> wrappedBundleCNBs = new HashSet<String>(100);
            Map<String, Set<String>> clusterDependencies = new HashMap<String, Set<String>>();
            Map<String, Set<String>> clusterModules = new HashMap<String, Set<String>>();
            
            //verify integrity
            Set<String> modulesCNBs = new HashSet<String>(200);
            Set<String> dependencyCNBs = new HashSet<String>(200);
            Map<String, Set<String>> dependencyCNBBacktraces = new HashMap<String, Set<String>>(50);
            Set<String> requireTokens = new HashSet<String>(50);
            Map<String, Set<String>> requireTokensBacktraces = new HashMap<String, Set<String>>(50);
            Set<String> provideTokens = new HashSet<String>(50);
            Set<String> osgiImports = new HashSet<String>(50);
            Map<String, Set<String>> osgiImportsBacktraces = new HashMap<String, Set<String>>(50);
            Set<String> osgiExports = new HashSet<String>(50);
            Set<String> osgiExportsSubs = new HashSet<String>(50); //a way to deal with nb module declaring xxx.** (subpackages) declaration that is consumed by osgi imports
            
            List<BundleTuple> bundles = new ArrayList<BundleTuple>();

            @SuppressWarnings( "unchecked" )
            Set<Artifact> artifacts = project.getArtifacts();
            for ( Artifact art : artifacts )
            {
                ArtifactResult res = turnJarToNbmFile( art, artifactFactory, artifactResolver, project, localRepository );
                if ( res.hasConvertedArtifact() )
                {
                    art = res.getConvertedArtifact();
                }

                if ( art.getType().equals( "nbm-file" ) )
                {
                    try
                    {
                        JarFile jf = new JarFile( art.getFile() );
                        try
                        {
                            String clusterName = findCluster( jf );                            
                            ClusterTuple cluster = processCluster( clusterName, knownClusters, nbmBuildDirFile, art );
                            
                            if ( cluster.newer )
                            {
                                getLog().debug( "Copying " + art.getId() + " to cluster " + clusterName );
                                Enumeration<JarEntry> enu = jf.entries();

                                // we need to trigger this ant task to generate the update_tracking file.
                                MakeListOfNBM makeTask = (MakeListOfNBM) antProject.createTask( "genlist" );
                                antProject.setNewProperty( "module.name", art.getFile().getName() ); // TODO
                                antProject.setProperty( "cluster.dir", clusterName );
                                FileSet set = makeTask.createFileSet();
                                set.setDir( cluster.location );
                                makeTask.setOutputfiledir( cluster.location );
                                String[] executables = null;
                                while ( enu.hasMoreElements() )
                                {
                                    JarEntry ent = enu.nextElement();
                                    String name = ent.getName();
                                    //MNBMODULE-176
                                    if ( name.equals("Info/executables.list")) {
                                        InputStream is = jf.getInputStream( ent );
                                        executables = StringUtils.split( IOUtil.toString( is, "UTF-8" ), "\n");
                                    }
                                    else if ( name.startsWith( "netbeans/" ) )
                                    { // ignore everything else.
                                        String path = clusterName + name.substring( "netbeans".length() );
                                        boolean ispack200 = path.endsWith( ".jar.pack.gz" );
                                        if ( ispack200 )
                                        {
                                            path = path.replace( ".jar.pack.gz", ".jar" );
                                        }
                                        File fl = new File( nbmBuildDirFile, path.replace( "/", File.separator ) );
                                        if ( ent.isDirectory() )
                                        {
                                            fl.mkdirs();
                                        }
                                        else if ( path.endsWith( ".external" ) ) // MNBMODULE-138
                                        {
                                            InputStream is = jf.getInputStream( ent );
                                            try
                                            {
                                                externalDownload( new File( fl.getParentFile(),
                                                                            fl.getName().replaceFirst( "[.]external$",
                                                                                                       "" ) ), is );
                                            }
                                            finally
                                            {
                                                is.close();
                                            }
                                            //MNBMODULE-192
                                            set.appendIncludes( new String[] { name.substring( "netbeans/".length(), name.length() - ".external".length() ) } );
                                        }
                                        else
                                        {
                                            String part = name.substring( "netbeans/".length() );
                                            if ( ispack200 )
                                            {
                                                part = part.replace( ".jar.pack.gz", ".jar" );
                                            }
                                            set.appendIncludes( new String[] { part } );

                                            fl.getParentFile().mkdirs();
                                            fl.createNewFile();
                                            BufferedOutputStream outstream = null;
                                            try
                                            {
                                                outstream = new BufferedOutputStream( new FileOutputStream( fl ) );
                                                InputStream instream = jf.getInputStream( ent );
                                                if ( ispack200 )
                                                {
                                                    Pack200.Unpacker unp = Pack200.newUnpacker();
                                                    JarOutputStream jos = new JarOutputStream( outstream );
                                                    GZIPInputStream gzip = new GZIPInputStream( instream );
                                                    try
                                                    {
                                                        unp.unpack( gzip, jos );
                                                    }
                                                    finally
                                                    {
                                                        jos.close();
                                                    }
                                                }
                                                else
                                                {
                                                    IOUtil.copy( instream, outstream );
                                                }
                                            }
                                            finally
                                            {
                                                IOUtil.close( outstream );
                                            }
                                            
                                            //TODO examine netbeans/config/Modules to see if the module is autoload/eager
                                            // in verifyIntegrity these could be handled more gracefully than regular modules.
                                            //eager is simpler, does not need to have module dependencies satisfied.
                                            //autoload needs checking if any of the other modules declares a dependency on it. if not, also safe to ignore?
                                            
                                            
                                            // now figure which one of the jars is the module jar..
                                            if ( part.matches("(modules|core|lib)/[^/]+[.]jar") )
                                            {
                                                ExamineManifest ex = new ExamineManifest( getLog() );
                                                ex.setJarFile( fl );
                                                ex.setPopulateDependencies( true );
                                                ex.checkFile();
                                                if ( ex.isNetBeansModule() )
                                                {
                                                    makeTask.setModule( part );
                                                    addToMap(clusterDependencies, clusterName, ex.getDependencyTokens());
                                                    addToMap(clusterModules, clusterName, Collections.singletonList( ex.getModule() ));
                                                    
                                                }
                                                else if ( ex.isOsgiBundle() )
                                                {
                                                    wrappedBundleCNBs.add( ex.getModule() );
                                                }
                                                if (verifyIntegrity) {
                                                    dependencyCNBs.addAll(ex.getDependencyTokens());
                                                    modulesCNBs.add(ex.getModule());
                                                    for (String d : ex.getDependencyTokens()) {
                                                        addToMap(dependencyCNBBacktraces, d, Collections.singletonList( ex.getModule() ));
                                                    }
                                                    if (ex.isOsgiBundle()) {
                                                        osgiImports.addAll( ex.getOsgiImports());
                                                        for (String d : ex.getOsgiImports()) {
                                                            addToMap(osgiImportsBacktraces, d, Collections.singletonList( ex.getModule() ));
                                                        }
                                                        osgiExports.addAll( ex.getOsgiExports());
                                                    }
                                                    if (ex.isNetBeansModule()) {
                                                        requireTokens.addAll(ex.getNetBeansRequiresTokens());
                                                        for (String r : ex.getNetBeansRequiresTokens()) {
                                                            addToMap( requireTokensBacktraces, r, Collections.singletonList( ex.getModule()));
                                                        }
                                                        provideTokens.addAll(ex.getNetBeansProvidesTokens());
                                                        for (String pack : ex.getPackages()) {
                                                            if (pack.endsWith( ".**")) {
                                                                //what to do with subpackages?
                                                                pack = pack.substring( 0, pack.length() - ".**".length());
                                                                osgiExportsSubs.add( pack );
                                                            } else if (pack.endsWith( ".*")) {
                                                                pack = pack.substring( 0, pack.length() - ".*".length());
                                                                osgiExports.add(pack);                                                            
                                                            }
                                                        }
                                                        
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                try
                                {
                                    makeTask.execute();
                                }
                                catch ( BuildException e )
                                {
                                    getLog().error( "Cannot Generate update_tracking XML file from " + art.getFile() );
                                    throw new MojoExecutionException( e.getMessage(), e );
                                }
                                
                                if ( executables != null ) 
                                {
                                    //MNBMODULE-176
                                    for ( String exec : executables )
                                    {
                                        exec = exec.replace( "/", File.separator);
                                        File execFile = new File(cluster.location, exec);
                                        if (execFile.exists()) {
                                            execFile.setExecutable( true, false);
                                        }
                                    }
                                }
                            }
                        }
                        finally
                        {
                            jf.close();
                        }
                    }
                    catch ( IOException ex )
                    {
                        getLog().error( art.getFile().getAbsolutePath(), ex );
                    }
                }
                if ( res.isOSGiBundle() )
                {
                    ExamineManifest ex = res.getExaminedManifest();
                    bundles.add( new BundleTuple( art,  ex) );
                    if (verifyIntegrity) {
                        dependencyCNBs.addAll(ex.getDependencyTokens());
                        for ( String d : ex.getDependencyTokens() )
                        {
                            addToMap( dependencyCNBBacktraces, d, Collections.singletonList( ex.getModule() ) );
                        }
                        modulesCNBs.add(ex.getModule());
                        osgiImports.addAll( ex.getOsgiImports());
                        for ( String d : ex.getOsgiImports() )
                        {
                            addToMap( osgiImportsBacktraces, d, Collections.singletonList( ex.getModule() ) );
                        }
                        
                        osgiExports.addAll( ex.getOsgiExports());
                    }
                }
            }
            
            if (verifyIntegrity) {
                dependencyCNBs.removeAll( modulesCNBs );
                if (modulesCNBs.contains( "org.netbeans.modules.netbinox")) {
                    dependencyCNBs.remove( "org.eclipse.osgi"); //this is special.
                }
                osgiImports.removeAll( osgiExports );
                Iterator<String> it = osgiImports.iterator();
                while (it.hasNext()) {
                    String s = it.next();
                    if (s.startsWith( "java.") || s.startsWith( "javax.") || s.startsWith( "sun.") || s.startsWith( "org.xml.sax") || s.startsWith( "org.w3c.dom") || s.startsWith( "org.ietf.jgss")) {
                        it.remove();
                        continue;
                    }
                    for (String sub : osgiExportsSubs) {
                        if (s.startsWith( sub )) {
                            it.remove();
                            break;
                        }
                    }
                }
                requireTokens.removeAll( provideTokens );
                requireTokens.removeAll( defaultPlatformTokens );
                if (!dependencyCNBs.isEmpty() || !osgiImports.isEmpty() ||!requireTokens.isEmpty()) {
                    if (!dependencyCNBs.isEmpty()) {
                        getLog().error( "Some included modules/bundles depend on these codenamebases but they are not included. The application will fail starting up. The missing codenamebases are:" );
                        for (String s : dependencyCNBs) {
                            Set<String> back = dependencyCNBBacktraces.get( s );
                            getLog().error("   " + s + (back != null ? "          ref: " + Arrays.toString( back.toArray()) : ""));
                        }
                    }
                    if (!osgiImports.isEmpty()) {
                        getLog().error("Some OSGi imports are not satisfied by included bundles' exports. The application will fail starting up. The missing imports are:");
                        for (String s : osgiImports) {
                            Set<String> back = osgiImportsBacktraces.get( s );
                            getLog().error("   " + s + (back != null ? "          ref: " + Arrays.toString( back.toArray()) : ""));
                        }
                    }
                     if (!requireTokens.isEmpty()) {
                        getLog().error("Some tokens required by included modules are not provided by included modules. The application will fail starting up. The missing tokens are:");
                        for (String s : requireTokens) {
                            Set<String> back = requireTokensBacktraces.get( s );
                            getLog().error("   " + s + (back != null ? "          ref: " + Arrays.toString( back.toArray()) : ""));
                        }
                    }
                    throw new MojoFailureException("See above for consistency validation check failures. Either fix those by adding the relevant dependencies to the application or disable the check by setting the verifyIntegrity parameter to false or by running with -Dnetbeans.verify.integrity=false cmd line parameter.");
                }
            }
            
            //attempt to sort clusters based on the dependencies and cluster content.
            Map<String, Set<String>> cluster2depClusters = computeClusterOrdering( clusterDependencies, clusterModules );
            clusterModules.clear();
        
            //now assign the cluster to bundles based on dependencies..
            assignClustersToBundles( bundles, wrappedBundleCNBs, clusterDependencies, cluster2depClusters, getLog() );
            
            
            for (BundleTuple ent : bundles) {
                Artifact art = ent.artifact;
                final ExamineManifest ex = ent.manifest;
                
                String clstr = ent.cluster;
                if (clstr == null) {
                    clstr = defaultCluster;
                }
                
                ClusterTuple cluster = processCluster( clstr, knownClusters, nbmBuildDirFile, art );
                if ( cluster.newer )
                {
                    getLog().info( "Copying " + art.getId() + " to cluster " + clstr );
                    File modules = new File( cluster.location, "modules" );
                    modules.mkdirs();
                    File config = new File( cluster.location, "config" );
                    File confModules = new File( config, "Modules" );
                    confModules.mkdirs();
                    File updateTracking = new File( cluster.location, "update_tracking" );
                    updateTracking.mkdirs();
                    final String cnb = ex.getModule();
                    final String cnbDashed = cnb.replace( ".", "-" );
                    final File moduleArt = new File( modules, cnbDashed + ".jar" ); //do we need the file in some canotical name pattern?
                    final String specVer = ex.getSpecVersion();
                    try
                    {
                        FileUtils.copyFile( art.getFile(), moduleArt );
                        final File moduleConf = new File( confModules, cnbDashed + ".xml" );
                        FileUtils.copyStreamToFile( new InputStreamFacade() {
                            @Override
                            public InputStream getInputStream() throws IOException
                            {
                                return new StringInputStream( createBundleConfigFile( cnb, ex.isBundleAutoload() ), "UTF-8" );
                            }
                        }, moduleConf );
                        FileUtils.copyStreamToFile( new InputStreamFacade() {
                            @Override
                            public InputStream getInputStream() throws IOException
                            {
                                return new StringInputStream( createBundleUpdateTracking( cnb, moduleArt, moduleConf, specVer ), "UTF-8" );
                            }
                        }, new File( updateTracking, cnbDashed + ".xml" ) );
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

    private String findCluster( JarFile jf )
        throws MojoFailureException, IOException
    {
        ZipEntry entry = jf.getEntry( "Info/info.xml" );
        InputStream ins = jf.getInputStream( entry );
        String str = IOUtil.toString( ins, "UTF8" );
        Matcher m = patt.matcher( str );
        if ( !m.matches() )
        {
            getLog().info( "Cannot find cluster for " + jf.getName() + " Falling back to default value - '"
                               + defaultCluster + "'." );
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
     * @param brandingToken
     * 
     * @throws java.io.IOException
     */
    private void createBinEtcDir( File buildDir, String brandingToken )
        throws IOException, MojoExecutionException
    {
        File etcDir = new File( buildDir + File.separator + "etc" );
        etcDir.mkdir();

        // create app.clusters which contains a list of clusters to include in the application

        File clusterConf = new File( etcDir + File.separator + brandingToken + ".clusters" );
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

                @Override
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
        File destExe64 = new File( destBinDir, brandingToken + "64.exe" );
        File destSh = new File( destBinDir, brandingToken );

        if ( binDirectory != null )
        {
            //we have custom launchers.
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
                else if ( name.endsWith( "64.exe" ) )
                {
                    dest = destExe64;
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
                //we have org-netbeans-modules-apisupport-harness in target area, just use it's own launchers.
                binDir = new File(
                    harnessDir.getAbsolutePath() + File.separator + "launchers" );
                File exe = new File( binDir, "app.exe" );
                FileUtils.copyFile( exe, destExe );
                File exe64 = new File( binDir, "app64.exe" );
                if ( exe64.isFile() )
                {
                    FileUtils.copyFile( exe64, destExe64 );
                }
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
                File nbm = getHarnessNbm();
                ZipFile zip = new ZipFile( nbm );
                try {
                    getLog().debug( "Using fallback executables from downloaded org-netbeans-modules-apisupport-harness nbm file." );
                    writeFromZip(zip, "netbeans/launchers/app.sh",  destSh, true );
                    writeFromZip(zip, "netbeans/launchers/app.exe",  destExe, true );
                    writeFromZip(zip, "netbeans/launchers/app64.exe",  destExe64, false );
                    writeFromZip(zip, "netbeans/launchers/app_w.exe",  destExeW, false );
                } finally {
                    zip.close();
                }
            }
        }

        Project antProject = antProject();

        Chmod chmod = (Chmod) antProject.createTask( "chmod" );
        FileSet fs = new FileSet();
        fs.setDir( destBinDir );
        fs.setIncludes( "*" );
        chmod.addFileset( fs );
        chmod.setPerm( "755" );
        chmod.execute();
    }

    private void writeFile( String path, File destSh )
        throws IOException
    {
        InputStream instream = null;
        OutputStream output = null;
        try
        {
            instream = getClass().getClassLoader().getResourceAsStream( path );
            if ( instream == null )
            {
                throw new FileNotFoundException( path );
            }
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
            getLog().info( "Processing cluster '" + cluster + "'" );
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

    private void externalDownload( File f, InputStream is )
        throws IOException
    {
        // Cf. org.netbeans.nbbuild.AutoUpdate
        BufferedReader r = new BufferedReader( new InputStreamReader( is, "UTF-8" ) );
        long crc = -1;
        long size = -1;
        boolean found = false;
        String line;
        while ( ( line = r.readLine() ) != null )
        {
            if ( line.startsWith( "CRC:" ) )
            {
                crc = Long.parseLong( line.substring( 4 ).trim() );
            }
            else if ( line.startsWith( "URL:m2:/" ) )
            {
                if ( ! found )
                {
                    String[] coords = line.substring( 8 ).trim().split( ":" );
                    Artifact artifact;
                    if ( coords.length == 4 )
                    {
                        artifact = artifactFactory.createArtifact( coords[0], coords[1], coords[2], null, coords[3] );
                    }
                    else
                    {
                        artifact = artifactFactory.createArtifactWithClassifier( coords[0], coords[1], coords[2], coords[3], coords[4] );
                    }
                    try
                    {
                        artifactResolver.resolve( artifact, project.getRemoteArtifactRepositories(), localRepository );
                        FileUtils.copyFile( artifact.getFile(), f );
                        found = true;
                    }
                    catch ( AbstractArtifactResolutionException x )
                    {
                        getLog().warn( "Cannot find " + line.substring( 8 ), x );
                    }
                }
            }
            else if ( line.startsWith( "URL:" ) )
            {
                if ( ! found )
                {
                    String url = line.substring( 4 ).trim();
                    try
                    {
                        // XXX use Wagon API instead
                        FileUtils.copyURLToFile( new URL( url ), f );
                        found = true;
                    }
                    catch ( IOException x )
                    {
                        getLog().warn( "Cannot download " + url, x );
                    }
                }
            }
            else if ( line.startsWith( "SIZE:" ) )
            {
                size = Long.parseLong( line.substring( 5 ).trim() );
            }
            else
            {
                getLog().warn( "Unrecognized line: " + line );
            }
        }
        if ( ! found )
        {
            throw new IOException( "Could not download " + f );
        }
        if ( crc != -1 && crc != crcForFile( f ).getValue() )
        {
            throw new IOException( "CRC-32 of " + f + " does not match declared " + crc );
        }
        if ( size != -1 && size != f.length() )
        {
            throw new IOException( "Size of " + f + " does not match declared " + size );
        }
    }

    private File getHarnessNbm() throws MojoExecutionException
    {
        @SuppressWarnings( "unchecked" )
        Set<Artifact> artifacts = project.getArtifacts();
        String version = null;
        for (Artifact a : artifacts) {
            if ("org.netbeans.modules".equals(a.getGroupId()) && "org-netbeans-bootstrap".equals(a.getArtifactId())) {
                version = a.getBaseVersion(); //base version in non-snapshot should equals version, in snapshots to X-SNAPSHOT, not timestamp
                break;
            }
        }
        if (version == null) {
            throw new MojoExecutionException( "We could not find org-netbeans-bootstrap among the modules in the application. Launchers could not be found.");
        }
        Artifact nbmArt = artifactFactory.createArtifact(
            "org.netbeans.modules",
            "org-netbeans-modules-apisupport-harness",
            version,
            "compile",
            "nbm-file");
        try
        {
            artifactResolver.resolve( nbmArt, project.getRemoteArtifactRepositories(), localRepository );
        }

        catch ( ArtifactResolutionException ex )
        {
            throw new MojoExecutionException( "Failed to retrieve the nbm file from repository", ex );
        }
        catch ( ArtifactNotFoundException ex )
        {
            throw new MojoExecutionException( "Failed to retrieve the nbm file from repository", ex );
        }
        return nbmArt.getFile();
    }

    private void writeFromZip( final ZipFile zip, String zipPath, File destFile, boolean mandatory ) throws MojoExecutionException, IOException
    {
        final ZipEntry path = zip.getEntry( zipPath );
        if (path == null) {
            if (mandatory) {
                throw new MojoExecutionException( zipPath + " not found in " + zip.getName());
            }
            getLog().debug(zipPath + " is not present in " + zip.getName());
            return;
        }
        FileUtils.copyStreamToFile( new InputStreamFacade() {
            
            @Override
            public InputStream getInputStream() throws IOException
            {
                return zip.getInputStream( path );
            }
        }, destFile);
    }

    private static void addToMap( Map<String, Set<String>> map, String clusterName, List<String> newValues )
    {
        Set<String> lst = map.get( clusterName );
        if ( lst == null )
        {
            lst = new HashSet<String>();
            map.put( clusterName, lst );
        }
        if ( newValues != null )
        {
            lst.addAll( newValues );
        }
    }
    
    private static List<String> findByDependencies( Map<String, Set<String>> clusterDependencies, String spec)
    {
        List<String> toRet = new ArrayList<String>();
        for ( Map.Entry<String, Set<String>> entry : clusterDependencies.entrySet() )
        {
            if ( entry.getValue().contains( spec ) )
            {
                toRet.add(entry.getKey());
            }
        }
        return toRet;
    }

    //the basic idea is that bundle's cluster can be determined by who depends on it.
    //simplest case is when a module depends on it. If there are more, we need to pick one that is "lower in the stack, that's what cluster2depClusters is for.
    //the rest needs to be determined in more sofisticated manner.
    //start from bundles with known cluster and see what other bundles they depend on. stamp all these with the same cluster. do it recursively.
    //At the end process the remaining bundles in reverse order. Check if *they* depend on a bundle with known cluster and so on..
    //A few unsolved cases:
    // - we never update the cluster information once a match was found, but there is a possibility that later in the processing the cluster could be "lowered".
    // - 2 or more modules from unrelated clusters we cannot easily decide, most likely should be in common denominator cluster but our cluster2depClusters map is not transitive, only lists direct dependencies
    static void assignClustersToBundles( List<BundleTuple> bundles, Set<String> wrappedBundleCNBs, Map<String, Set<String>> clusterDependencies, Map<String, Set<String>> cluster2depClusters, Log log)
    {
        List<BundleTuple> toProcess = new ArrayList<BundleTuple>();
        List<BundleTuple> known = new ArrayList<BundleTuple>();
        for ( Iterator<BundleTuple> it = bundles.iterator(); it.hasNext(); )
        {
            BundleTuple ent = it.next();
            Artifact art = ent.artifact;
            ExamineManifest ex = ent.manifest;
            String spec = ex.getModule();
            if ( wrappedBundleCNBs.contains( spec ) )
            {
                // we already have this one as a wrapped module.
                log.debug( "Not including bundle " + art.getDependencyConflictId()
                                    + ". It is already included in a NetBeans module" );
                it.remove();
                continue;
            }
            List<String> depclusters = findByDependencies(clusterDependencies, spec);
            if (depclusters.size() == 1) {
                ent.cluster = depclusters.get( 0 );
                known.add( ent );
            } else if (depclusters.isEmpty()) {
                toProcess.add(ent);
            } else {
                //more results.. from 2 dependent clusters pick the one that is lower in the stack.
                for ( Iterator<String> it2 = depclusters.iterator(); it2.hasNext(); )
                {
                    String s = it2.next();
                    Set<String> depsCs = cluster2depClusters.get( s );
                    boolean removeS = false;
                    for (String sDep : depclusters) {
                        if (s.equals( sDep) ) {
                            continue;
                        }
                        if (depsCs != null && depsCs.contains( sDep ) ) {
                            removeS = true;
                        }
                    }
                    if (removeS) {
                        it2.remove();
                    }
                }
                ent.cluster = depclusters.get( 0 ); //TODO still some free room there, what if they don't directly depend on each other but still are related
                known.add (ent);
            }
        }
        if (!toProcess.isEmpty())
        {
            walkKnownBundleDependenciesDown(known, toProcess);
        }
        if (!toProcess.isEmpty())
        {
            walkKnownBundleDependenciesUp(known, toProcess);
        }
    }

    private static void walkKnownBundleDependenciesDown( List<BundleTuple> known, List<BundleTuple> toProcess )
    {
        boolean atLeastOneWasFound = false;
        for ( Iterator<BundleTuple> it = toProcess.iterator(); it.hasNext(); )
        {
            BundleTuple bundleTuple = it.next();
            boolean found = false;
            for ( BundleTuple knownBT : known)
            {
                Sets.SetView<String> is = Sets.intersection(bundleTuple.manifest.getOsgiExports() , knownBT.manifest.getOsgiImports() );
                if (!is.isEmpty()) {
                    found = true;
                    bundleTuple.cluster = knownBT.cluster;
                    break;
                }
                //dependencyTokens are requireBundle - matches the module property
                is = Sets.intersection(Collections.singleton( bundleTuple.manifest.getModule()), new HashSet(knownBT.manifest.getDependencyTokens()) );
                if (!is.isEmpty()) {
                    found = true;
                    bundleTuple.cluster = knownBT.cluster;
                    break;
                }
                
            }
            if (found) {
                atLeastOneWasFound = true;
                it.remove();
                known.add(bundleTuple);
            }
            
        }
        if (!toProcess.isEmpty() && atLeastOneWasFound) {
            walkKnownBundleDependenciesDown( known, toProcess );
        }
    }

    private static void walkKnownBundleDependenciesUp( List<BundleTuple> known, List<BundleTuple> toProcess )
    {
        boolean atLeastOneWasFound = false;
        for ( Iterator<BundleTuple> it = toProcess.iterator(); it.hasNext(); )
        {
            BundleTuple bundleTuple = it.next();
            boolean found = false;
            for ( BundleTuple knownBT : known)
            {
                Sets.SetView<String> is = Sets.intersection(bundleTuple.manifest.getOsgiImports() , knownBT.manifest.getOsgiExports() );
                if (!is.isEmpty()) {
                    found = true;
                    bundleTuple.cluster = knownBT.cluster;
                    break;
                }
                //dependencyTokens are requireBundle - matches the module property
                is = Sets.intersection(Collections.singleton( knownBT.manifest.getModule()), new HashSet(bundleTuple.manifest.getDependencyTokens()) );
                if (!is.isEmpty()) {
                    found = true;
                    bundleTuple.cluster = knownBT.cluster;
                    break;
                }
                
            }
            if (found) {
                atLeastOneWasFound = true;
                it.remove();
                known.add(bundleTuple);
            }
            
        }
        if (!toProcess.isEmpty() && atLeastOneWasFound) {
            walkKnownBundleDependenciesDown( known, toProcess );
        }
        if (!toProcess.isEmpty() && atLeastOneWasFound) {
            walkKnownBundleDependenciesUp( known, toProcess );
        }
    }

    //static and default for tests..
    static Map<String, Set<String>> computeClusterOrdering( Map<String, Set<String>> clusterDependencies, Map<String, Set<String>> clusterModules )
    {
        Map<String, Set<String>> cluster2depClusters = new HashMap<String, Set<String>>();
        for ( Map.Entry<String, Set<String>> entry : clusterDependencies.entrySet() )
        {
            String cluster = entry.getKey();
            Set<String> deps = entry.getValue();
            for (Map.Entry<String, Set<String>> subEnt : clusterModules.entrySet()) {
                if (subEnt.getKey().equals( cluster) ) {
                    continue;
                }
                Sets.SetView<String> is = Sets.intersection(subEnt.getValue(), deps );
                if (!is.isEmpty()) {
                    addToMap( cluster2depClusters, cluster, Collections.singletonList( subEnt.getKey() ) );
                }
            }
        }
        return cluster2depClusters;
    }
    
    static class BundleTuple {
        final Artifact artifact;
        final ExamineManifest manifest;
        String cluster;

        BundleTuple( Artifact artifact, ExamineManifest manifest )
        {
            this.artifact = artifact;
            this.manifest = manifest;
        }
        
    }

    private static class ClusterTuple
    {
        final File location;
        final boolean newer;

        private ClusterTuple( File clusterFile, boolean newer )
        {
            location = clusterFile;
            this.newer = newer;
        }
    }

    static String createBundleConfigFile( String cnb, boolean autoload)
    {
        return
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<!DOCTYPE module PUBLIC \"-//NetBeans//DTD Module Status 1.0//EN\"\n" +
"                        \"http://www.netbeans.org/dtds/module-status-1_0.dtd\">\n" +
"<module name=\"" + cnb +"\">\n" +
"    <param name=\"autoload\">" + autoload + "</param>\n" +
"    <param name=\"eager\">false</param>\n" + (autoload ? "" : "    <param name=\"enabled\">true</param>\n") +
"    <param name=\"jar\">modules/" + cnb.replace( ".", "-") + ".jar</param>\n" +
"    <param name=\"reloadable\">false</param>\n" +
"</module>\n";
    }

    static String createBundleUpdateTracking( String cnb, File moduleArt, File moduleConf, String specVersion )
        throws FileNotFoundException, IOException
    {

        return
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<module codename=\"" + cnb + "\">\n" +
"    <module_version install_time=\"" + System.currentTimeMillis() + "\" last=\"true\" origin=\"installer\" specification_version=\"" + specVersion + "\">\n" +
"        <file crc=\"" + crcForFile( moduleConf ).getValue() + "\" name=\"config/Modules/" + cnb.replace( ".", "-" ) + ".xml\"/>\n" +
"        <file crc=\"" + crcForFile( moduleArt ).getValue() + "\" name=\"modules/" + cnb.replace( ".", "-" ) + ".jar\"/>\n" +
"    </module_version>\n" +
"</module>";

    }

    static CRC32 crcForFile( File inFile )
        throws FileNotFoundException, IOException
    {
        CRC32 crc = new CRC32();
        InputStream inFileStream = new FileInputStream( inFile );
        try {
            byte[] array = new byte[(int) inFile.length()];
            int len = inFileStream.read( array );
            if ( len != array.length )
            {
                throw new IOException( "Cannot fully read " + inFile );
            }
            crc.update( array );
        }
        finally
        {
            inFileStream.close();
        }

        return crc;
    }

}
