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

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
//import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.codehaus.mojo.nbm.model.NbmResource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.util.FileUtils;
import org.netbeans.nbbuild.CreateModuleXML;
import org.netbeans.nbbuild.MakeListOfNBM;
import org.codehaus.mojo.nbm.model.NetBeansModule;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.netbeans.nbbuild.JHIndexer;

/**
 * Create the NetBeans module directory structure, a prerequisite for nbm creation and cluster creation.
 * <p/>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 *
 */
public abstract class CreateNetBeansFileStructure
        extends AbstractNbmMojo
{

    /**
     * NetBeans module assembly build directory.
     * directory where the the NetBeans jar and nbm file get constructed.
     */
    @Parameter(defaultValue="${project.build.directory}/nbm", property="maven.nbm.buildDir")
    protected File nbmBuildDir;
    /**
     * Build directory
     */
    @Parameter(required=true, readonly=true, property="project.build.directory")
    protected File buildDir;
    /**
     * Name of the jar packaged by the jar:jar plugin
     */
    @Parameter(alias="jarname", property="project.build.finalName")
    protected String finalName;
    /**
     * a NetBeans module descriptor containing dependency information and more..
     * @deprecated all content from the module descriptor can be defined as plugin configuration now, will be removed in 4.0 entirely
     */
    @Parameter(defaultValue="${basedir}/src/main/nbm/module.xml")
    protected File descriptor;
    /**
     * NetBeans module's cluster. Replaces the cluster element in module descriptor.
     *
     */
    @Parameter(required=true, defaultValue="extra")
    protected String cluster;
    /**
     * The location of JavaHelp sources for the project. The documentation
     * itself is expected to be in the directory structure based on codenamebase of the module.
     * eg. if your codenamebase is "org.netbeans.modules.apisupport", then the actual docs
     * files shall go to ${basedir}/src/main/javahelp/org/netbeans/modules/apisupport/docs.
     * @deprecated Obsolete as of NetBeans 7.0 with &#64;HelpSetRegistration.
     * @since 2.7
     */
    @Deprecated
    @Parameter(defaultValue="${basedir}/src/main/javahelp")
    protected File nbmJavahelpSource;

    @Parameter(required=true, readonly=true, property="project")
    protected MavenProject project;

    /**
     * A list of additional resources to include in the NBM file.
     * (Not in the module JAR; see <code>InstalledFileLocator</code> for retrieval.)
     * Supersedes similarly-named configuration in the module descriptor file.
     * <p>For example, to include native libraries:</p>
     *
     <pre>
            &lt;nbmResource&gt;
            &nbsp;&nbsp;&lt;directory&gt;src/main/libs&lt;/directory&gt;
            &nbsp;&nbsp;&lt;targetPath&gt;modules/lib&lt;/targetPath&gt;
            &nbsp;&nbsp;&lt;includes&gt;
            &nbsp;&nbsp;&nbsp;&nbsp;&lt;include&gt;*.dll&lt;/include&gt;
            &nbsp;&nbsp;&nbsp;&nbsp;&lt;include&gt;*.so&lt;/include&gt;
            &nbsp;&nbsp;&lt;/includes&gt;
            &lt;/nbmResource&gt;
     </pre>
     *
     * @since 3.2
     */
    @Parameter
    protected Resource[] nbmResources;

    /**
     * The character encoding scheme to be applied when filtering nbm resources.
     *
     * @since 3.2
     */
    @Parameter(property="encoding", defaultValue="${project.build.sourceEncoding}")
    
    protected String encoding;
    
    /**
     * Deployment type of the module, allowed values are <code>normal</code>,<code>eager</code>,<code>autoload</code>. 
     * <p>
     * <code>autoload</code> - Such a module is
     * automatically enabled when some other module requires it and
     * automatically disabled otherwise.</p>
     *                     <p><code>eager</code> - This module type gets
     * automatically enabled when all it's dependencies are
     * satisfied. Disabled otherwise.</p>
     *                     <p><code>normal</code> - This is the default
     * value. This kind of module is enabled/disabled manually by
     * the user. It installs enabled.</p>
     * 
     * For details, see <a href="http://bits.netbeans.org/dev/javadoc/org-openide-modules/org/openide/modules/doc-files/api.html#enablement">Netbeans Module system docs</a>
     * @since 3.8
     */ 
    @Parameter(defaultValue="normal")
    protected String moduleType;
    
    /**
     * codename base of the module, uniquely identifying the module within the NetBeans runtime. usually the package name equivalent.
     * Can include the major release version.
     * See <a href="http://bits.netbeans.org/dev/javadoc/org-openide-modules/org/openide/modules/doc-files/api.html#how-manifest"> NetBeans Module system docs</a>
     * @since 3.8
     */
    @Parameter(defaultValue="${project.groupId}.${project.artifactId}")
    private String codeNameBase;
    
    /**
     * list of groupId:artifactId pairs describing libraries that go into the nbm file and will only include the .external reference in the nbm
     * instead of the actual binary. See <a href="http://netbeans.org/bugzilla/show_bug.cgi?id=195041">NetBeans issue #195041</a> for details.
     * Please note that the scheme will only work for artifacts present in central repository but no effort is made at build time to enforce that.
     * Additionally at runtime when installing the module, the user has to be online and be capable of reaching central using maven. 
     * You have been warned.
     * @since 3.8
     */ 
    @Parameter
    private List<String> externals;


    @Component
    protected MavenResourcesFiltering mavenResourcesFiltering;

    @Parameter(property="session", readonly=true, required=true)
    protected MavenSession session;


    //items used by the CreateNBMMojo.
    protected Project antProject;
    protected NetBeansModule module;
    protected File clusterDir;
    protected String moduleJarName;

    public void execute()
            throws MojoExecutionException, MojoFailureException
    {
        antProject = registerNbmAntTasks();
        if ( descriptor != null && descriptor.exists() )
        {
            module = readModuleDescriptor( descriptor );
        } else
        {
            module = createDefaultDescriptor( project, false );
        }
        String type = moduleType;
        if ("normal".equals(type) && module.getModuleType() != null) {
            type = module.getModuleType();
            getLog().warn( "moduleType in module descriptor is deprecated, use the plugin's parameter moduleType");
        }
        if (!"normal".equals(type) && !"autoload".equals(type) && !"eager".equals(type)) {
            getLog().error( "Only 'normal,autoload,eager' are allowed values in the moduleType parameter");
        }
        boolean autoload = "autoload".equals( type );
        boolean eager = "eager".equals( type );
        // 1. initialization
        String moduleName = codeNameBase;
        if (module.getCodeNameBase() != null) {
            moduleName = module.getCodeNameBase();
            getLog().warn( "codeNameBase in module descriptor is deprecated, use the plugin's parameter codeNameBase");
        }
        moduleName = NetBeansManifestUpdateMojo.stripVersionFromCodebaseName( moduleName.replaceAll( "-", "." ) );
        moduleJarName = moduleName.replace( '.', '-' );
        if ( "extra".equals( cluster ) && module.getCluster() != null )
        {
            getLog().warn(
                    "Parameter cluster in module descriptor is deprecated, use the plugin configuration element." );
            cluster = module.getCluster();
        }
        File jarFile = new File( buildDir, finalName + ".jar" );
        clusterDir = new File( nbmBuildDir, "netbeans" + File.separator + cluster );
        File moduleJarLocation = new File( clusterDir, "modules" );
        moduleJarLocation.mkdirs();

        //2. create nbm resources
        File moduleFile = new File( moduleJarLocation, moduleJarName + ".jar" );

        try
        {
            boolean needPlainCopy = false;
            InputStream is = new FileInputStream( jarFile );
            try
            {
                JarInputStream jis = new JarInputStream( is );
                Manifest m = jis.getManifest();
                Attributes a = m.getMainAttributes();
                String classPath = ( String ) a.remove( new Attributes.Name( "X-Class-Path" ) );
                if ( classPath == null )
                {
                    needPlainCopy = true;
                }
                else // MNBMODULE-133
                {
                    getLog().info( "Copying module JAR to " + moduleJarLocation + " with manifest updates" );
                    a.putValue( "Class-Path", classPath );
                    a.remove( new Attributes.Name( "Maven-Class-Path" ) );
                    OutputStream os = new FileOutputStream( moduleFile );
                    try
                    {
                        JarOutputStream jos = new JarOutputStream( os, m );
                        JarEntry entry;
                        while ( ( entry = jis.getNextJarEntry() ) != null )
                        {
                            JarEntry entry2 = new JarEntry( entry );
                            jos.putNextEntry( entry2 );
                            int c;
                            while ( ( c = jis.read() ) != -1 )
                            {
                                jos.write( c );
                            }
                            jos.closeEntry();
                        }
                        jos.finish();
                        jos.close();
                    }
                    finally
                    {
                        os.close();
                    }
                }
            }
            finally
            {
                is.close();
            }
            if ( needPlainCopy )
            {
                getLog().info( "Copying module JAR to " + moduleJarLocation );
                FileUtils.getFileUtils().copyFile( jarFile, moduleFile, null, true, false );
            }
        }
        catch ( IOException x )
        {
            throw new MojoExecutionException( "Cannot copy module jar", x );
        }

        ExamineManifest modExaminator = new ExamineManifest( getLog() );
        modExaminator.setJarFile( moduleFile );
        modExaminator.checkFile();
        String classpathValue = modExaminator.getClasspath();

        if ( module != null )
        {
            // copy libraries to the designated place..
            @SuppressWarnings("unchecked")
            List<Artifact> artifacts = project.getRuntimeArtifacts();
            for ( Artifact artifact : artifacts )
            {
                File source = artifact.getFile();
                String name = source.getName();
                if ( classpathValue.contains( "ext/" + artifact.getGroupId() + "/" + name ) )
                {
                    File targetDir = new File( moduleJarLocation, "ext/" + artifact.getGroupId() );
                    targetDir.mkdirs();
                    File target = new File( targetDir, name );

                    try
                    {
                        FileUtils.getFileUtils().copyFile( source, target, null, true, false );
                        if ( externals != null && externals.contains(artifact.getGroupId() + ":" + artifact.getArtifactId())) // MNBMODULE-138
                        {
                            getLog().info( "Using *.external replacement for " + name );
                            PrintWriter external = new PrintWriter( new File( targetDir, name + ".external" ), "UTF-8" );
                            try
                            {
                                writeExternal( external, artifact );
                            }
                            finally
                            {
                                external.close();
                            }
                        }
                    }
                    catch ( IOException ex )
                    {
                        getLog().error( "Cannot copy library jar" );
                        throw new MojoExecutionException( "Cannot copy library jar", ex );
                    }
                }
            }
            if ( nbmResources != null )
            {
                copyNbmResources();
            }
            copyDeprecatedNbmResources();
        }

        //javahelp stuff.
        if ( nbmJavahelpSource.exists() )
        {
            getLog().warn( "src/main/javahelp/ deprecated; use @HelpSetRegistration instead" );
            File javahelp_target = new File( buildDir, "javahelp" );
            String javahelpbase = moduleJarName.replace( '-', File.separatorChar ) + File.separator + "docs";
            String javahelpSearch = "JavaHelpSearch";
            File b = new File( javahelp_target, javahelpbase );
            File p = new File( b, javahelpSearch );
            p.mkdirs();
            Copy cp = (Copy) antProject.createTask( "copy" );
            cp.setTodir( javahelp_target );
            FileSet set = new FileSet();
            set.setDir( nbmJavahelpSource );
            cp.addFileset( set );
            cp.execute();
            getLog().info( "Generating JavaHelp Index..." );

            JHIndexer jhTask = (JHIndexer) antProject.createTask( "jhindexer" );
            jhTask.setBasedir( b );
            jhTask.setDb( p );
            jhTask.setIncludes( "**/*.html" );
            jhTask.setExcludes( javahelpSearch );
            Path path = new Path( antProject );
            jhTask.setClassPath( path );
            clearStaticFieldsInJavaHelpIndexer();
            try
            {
                jhTask.execute();
            }
            catch ( BuildException e )
            {
                getLog().error( "Cannot generate JavaHelp index." );
                throw new MojoExecutionException( e.getMessage(), e );
            }
            File helpJarLocation = new File( clusterDir, "modules/docs" );
            helpJarLocation.mkdirs();
            Jar jar = (Jar) antProject.createTask( "jar" );
            jar.setDestFile( new File( helpJarLocation, moduleJarName + ".jar" ) );
            set = new FileSet();
            set.setDir( javahelp_target );
            jar.addFileset( set );
            jar.execute();
        }

        File configDir = new File( clusterDir, "config" + File.separator + "Modules" );
        configDir.mkdirs();
        CreateModuleXML moduleXmlTask = (CreateModuleXML) antProject.createTask( "createmodulexml" );
        moduleXmlTask.setXmldir( configDir );
        FileSet fs = new FileSet();
        fs.setDir( clusterDir );
        fs.setIncludes( "modules" + File.separator + moduleJarName + ".jar" );
        if ( autoload )
        {
            moduleXmlTask.addAutoload( fs );
        }
        else if ( eager )
        {
            moduleXmlTask.addEager( fs );
        }
        else
        {
            moduleXmlTask.addEnabled( fs );
        }
        try
        {
            moduleXmlTask.execute();
        }
        catch ( BuildException e )
        {
            getLog().error( "Cannot generate config file." );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        MakeListOfNBM makeTask = (MakeListOfNBM) antProject.createTask( "genlist" );
        antProject.setNewProperty( "module.name", finalName );
        antProject.setProperty( "cluster.dir", cluster );
        FileSet set = makeTask.createFileSet();
        set.setDir( clusterDir );
        PatternSet pattern = set.createPatternSet();
        pattern.setIncludes( "**" );
        makeTask.setModule( "modules" + File.separator + moduleJarName + ".jar" );
        makeTask.setOutputfiledir( clusterDir );
        try
        {
            makeTask.execute();
        }
        catch ( BuildException e )
        {
            getLog().error( "Cannot Generate nbm list" );
            throw new MojoExecutionException( e.getMessage(), e );
        }

    }

    private void copyDeprecatedNbmResources()
        throws BuildException, MojoExecutionException
    {
        // copy additional resources..
        List<NbmResource> ress = module.getNbmResources();
        if ( ress.size() > 0 )
        {
            getLog().warn( "NBM resources defined in module descriptor are deprecated. Please configure NBM resources in plugin configuration." );
            Copy cp = (Copy) antProject.createTask( "copy" );
            cp.setTodir( clusterDir );
            HashMap<File, Collection<FileSet>> customPaths = new HashMap<File, Collection<FileSet>>();
            boolean hasStandard = false;
            for ( NbmResource res : ress )
            {
                if ( res.getBaseDirectory() != null )
                {
                    File base = new File( project.getBasedir(), res.getBaseDirectory() );
                    FileSet set = new FileSet();
                    set.setDir( base );
                    for ( String inc : res.getIncludes() )
                    {
                        set.createInclude().setName( inc );
                    }
                    for ( String exc : res.getExcludes() )
                    {
                        set.createExclude().setName( exc );
                    }

                    if ( res.getRelativeClusterPath() != null )
                    {
                        File path = new File( clusterDir, res.getRelativeClusterPath() );
                        Collection<FileSet> col = customPaths.get( path );
                        if ( col == null )
                        {
                            col = new ArrayList<FileSet>();
                            customPaths.put( path, col );
                        }
                        col.add( set );
                    }
                    else
                    {
                        cp.addFileset( set );
                        hasStandard = true;
                    }
                }
            }
            try
            {
                if ( hasStandard )
                {
                    cp.execute();
                }
                if ( customPaths.size() > 0 )
                {
                    for ( Map.Entry<File, Collection<FileSet>> ent : customPaths.entrySet() )
                    {
                        cp = (Copy) antProject.createTask( "copy" );
                        cp.setTodir( ent.getKey() );
                        for ( FileSet set : ent.getValue() )
                        {
                            cp.addFileset( set );
                        }
                        cp.execute();
                    }
                }
            }
            catch ( BuildException e )
            {
                getLog().error( "Cannot copy additional resources into the nbm file" );
                throw new MojoExecutionException( e.getMessage(), e );
            }
        }
    }

    // repeated invokation of the javahelp indexer (possibly via multiple classloaders)
    // is causing trouble, residue from previous invokations seems to cause errors
    // this is a nasty workaround for the problem.
    // alternatively we could try invoking the indexer from a separate jvm i guess,
    // ut that's more work.
    private void clearStaticFieldsInJavaHelpIndexer() // MNBMODULE-51 hack
    {
        try
        {
            Class clazz = Class.forName( "com.sun.java.help.search.Indexer" );
            Field fld = clazz.getDeclaredField( "kitRegistry" );
            fld.setAccessible( true );
            Hashtable hash = (Hashtable) fld.get( null );
            hash.clear();

            clazz = Class.forName( "com.sun.java.help.search.HTMLIndexerKit" );
            fld = clazz.getDeclaredField( "defaultParser" );
            fld.setAccessible( true );
            fld.set( null, null);

            fld = clazz.getDeclaredField( "defaultCallback" );
            fld.setAccessible( true );
            fld.set( null, null);

        }
        catch ( IllegalArgumentException ex )
        {
            Logger.getLogger( CreateNetBeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
        catch ( IllegalAccessException ex )
        {
            Logger.getLogger( CreateNetBeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
        catch ( NoSuchFieldException ex )
        {
            Logger.getLogger( CreateNetBeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
        catch ( SecurityException ex )
        {
            Logger.getLogger( CreateNetBeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
        catch ( ClassNotFoundException ex )
        {
            Logger.getLogger( CreateNetBeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
    }

    private void copyNbmResources()
        throws MojoExecutionException
    {
        try
        {
            if ( StringUtils.isEmpty( encoding ) && isFilteringEnabled( nbmResources ) )
            {
                getLog().warn( "File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING
                                   + ", i.e. build is platform dependent!" );
            }
            MavenResourcesExecution mavenResourcesExecution =
                new MavenResourcesExecution( Arrays.asList( nbmResources ), clusterDir, project, encoding,
                                             Collections.EMPTY_LIST, Collections.EMPTY_LIST, session );
            mavenResourcesExecution.setEscapeWindowsPaths( true );
            mavenResourcesFiltering.filterResources( mavenResourcesExecution );
        }
        catch ( MavenFilteringException ex )
        {
            throw new MojoExecutionException( ex.getMessage(), ex );
        }
    }

    /**
     * Determines whether filtering has been enabled for any resource.
     *
     * @param resources The set of resources to check for filtering.
     * @return <code>true</code> if at least one resource uses filtering, <code>false</code> otherwise.
     */
    private boolean isFilteringEnabled( Resource[] resources )
    {
        for ( Resource resource : resources )
        {
            if ( resource.isFiltering() )
            {
                return true;
            }
        }
        return false;
    }

    static void writeExternal( PrintWriter w, Artifact artifact )
        throws IOException
    {
        w.write( "CRC:" );
        File file = artifact.getFile();
        w.write( Long.toString( CreateClusterAppMojo.crcForFile( file ).getValue() ) );
        w.write( "\nSIZE:" );
        w.write( Long.toString( file.length() ) );
        w.write( "\nURL:m2:/" );
        w.write( artifact.getGroupId() );
        w.write( ':' );
        w.write( artifact.getArtifactId() );
        w.write( ':' );
        w.write( artifact.getVersion() );
        w.write( ':' );
        w.write( artifact.getType() );
        if ( artifact.getClassifier() != null )
        {
            w.write( ':' );
            w.write( artifact.getClassifier() );
        }
        w.write( "\nURL:" );
        // artifact.repository is null, so cannot use its url, and anyway might be a mirror
        w.write( /* M3: RepositorySystem.DEFAULT_REMOTE_REPO_URL + '/' */ "http://repo.maven.apache.org/maven2/" );
        w.write( new DefaultRepositoryLayout().pathOf( artifact ) );
        w.write( '\n' );
        w.flush();
    }

}
