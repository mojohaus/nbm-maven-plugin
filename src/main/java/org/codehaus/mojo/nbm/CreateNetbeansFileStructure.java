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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
//import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.codehaus.mojo.nbm.model.NbmResource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.LoadProperties;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.util.FileUtils;
import org.netbeans.nbbuild.CreateModuleXML;
import org.netbeans.nbbuild.MakeListOfNBM;
import org.codehaus.mojo.nbm.model.NetbeansModule;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.netbeans.nbbuild.JHIndexer;

/**
 * Create the Netbeans module directory structure, a prerequisite for nbm creation and cluster creation.
 * <p/>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 *
 */
public abstract class CreateNetbeansFileStructure
        extends AbstractNbmMojo
{

    /**
     * Netbeans module assembly build directory.
     * directory where the the netbeans jar and nbm file get constructed.
     * @parameter default-value="${project.build.directory}/nbm" expression="${maven.nbm.buildDir}"
     */
    protected File nbmBuildDir;
    /**
     * Build directory
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    protected File buildDir;
    /**
     * Name of the jar packaged by the jar:jar plugin
     * @parameter alias="jarName" expression="${project.build.finalName}"
     */
    protected String finalName;
    /**
     * a netbeans module descriptor containing dependency information and more..
     *
     * @parameter default-value="${basedir}/src/main/nbm/module.xml"
     */
    protected File descriptor;
    /**
     * Netbeans module's cluster. Replaces the cluster element in module descriptor.
     *
     * @parameter default-value="extra"
     * @required
     */
    protected String cluster;
    /**
     * The location of JavaHelp sources for the project. The documentation
     * itself is expected to be in the directory structure based on codenamebase of the module.
     * eg. if your codenamebase is "org.netbeans.modules.apisupport", then the actual docs
     * files shall go to ${basedir}/src/main/javahelp/org/netbeans/modules/apisupport/docs.
     * Obsolete as of NetBeans 7.0 with &#64;HelpSetRegistration.
     * @parameter default-value="${basedir}/src/main/javahelp"
     * @since 2.7
     */
    protected File nbmJavahelpSource;
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;
    /**
     * Distribution base URL for the NBM at runtime deployment time.
     * Note: Uselfulness of the parameter is questionable, it doesn't allow for mirrors and
     * usually when downloading the nbm, one alreayd knows the location anyway.
     * Please note that the netbeans.org Ant scripts put a dummy url here.
     * The actual correct value used when constructing update site is
     * explicitly set there. The general assuption there is that all modules from one update
     * center come from one base URL.
     * <p/>
     * The value is either a direct http protocol based URL that points to
     * the location under which nbm file will be located, or
     * <p/>
     * it allows to create an update site based on maven repository content.
     * The later created autoupdate site document can use this information and
     * compose the application from one or multiple maven repositories.
     * <br/>
     * Format: id::layout::url same as in maven-deploy-plugin
     * <br/>
     * with the 'default' and 'legacy' layouts. (maven2 vs maven1 layout)
     * <br/>
     * If the value doesn't contain :: characters,
     * it's assumed to be the flat structure and the value is just the URL.
     * 
     * @parameter expression="${maven.nbm.distributionURL}"
     */
    private String distributionUrl;

    /**
     * The list of nbmResources we want to include in the nbm file (not in module jar,
     * but as external content within the nbm. Replaces the same configuration in the module
     * descriptor file. For example to include external dll files in the nbm:
     *
     <code>
            &lt;nbmResource&gt;<br/>
            &nbsp;&nbsp;&lt;directory&gt;src/main/libs&lt;/directory&gt;<br/>
            &nbsp;&nbsp;&lt;targetPath&gt;lib&lt;/targetPath&gt;<br/>
            &nbsp;&nbsp;&lt;includes&gt;<br/>
            &nbsp;&nbsp;&nbsp;&nbsp;&lt;include&gt;*.dll&lt;/include&gt;<br/>
            &nbsp;&nbsp;&nbsp;&nbsp;&lt;include&gt;*.so&lt;/include&gt;<br/>
            &nbsp;&nbsp;&lt;/includes&gt;<br/>
            &lt;/nbmResource&gt;<br/>
     </code>
     *
     * @parameter
     * @since 3.2
     */
    protected Resource[] nbmResources;

    /**
     * The character encoding scheme to be applied when filtering nbm resources.
     *
     * @parameter expression="${encoding}" default-value="${project.build.sourceEncoding}"
     * @since 3.2
     */
    protected String encoding;

    /**
     *
     * @component role="org.apache.maven.shared.filtering.MavenResourcesFiltering" role-hint="default"
     * @required
     */
    protected MavenResourcesFiltering mavenResourcesFiltering;

    /**
     * @parameter default-value="${session}"
     * @readonly
     * @required
     */
    protected MavenSession session;


    //items used by the CreateNBMMojo.
    protected Project antProject;
    protected NetbeansModule module;
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
        if ( distributionUrl != null )
        {
            module.setDistributionUrl( distributionUrl );
        }
        String type = module.getModuleType();
        boolean autoload = "autoload".equals( type );
        boolean eager = "eager".equals( type );
        // 1. initialization
        if ( autoload && eager )
        {
            getLog().error( "Module cannot be both eager and autoload" );
            throw new MojoExecutionException(
                    "Module cannot be both eager and autoload" );
        }
        String moduleName = module.getCodeNameBase();
        if ( moduleName == null )
        {
            moduleName = project.getGroupId() + "." + project.getArtifactId();
            moduleName = moduleName.replaceAll( "-", "." );
        }
        moduleJarName = moduleName.replace( '.', '-' );
        if ( "extra".equals( cluster ) && module.getCluster() != null )
        {
            getLog().warn(
                    "Parameter cluster in module descriptor is deprecated, use the plugin configuration element." );
            cluster = module.getCluster();
        }
        // it can happen the moduleName is in format org.milos/1
        int index = moduleJarName.indexOf( '/' );
        if ( index > -1 )
        {
            moduleJarName = moduleJarName.substring( 0, index ).trim();
        }

        File jarFile = new File( buildDir, finalName + ".jar" );
        clusterDir = new File( nbmBuildDir,
                "netbeans" + File.separator + cluster );
        File moduleJarLocation = new File( clusterDir, "modules" );
        moduleJarLocation.mkdirs();

        //2. create nbm resources
        File moduleFile = new File( moduleJarLocation, moduleJarName + ".jar" );

        getLog().info( "Copying module jar to " + moduleJarLocation );
        try
        {
            FileUtils.getFileUtils().copyFile( jarFile, moduleFile, null, true,
                    false );
        } catch ( IOException ex )
        {
            getLog().error( "Cannot copy module jar" );
            throw new MojoExecutionException( "Cannot copy module jar", ex );
        }

        ExamineManifest modExaminator = new ExamineManifest( getLog() );
        modExaminator.setJarFile( jarFile );
        modExaminator.checkFile();
        String classpathValue = modExaminator.getClasspath();

        if ( module != null )
        {
            // copy libraries to the designated place..            
            List<String> librList = new ArrayList<String>();
            if ( module.getLibraries() != null )
            {
                librList.addAll( module.getLibraries() );
            }
            @SuppressWarnings("unchecked")
            List<Artifact> artifacts = project.getRuntimeArtifacts();
            for ( Artifact artifact : artifacts )
            {
                File source = artifact.getFile();
                if ( classpathValue.contains( "ext/" + artifact.getGroupId() + "/" + source.getName() ) )
                {
                    File targetDir = new File( moduleJarLocation, "ext/" + artifact.getGroupId() );
                    targetDir.mkdirs();
                    File target = new File( targetDir, source.getName() );

                    try
                    {
                        FileUtils.getFileUtils().copyFile( source, target, null,
                                true, false );
                    } catch ( IOException ex )
                    {
                        getLog().error( "Cannot copy library jar" );
                        throw new MojoExecutionException(
                                "Cannot copy library jar", ex );
                    }
                }
            }
            if (nbmResources != null) {
                copyNbmResources();
            }
            copyDeprecatedNbmResources();
        }

        //javahelp stuff.
        if ( nbmJavahelpSource.exists() )
        {
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
            MNMMODULE51hackClearStaticFieldsInJavaHelpIndexer();
            try
            {
                jhTask.execute();
            } catch ( BuildException e )
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

        File configDir = new File( clusterDir,
                "config" + File.separator + "Modules" );
        configDir.mkdirs();
        CreateModuleXML moduleXmlTask = (CreateModuleXML) antProject.createTask(
                "createmodulexml" );
        moduleXmlTask.setXmldir( configDir );
        FileSet fs = new FileSet();
        fs.setDir( clusterDir );
        fs.setIncludes( "modules" + File.separator + moduleJarName + ".jar" );
        if ( autoload )
        {
            moduleXmlTask.addAutoload( fs );
        } else if ( eager )
        {
            moduleXmlTask.addEager( fs );
        } else
        {
            moduleXmlTask.addEnabled( fs );
        }
        try
        {
            moduleXmlTask.execute();
        } catch ( BuildException e )
        {
            getLog().error( "Cannot generate config file." );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        MakeListOfNBM makeTask = (MakeListOfNBM) antProject.createTask(
                "genlist" );
        antProject.setNewProperty( "module.name", finalName );
        antProject.setProperty( "cluster.dir", cluster );
        FileSet set = makeTask.createFileSet();
        set.setDir( clusterDir );
        PatternSet pattern = set.createPatternSet();
        pattern.setIncludes( "**" );
        makeTask.setModule(
                "modules" + File.separator + moduleJarName + ".jar" );
        makeTask.setOutputfiledir( clusterDir );
        try
        {
            makeTask.execute();
        } catch ( BuildException e )
        {
            getLog().error( "Cannot Generate nbm list" );
            throw new MojoExecutionException( e.getMessage(), e );
        }

    }

    private void copyDeprecatedNbmResources() throws BuildException, MojoExecutionException {
        // copy additional resources..
        List<NbmResource> ress = module.getNbmResources();
        if (ress.size() > 0) {
            getLog().warn("NBM resources defined in module descriptor are deprecated. Please configure NBM resources in plugin configuration.");
            Copy cp = (Copy) antProject.createTask( "copy" );
            cp.setTodir(clusterDir);
            HashMap<File, Collection<FileSet>> customPaths = new HashMap<File, Collection<FileSet>>();
            boolean hasStandard = false;
            for (NbmResource res : ress) {
                if (res.getBaseDirectory() != null) {
                    File base = new File(project.getBasedir(), res.getBaseDirectory());
                    FileSet set = new FileSet();
                    set.setDir(base);
                    for (String inc : res.getIncludes()) {
                        set.createInclude().setName(inc);
                    }
                    for (String exc : res.getExcludes()) {
                        set.createExclude().setName(exc);
                    }

                    if (res.getRelativeClusterPath() != null) {
                        File path = new File(clusterDir, res.getRelativeClusterPath());
                        Collection<FileSet> col = customPaths.get( path );
                        if (col == null) {
                            col = new ArrayList<FileSet>();
                            customPaths.put(path, col);
                        }
                        col.add(set);
                    } else {
                        cp.addFileset(set);
                        hasStandard = true;
                    }
                }
            }
            try {
                if (hasStandard) {
                    cp.execute();
                }
                if (customPaths.size() > 0) {
                    for (Map.Entry<File, Collection<FileSet>> ent : customPaths.entrySet()) {
                        cp = (Copy) antProject.createTask( "copy" );
                        cp.setTodir(ent.getKey());
                        for (FileSet set : ent.getValue()) {
                            cp.addFileset(set);
                        }
                        cp.execute();
                    }
                }
            } catch (BuildException e) {
                getLog().error("Cannot copy additional resources into the nbm file");
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    // repeated invokation of the javahelp indexer (possibly via multiple classloaders)
    // is causing trouble, residue from previous invokations seems to cause errors
    // this is a nasty workaround for the problem.
    // alternatively we could try invoking the indexer from a separate jvm i guess,
    // ut that's more work.
    private void MNMMODULE51hackClearStaticFieldsInJavaHelpIndexer()
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
            Logger.getLogger( CreateNetbeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
        catch ( IllegalAccessException ex )
        {
            Logger.getLogger( CreateNetbeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }        catch ( NoSuchFieldException ex )
        {
            Logger.getLogger( CreateNetbeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
        catch ( SecurityException ex )
        {
            Logger.getLogger( CreateNetbeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }        catch ( ClassNotFoundException ex )
        {
            Logger.getLogger( CreateNetbeansFileStructure.class.getName() ).log( Level.SEVERE, null, ex );
        }
    }

    private void copyNbmResources() throws MojoExecutionException {
        try {
            if (StringUtils.isEmpty(encoding) && isFilteringEnabled(nbmResources)) {
                getLog().warn("File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING + ", i.e. build is platform dependent!");
            }
            MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(Arrays.asList(nbmResources), clusterDir, project, encoding, Collections.EMPTY_LIST, Collections.EMPTY_LIST, session);
            mavenResourcesExecution.setEscapeWindowsPaths(true);
            mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        } catch (MavenFilteringException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
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

}
