/* ==========================================================================
 * Copyright 2003-2006 Mevenide Team
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.artifact.AttachedArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Input;
import org.apache.tools.ant.taskdefs.PathConvert;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

/**
 * A goal for identifying NetBeans modules from the installation and populating the local
 * repository with them. Optionally you can also deploy to a remote repository.
 * <p/>
 * If you are looking for an existing remote repository for NetBeans artifacts, check out
 * <a href="http://bits.netbeans.org/maven2/">http://bits.netbeans.org/maven2/</a>,
 * it contains API artifacts for multiple releases.
 * <a href="http://bits.netbeans.org/netbeans/trunk/maven-snapshot/">http://bits.netbeans.org/netbeans/trunk/maven-snapshot/</a>
 * may also be used for <code>SNAPSHOT</code> artifacts if you wish to test development builds.
 * <p/>
 * See this <a href="http://mojo.codehaus.org/nbm-maven/nbm-maven-plugin/repository.html">HOWTO</a> on how to generate the NetBeans binaries required
 * by this goal.
 * <p/>
 * <b>Compatibility Note</b>: The 3.0+ version puts all unrecognized, non-module, 3rd party jars
 * in the org.netbeans.external group and adds them as dependencies to respective modules.
 * That can cause backward incompatibility with earlier versions which generated incomplete (different)
 * maven metadata.
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 */
@Mojo(name="populate-repository", aggregator=true, requiresProject=false)
public class PopulateRepositoryMojo
    extends AbstractNbmMojo
{
    private static final String GROUP_API = "org.netbeans.api";
    private static final String GROUP_IMPL = "org.netbeans.modules";
    private static final String GROUP_EXTERNAL = "org.netbeans.external";
    private static final String GROUP_CLUSTER = "org.netbeans.cluster";

    /**
     * an url where to deploy the NetBeans artifacts. Optional, if not specified, the artifacts will be only installed
     * in local repository, if you need to give credentials to access remote repo, the id of the server is hardwired to "netbeans".
     */
    @Parameter(property="deployUrl")
    private String deployUrl;

    /**
     * Optional parameter, by default the generated metadata is installed in local repository.
     * Setting this parameter to false will avoid installing the bits. Only meaningful together with
     * a defined "deployUrl" parameter.
     * @since 3.0
     */
    @Parameter(defaultValue="false", property="skipInstall")
    private boolean skipLocalInstall;


    /**
     * Location of NetBeans installation
     */
    @Parameter(property="netbeansInstallDirectory")
    protected File netbeansInstallDirectory;

    /**
     * If you want to install/deploy also NetBeans api javadocs, download the javadoc zip file from netbeans.org
     * expand it to a directory, it should contain multiple zip files. Define this parameter as absolute path to the zip files folder.
     *
     */
    @Parameter(property="netbeansJavadocDirectory")
    protected File netbeansJavadocDirectory;

    /**
     * Assumes a folder with &lt;code-name-base&gt;.zip files containing sources for modules.
     */
    @Parameter(property="netbeansSourcesDirectory")
    protected File netbeansSourcesDirectory;

    /**
     * If defined, will match the nbm files found in the designated folder with the modules
     * and upload the nbm file next to the module jar in local and remote repositories.
     *
     * Assumes a folder with &lt;code-name-base&gt;.nbm files containing nbm files for modules.
     * @since 3.0
     */
    @Parameter(property="netbeansNbmDirectory")
    protected File netbeansNbmDirectory;

    /**
     * Optional parameter, when specified, will force all modules to have the designated version.
     * Good when depending on releases. Then you would for example specify RELEASE50 in this parameter and
     * all modules get this version in the repository. If not defined, the maven version is
     * derived from the OpenIDE-Module-Specification-Version manifest attribute.
     * <p/>
     * Highly Recommended!
     */
    @Parameter(property="forcedVersion")
    protected String forcedVersion;

    /**
     * Optional parameter, when specified it shall point to a directory containing a
     * Nexus Lucene index. This index will be used to find external libraries that
     * are referenced by NetBeans modules and populate the POM metadata with correct
     * dependencies. Any dependencies not found this way, will be generated a unique
     * id under the org.netbeans.external groupId.
     * <p/>
     * The Nexus Lucene index zip file for central repository can be found here:
     * http://repo1.maven.org/maven2/.index/nexus-maven-repository-index.zip
     * Unzip it to a directory and use this parameter to point to it.
     * @since 3.0
     */
    @Parameter(property="nexusIndexDirectory")
    private File nexusIndexDirectory;

    /**
     * Whether to create cluster POMs in the {@code org.netbeans.cluster} group.
     * Only meaningful when {@code forcedVersion} is defined.
     * @since 3.7
     */
    @Parameter(defaultValue="true", property="defineCluster")   
    private boolean defineCluster;

    /**
     * Optional remote repository to use for inspecting remote dependencies.
     * This may be used to populate just part of an installation,
     * when base modules are already available in Maven format.
     * Currently only supported when {@code forcedVersion} is defined.
     * @since 3.7
     */
    @Parameter(property="dependencyRepositoryUrl")
    private String dependencyRepositoryUrl;

    /**
     * Repository ID to use when inspecting remote dependencies.
     * Only meaningful when {@code dependencyRepositoryUrl} is defined.
     * @since 3.7
     */
    @Parameter(defaultValue="temp", property="dependencyRepositoryId")
    private String dependencyRepositoryId;

    // <editor-fold defaultstate="collapsed" desc="Component parameters">
    /**
     * Local maven repository.
     */
    @Parameter(required=true, readonly=true, defaultValue="${localRepository}")
    protected ArtifactRepository localRepository;

    /**
     * Maven ArtifactFactory.
     */
    @Component
    private ArtifactFactory artifactFactory;

    /**
     * Maven ArtifactInstaller.
     */
    @Component
    private ArtifactInstaller artifactInstaller;

    /**
     * Maven ArtifactDeployer.
     *
     */
    @Component
    private ArtifactDeployer artifactDeployer;

    /**
     * Maven ArtifactHandlerManager
     *
     */
    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Maven ArtifactRepositoryFactory.
     *
     */
    @Component
    private ArtifactRepositoryFactory repositoryFactory;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private ArtifactRepositoryLayout artifactRepositoryLayout;
// </editor-fold>

    public void execute()
        throws MojoExecutionException
    {
        getLog().info( "Populate repository with NetBeans modules" );
        Project antProject = registerNbmAntTasks();
        ArtifactRepository deploymentRepository = null;
        if ( deployUrl != null )
        {
            ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();
            deploymentRepository = repositoryFactory.createDeploymentArtifactRepository(
                "netbeans", deployUrl, layout, true );
        }
        else if ( skipLocalInstall )
        {
            throw new MojoExecutionException(
                    "When skipping install to local repository, one shall define the deployUrl parameter" );
        }

        IndexSearcher searcher = null;
        if ( nexusIndexDirectory != null && nexusIndexDirectory.exists() )
        {
            try
            {
                Directory nexusDir = FSDirectory.getDirectory( nexusIndexDirectory );
                IndexReader nexusReader = IndexReader.open( nexusDir );
                searcher = new IndexSearcher( nexusReader );
                getLog().info( "Opened index with " + nexusReader.numDocs() + " documents" );
            }
            catch ( IOException ex )
            {
                getLog().error( "Could not open " + nexusIndexDirectory, ex );
            }
        }

        if ( netbeansInstallDirectory == null )
        {
            Input input = (Input) antProject.createTask( "input" );
            input.setMessage( "Please enter NetBeans installation directory:" );
            input.setAddproperty( "installDir" );
            try
            {
                input.execute();
            }
            catch ( BuildException e )
            {
                getLog().error( "Cannot run ant:input" );
                throw new MojoExecutionException( e.getMessage(), e );
            }
            String prop = antProject.getProperty( "installDir" );
            netbeansInstallDirectory = new File( prop );
        }

        File rootDir = netbeansInstallDirectory;
        if ( !rootDir.exists() )
        {
            getLog().error( "NetBeans installation doesn't exist." );
            throw new MojoExecutionException( "NetBeans installation doesn't exist." );
        }
        getLog().info( "Copying NetBeans artifacts from " + netbeansInstallDirectory );

        PathConvert convert = (PathConvert) antProject.createTask( "pathconvert" );
        convert.setPathSep( "," );
        convert.setProperty( "netbeansincludes" );
        FileSet set = new FileSet();
        set.setDir( rootDir );
        set.createInclude().setName( "**/modules/*.jar" );
        set.createInclude().setName( "*/core/*.jar" );
        set.createInclude().setName( "platform*/lib/*.jar" );

        convert.createPath().addFileset( set );
        try
        {
            convert.execute();
        }
        catch ( BuildException e )
        {
            getLog().error( "Cannot run ant:pathconvert" );
            throw new MojoExecutionException( e.getMessage(), e );
        }

        String prop = antProject.getProperty( "netbeansincludes" );
        StringTokenizer tok = new StringTokenizer( prop, "," );
        HashMap<ModuleWrapper, Artifact> moduleDefinitions = new HashMap<ModuleWrapper, Artifact>();
        HashMap<String, Collection<ModuleWrapper>> clusters = new HashMap<String, Collection<ModuleWrapper>>();
        while ( tok.hasMoreTokens() )
        {
            String token = tok.nextToken();
            File module = new File( token );
            String clust = module.getAbsolutePath().substring( rootDir.getAbsolutePath().length() + 1 );
            clust = clust.substring( 0, clust.indexOf( File.separator ) );
            ExamineManifest examinator = new ExamineManifest( getLog() );
            examinator.setPopulateDependencies( true );
            examinator.setJarFile( module );
            examinator.checkFile();
            if ( examinator.isNetBeansModule() || examinator.isOsgiBundle() )
            {
                //TODO get artifact id from the module's manifest?
                String artifact = module.getName().substring( 0, module.getName().indexOf( ".jar" ) );
                if ( "boot".equals( artifact ) )
                {
                    artifact = "org-netbeans-bootstrap";
                }
                if ( "core".equals( artifact ) )
                {
                    artifact = "org-netbeans-core-startup";
                }
                String version = forcedVersion == null ? examinator.getSpecVersion() : forcedVersion;
                String group = examinator.isOsgiBundle() ? GROUP_EXTERNAL : examinator.hasPublicPackages() ? GROUP_API : GROUP_IMPL;
                Artifact art = createArtifact( artifact, version, group );
                if ( examinator.isOsgiBundle() )
                {
                    Dependency dep = findExternal( searcher, module );
                    if ( dep != null )
                    {
                        // XXX use those coords instead of publishing this
                        // (for now all bundles are from Orbit, which does not publish to Central, or specially built)
                    }
                }
                ModuleWrapper wr = new ModuleWrapper( artifact, version, group, examinator, module );
                wr.setCluster( clust );
                moduleDefinitions.put( wr, art );
                Collection<ModuleWrapper> col = clusters.get( clust );
                if ( col == null )
                {
                    col = new ArrayList<ModuleWrapper>();
                    clusters.put( clust, col );
                }
                col.add( wr );
            }
        }
        List<ModuleWrapper> wrapperList = new ArrayList<ModuleWrapper>( moduleDefinitions.keySet() );
        int count = wrapperList.size() + 1;
        int index = 0;
        File javadocRoot = null;
        if ( netbeansJavadocDirectory != null )
        {
            javadocRoot = netbeansJavadocDirectory ;
            if ( !javadocRoot.exists() )
            {
                javadocRoot = null;
                throw new MojoExecutionException(
                    "The netbeansJavadocDirectory parameter doesn't point to an existing folder" );
            }
        }
        File sourceRoot = null;
        if ( netbeansSourcesDirectory != null )
        {
            sourceRoot = netbeansSourcesDirectory;
            if ( !sourceRoot.exists() )
            {
                sourceRoot = null;
                throw new MojoExecutionException(
                    "The netbeansSourceDirectory parameter doesn't point to an existing folder" );
            }
        }

        File nbmRoot = null;
        if ( netbeansNbmDirectory != null )
        {
            nbmRoot = netbeansNbmDirectory;
            if ( !nbmRoot.exists() )
            {
                nbmRoot = null;
                throw new MojoExecutionException(
                    "The nbmDirectory parameter doesn't point to an existing folder" );
            }
        }

        List<ExternalsWrapper> externals = new ArrayList<ExternalsWrapper>();
        try
        {
            for ( Map.Entry<ModuleWrapper, Artifact> elem : moduleDefinitions.entrySet() )
            {
                ModuleWrapper man = elem.getKey();
                Artifact art = elem.getValue();
                index = index + 1;
                getLog().info( "Processing " + index + "/" + count );
                File pom = createMavenProject( man, wrapperList, externals, searcher );
                ArtifactMetadata metadata = new ProjectArtifactMetadata( art, pom );
                art.addMetadata( metadata );
                File javadoc = null;
                Artifact javadocArt = null;
                if ( javadocRoot != null )
                {
                    File zip = new File( javadocRoot, art.getArtifactId() + ".zip" );
                    if ( zip.exists() )
                    {
                        javadoc = zip;
                        javadocArt = createAttachedArtifact( art, javadoc, "jar", "javadoc" );
                    }
                }
                File source = null;
                Artifact sourceArt = null;
                if ( sourceRoot != null )
                {
                    File zip = new File( sourceRoot, art.getArtifactId() + ".zip" );
                    if ( zip.exists() )
                    {
                        source = zip;
                        sourceArt = createAttachedArtifact( art, source, "jar", "sources" );
                    }
                }
                File nbm = null;
                Artifact nbmArt = null;
                if ( nbmRoot != null )
                {
                    File zip = new File( nbmRoot, art.getArtifactId() + ".nbm" );

                    if ( !zip.exists() )
                    {
                        zip = new File( nbmRoot,
                            man.getCluster() + File.separator + art.getArtifactId() + ".nbm" );
                    }
                    if ( zip.exists() )
                    {
                        nbm = zip;
                        nbmArt = createAttachedArtifact( art, nbm, "nbm-file", null );
                        if ( nbmArt.getArtifactHandler().getExtension().equals( "nbm-file" ) )
                        {
                            // Maven 2.x compatibility.
                            nbmArt = createAttachedArtifact( art, nbm, "nbm", null );
                        }
                        assert nbmArt.getArtifactHandler().getExtension().equals( "nbm" );
                    }
                }
                File moduleJar = man.getFile();
                File moduleJarMinusCP = null;
                if ( ! man.getModuleManifest().getClasspath().isEmpty() )
                {
                    try
                    {
                        moduleJarMinusCP = File.createTempFile( man.getArtifact(), ".jar" );
                        moduleJarMinusCP.deleteOnExit();
                        InputStream is = new FileInputStream( moduleJar );
                        try
                        {
                            OutputStream os = new FileOutputStream( moduleJarMinusCP );
                            try
                            {
                                JarInputStream jis = new JarInputStream( is );
                                Manifest mani = new Manifest( jis.getManifest() );
                                mani.getMainAttributes().remove( Attributes.Name.CLASS_PATH );
                                if ( !man.deps.isEmpty() )
                                { // MNBMODULE-132
                                    StringBuilder b = new StringBuilder();
                                    for ( Dependency dep : man.deps )
                                    {
                                        if ( b.length() > 0 )
                                        {
                                            b.append( ' ' );
                                        }
                                        b.append( dep.getGroupId() ).append( ':' ).append( dep.getArtifactId() ).append( ':' ).append( dep.getVersion() );
                                    }
                                    mani.getMainAttributes().putValue( "Maven-Class-Path", b.toString() );
                                }
                                else
                                {
                                    getLog().warn( "did not find any external artifacts for " + man.getModule() );
                                }
                                JarOutputStream jos = new JarOutputStream( os, mani );
                                JarEntry entry;
                                while ( ( entry = jis.getNextJarEntry() ) != null )
                                {
                                    if ( entry.getName().matches( "META-INF/.+[.]SF" ) )
                                    {
                                        throw new IOException( "cannot handle signed JARs" );
                                    }
                                    jos.putNextEntry( entry );
                                    byte[] buf = new byte[(int) entry.getSize()];
                                    int read = jis.read( buf, 0, buf.length );
                                    if ( read != buf.length )
                                    {
                                        throw new IOException( "read wrong amount" );
                                    }
                                    jos.write( buf );
                                }
                                jos.close();
                            }
                            finally
                            {
                                os.close();
                            }
                        }
                        finally
                        {
                            is.close();
                        }
                    }
                    catch ( IOException x )
                    {
                        getLog().warn( "Could not process " + moduleJar + ": " + x, x );
                        moduleJarMinusCP.delete();
                        moduleJarMinusCP = null;
                    }
                }
                try
                {
                    if ( !skipLocalInstall )
                    {
                        install( moduleJarMinusCP != null ? moduleJarMinusCP : moduleJar, art );
                        if ( javadoc != null )
                        {
                            install( javadoc, javadocArt );
                        }
                        if ( source != null )
                        {
                            install( source, sourceArt );
                        }
                        if ( nbm != null )
                        {
                            install( nbm, nbmArt );
                        }
                    }
                    try
                    {
                        if ( deploymentRepository != null )
                        {
                            artifactDeployer.deploy( moduleJarMinusCP != null ? moduleJarMinusCP : moduleJar, art,
                                                     deploymentRepository, localRepository );
                            if ( javadoc != null )
                            {
                                artifactDeployer.deploy( javadoc, javadocArt, deploymentRepository, localRepository );
                            }
                            if ( source != null )
                            {
                                artifactDeployer.deploy( source, sourceArt, deploymentRepository, localRepository );
                            }
                            if ( nbm != null )
                            {
                                artifactDeployer.deploy( nbm, nbmArt, deploymentRepository, localRepository );
                            }
                        }
                    }
                    catch ( ArtifactDeploymentException ex )
                    {
                        throw new MojoExecutionException( "Error Deploying artifact", ex );
                    }
                }
                finally
                {
                    if ( moduleJarMinusCP != null )
                    {
                        moduleJarMinusCP.delete();
                    }
                }
            }
        }
        finally
        {
            if ( searcher != null )
            {
                try
                {
                    searcher.close();
                }
                catch ( IOException ex )
                {
                    getLog().error( ex );
                }
            }
        }

        //process collected non-recognized external jars..
        if ( externals.size() > 0 )
        {
            index = 0;
            count = externals.size();
            for ( ExternalsWrapper ex : externals )
            {
                Artifact art = createArtifact( ex.getArtifact(), ex.getVersion(), ex.getGroupid() );
                index = index + 1;
                getLog().info( "Processing external " + index + "/" + count );
                File pom = createExternalProject( ex );
                ArtifactMetadata metadata = new ProjectArtifactMetadata( art, pom );
                art.addMetadata( metadata );
                if ( !skipLocalInstall )
                {
                    install( ex.getFile(), art );
                }
                try
                {
                    if ( deploymentRepository != null )
                    {
                        artifactDeployer.deploy( ex.getFile(), art,
                            deploymentRepository, localRepository );
                    }
                }
                catch ( ArtifactDeploymentException exc )
                {
                    throw new MojoExecutionException( "Error Deploying artifact", exc );
                }
            }
        }

        if ( ! defineCluster )
        {
            getLog().info( "Not creating cluster POMs." );
        }
        else if ( forcedVersion == null )
        {
            getLog().warn( "Version not specified, cannot create cluster POMs." );
        }
        else
        {
            for ( Map.Entry<String, Collection<ModuleWrapper>> elem : clusters.entrySet() )
            {
                String cluster = stripClusterName( elem.getKey() );
                Collection<ModuleWrapper> modules = elem.getValue();
                getLog().info( "Processing cluster " + cluster );
                Artifact art = createClusterArtifact( cluster, forcedVersion );
                File pom = createClusterProject( art, modules );
                ProjectArtifactMetadata metadata = new ProjectArtifactMetadata( art, pom );
                art.addMetadata( metadata );
                if ( !skipLocalInstall )
                {
                    install( pom, art );
                }
                try
                {
                    if ( deploymentRepository != null )
                    {
                        artifactDeployer.deploy( pom, art, deploymentRepository, localRepository );
                    }
                }
                catch ( ArtifactDeploymentException ex )
                {
                    throw new MojoExecutionException( "Error Deploying artifact", ex );
                }
            }

        }
    }

    void install( File file, Artifact art )
        throws MojoExecutionException
    {
        assert localRepository != null;
        try
        {
            artifactInstaller.install( file, art, localRepository );
        }
        catch ( ArtifactInstallationException e )
        {
            // TODO: install exception that does not give a trace
            throw new MojoExecutionException( "Error installing artifact", e );
        }
    }

    //performs the same tasks as the MavenProjectHelper
    Artifact createAttachedArtifact( Artifact primary, File file, String type, String classifier )
    {
        assert type != null;

        ArtifactHandler handler = null;

        handler = artifactHandlerManager.getArtifactHandler( type );

        if ( handler == null )
        {
            getLog().warn( "No artifact handler for " + type );
            handler = artifactHandlerManager.getArtifactHandler( "jar" );
        }

        Artifact artifact = new AttachedArtifact( primary, type, classifier, handler );

        artifact.setFile( file );
        artifact.setResolved( true );
        return artifact;
    }

    private File createMavenProject( ModuleWrapper wrapper, List<ModuleWrapper> wrapperList,
                                     List<ExternalsWrapper> externalsList, IndexSearcher searcher )
            throws MojoExecutionException
    {
        Model mavenModel = new Model();

        mavenModel.setGroupId( wrapper.getGroup() );
        mavenModel.setArtifactId( wrapper.getArtifact() );
        mavenModel.setVersion( wrapper.getVersion() );
        mavenModel.setPackaging( "jar" );
        mavenModel.setModelVersion( "4.0.0" );
        ExamineManifest man = wrapper.getModuleManifest();
        List<Dependency> deps = new ArrayList<Dependency>();
        if ( !man.getDependencyTokens().isEmpty() )
        {
            for ( String elem : man.getDependencyTokens() )
            {
                // create pseudo wrapper
                ModuleWrapper wr = new ModuleWrapper( elem );
                int index = wrapperList.indexOf( wr );
                if ( index > -1 )
                {
                    wr = wrapperList.get( index );
                    Dependency dep = new Dependency();
                    dep.setArtifactId( wr.getArtifact() );
                    dep.setGroupId( wr.getGroup() );
                    dep.setVersion( wr.getVersion() );
                    dep.setType( "jar" );
                    //we don't want the API modules to depend on non-api ones..
                    // otherwise the transitive dependency mechanism pollutes your classpath..
                    if ( wrapper.getModuleManifest().hasPublicPackages() && !wr.getModuleManifest().hasPublicPackages() )
                    {
                        dep.setScope( "runtime" );
                    }
                    deps.add( dep );
                }
                else if ( dependencyRepositoryUrl != null )
                {
                    Dependency dep = new Dependency();
                    dep.setType( "jar" );
                    String artifactId = elem.replace( '.', '-' );
                    dep.setArtifactId( artifactId );
                    if ( forcedVersion == null )
                    {
                        throw new MojoExecutionException( "Cannot use dependencyRepositoryUrl without forcedVersion" );
                    }
                    dep.setVersion( forcedVersion );
                    ArtifactRepositoryPolicy policy = new ArtifactRepositoryPolicy();
                    List<ArtifactRepository> repos = Collections.singletonList(
                            repositoryFactory.createArtifactRepository( dependencyRepositoryId, dependencyRepositoryUrl, artifactRepositoryLayout, policy, policy) );
                    try
                    {
                        artifactResolver.resolve( artifactFactory.createBuildArtifact( GROUP_API, artifactId, forcedVersion, "pom" ), repos, localRepository );
                        dep.setGroupId( GROUP_API );
                    }
                    catch ( AbstractArtifactResolutionException x )
                    {
                        try
                        {
                            artifactResolver.resolve( artifactFactory.createBuildArtifact( GROUP_IMPL, artifactId, forcedVersion, "pom" ), repos, localRepository );
                            dep.setGroupId( GROUP_IMPL );
                            if ( wrapper.getModuleManifest().hasPublicPackages() )
                            {
                                dep.setScope( "runtime" );
                            }
                        }
                        catch ( AbstractArtifactResolutionException x2 )
                        {
                            getLog().warn( x2.getOriginalMessage() );
                            throw new MojoExecutionException( "No module found for dependency '" + elem + "'", x );
                        }

                    }
                    deps.add( dep );
                }
                else
                {
                    getLog().warn( "No module found for dependency '" + elem + "'" );
                }
            }
        }
        //need some generic way to handle Classpath: items.
        //how to figure the right version?
        String cp = wrapper.getModuleManifest().getClasspath();
        if ( !cp.isEmpty() )
        {
            StringTokenizer tok = new StringTokenizer( cp );
            while ( tok.hasMoreTokens() )
            {
                String path = tok.nextToken();
                File f = new File( wrapper.getFile().getParentFile(), path );
                if ( f.exists() )
                {
                    Dependency dep = findExternal( searcher, f );
                    if ( dep != null )
                    {
                        deps.add( dep );
                        // XXX MNBMODULE-170: repack NBM with *.external
                    }
                    else
                    {
                        ExternalsWrapper ex = new ExternalsWrapper();
                        ex.setFile( f );
                        String artId = f.getName();
                        if ( artId.endsWith( ".jar" ) )
                        {
                            artId = artId.substring( 0, artId.length() - ".jar".length() );
                        }
                        ex.setVersion( wrapper.getVersion() );
                        ex.setArtifact( artId );
                        ex.setGroupid( GROUP_EXTERNAL );
                        externalsList.add( ex );
                        dep = new Dependency();
                        dep.setArtifactId( artId );
                        dep.setGroupId( GROUP_EXTERNAL );
                        dep.setVersion( wrapper.getVersion() );
                        dep.setType( "jar" );
                        deps.add( dep );
                    }
                }
            }
        }

        wrapper.deps = deps;
        mavenModel.setDependencies( deps );
        FileWriter writer = null;
        File fil = null;
        try
        {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = File.createTempFile( "maven", ".pom" );
            fil.deleteOnExit();
            writer = new FileWriter( fil );
            xpp.write( writer, mavenModel );
        }
        catch ( IOException ex )
        {
            ex.printStackTrace();

        }
        finally
        {
            if ( writer != null )
            {
                try
                {
                    writer.close();
                }
                catch ( IOException io )
                {
                    io.printStackTrace();
                }
            }
        }
        return fil;
    }

    private Dependency findExternal( IndexSearcher searcher, File f )
    {
        if ( searcher == null )
        {
            return null;
        }
        try
        {
            MessageDigest shaDig = MessageDigest.getInstance( "SHA1" );
            InputStream is = new FileInputStream( f );
            try {
                OutputStream os = new DigestOutputStream( new NullOutputStream(), shaDig );
                IOUtil.copy( is, os );
                os.close();
            }
            finally
            {
                is.close();
            }
            String sha = encode( shaDig.digest() );
            TermQuery q = new TermQuery( new Term( "1", sha ) );
            Hits hits = searcher.search( q );
            if ( hits.length() == 1 )
            {
                Document doc = hits.doc( 0 );
                Field idField = doc.getField( "u" );
                if ( idField != null )
                {
                    String id = idField.stringValue();
                    String[] splits = StringUtils.split( id, "|" );
                    Dependency dep = new Dependency();
                    dep.setArtifactId( splits[1] );
                    dep.setGroupId( splits[0] );
                    dep.setVersion( splits[2] );
                    dep.setType( "jar" );
                    if ( splits.length > 3 && !"NA".equals( splits[3] ) )
                    {
                        dep.setClassifier( splits[3] );
                    }
                    getLog().info( "found match " + splits[0] + ":" + splits[1] + ":" + splits[2] + " for " + f.getName() );
                    return dep;
                }
                else
                {
                    getLog().error( "no idField for " + q );
                }
            }
            else
            {
                getLog().info( "no repository match for " + f.getName() );
            }
        }
        catch ( Exception x )
        {
            getLog().error( x );
        }
        return null;
    }

    File createExternalProject( ExternalsWrapper wrapper )
    {
        Model mavenModel = new Model();

        mavenModel.setGroupId( wrapper.getGroupid() );
        mavenModel.setArtifactId( wrapper.getArtifact() );
        mavenModel.setVersion( wrapper.getVersion() );
        mavenModel.setPackaging( "jar" );
        mavenModel.setModelVersion( "4.0.0" );
        mavenModel.setName( 
            "Maven definition for " + wrapper.getFile().getName() + " - external part of NetBeans module." );
        mavenModel.setDescription( 
            "POM and identification for artifact that was not possible to uniquely identify as a maven dependency." );
        FileWriter writer = null;
        File fil = null;
        try
        {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = File.createTempFile( "maven", ".pom" );
            fil.deleteOnExit();
            writer = new FileWriter( fil );
            xpp.write( writer, mavenModel );
        }
        catch ( IOException ex )
        {
            ex.printStackTrace();
        }
        finally
        {
            if ( writer != null )
            {
                try
                {
                    writer.close();
                }
                catch ( IOException io )
                {
                    io.printStackTrace();
                }
            }
        }
        return fil;

    }

    private File createClusterProject( Artifact cluster, Collection<ModuleWrapper> mods )
    {
        Model mavenModel = new Model();

        mavenModel.setGroupId( cluster.getGroupId() );
        mavenModel.setArtifactId( cluster.getArtifactId() );
        mavenModel.setVersion( cluster.getVersion() );
//        mavenModel.setPackaging("nbm-application");
        mavenModel.setPackaging( "pom" );
        mavenModel.setModelVersion( "4.0.0" );
        List<Dependency> deps = new ArrayList<Dependency>();
        for ( ModuleWrapper wr : mods )
        {
            Dependency dep = new Dependency();
            dep.setArtifactId( wr.getArtifact() );
            dep.setGroupId( wr.getGroup() );
            dep.setVersion( wr.getVersion() );
            if ( wr.getModuleManifest().isNetBeansModule() )
            {
                dep.setType( "nbm-file" );
            }
            deps.add( dep );
        }
        mavenModel.setDependencies( deps );
//        
//        
//        Build build = new Build();
//        Plugin plg = new Plugin();
//        plg.setGroupId("org.codehaus.mojo");
//        plg.setArtifactId("nbm-maven-plugin");
//        plg.setVersion("2.7-SNAPSHOT");
//        plg.setExtensions(true);
//        build.addPlugin(plg);
//        mavenModel.setBuild(build);

        FileWriter writer = null;
        File fil = null;
        try
        {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = File.createTempFile( "maven", ".pom" );
            fil.deleteOnExit();
            writer = new FileWriter( fil );
            xpp.write( writer, mavenModel );
        }
        catch ( IOException ex )
        {
            ex.printStackTrace();
        }
        finally
        {
            IOUtil.close( writer );
        }
        return fil;
    }

    Artifact createArtifact( String artifact, String version, String group )
    {
        return artifactFactory.createBuildArtifact( group, artifact, version, "jar" );
    }

    private Artifact createClusterArtifact( String artifact, String version )
    {
        return artifactFactory.createBuildArtifact( GROUP_CLUSTER, artifact, version, "pom" );
    }

    private static Pattern PATTERN_CLUSTER = Pattern.compile( "([a-zA-Z]+)[0-9\\.]*" );
    static String stripClusterName( String key )
    {
        Matcher m = PATTERN_CLUSTER.matcher( key );
        if ( m.matches() )
        {
            return m.group( 1 );
        }
        return key;
    }

    private static class ExternalsWrapper
    {

        private File file;

        private String artifact;

        private String groupid;

        public String getArtifact()
        {
            return artifact;
        }

        public void setArtifact( String artifact )
        {
            this.artifact = artifact;
        }

        public File getFile()
        {
            return file;
        }

        public void setFile( File file )
        {
            this.file = file;
        }

        public String getGroupid()
        {
            return groupid;
        }

        public void setGroupid( String groupid )
        {
            this.groupid = groupid;
        }

        public String getVersion()
        {
            return version;
        }

        public void setVersion( String version )
        {
            this.version = version;
        }
        private String version;

    }

    private static class ModuleWrapper
    {

        ExamineManifest man;

        private String artifact;

        private String version;

        private String group;

        private File file;

        private String cluster;

        String module;

        List<Dependency> deps;

        public ModuleWrapper( String module )
        {
            this.module = module;
        }

        public ModuleWrapper( String art, String ver, String grp, ExamineManifest manifest, File fil )
        {
            man = manifest;
            artifact = art;
            version = ver;
            group = grp;
            file = fil;
        }

        public int hashCode()
        {
            return getModule().hashCode();
        }

        public boolean equals( Object obj )
        {
            return obj instanceof ModuleWrapper && getModule().equals( ( (ModuleWrapper) obj ).getModule() );
        }

        public String getModule()
        {
            return module != null ? module : getModuleManifest().getModule();
        }

        public ExamineManifest getModuleManifest()
        {
            return man;
        }

        private String getArtifact()
        {
            return artifact;
        }

        private String getVersion()
        {
            return version;
        }

        private String getGroup()
        {
            return group;
        }

        private File getFile()
        {
            return file;
        }

        void setCluster( String clust )
        {
            cluster = clust;
        }

        String getCluster()
        {
            return cluster;
        }
    }

    private static class NullOutputStream
        extends OutputStream
    {

        public void write( int b )
            throws IOException
        {
        }
    }

    /**
     * Encodes a 128 bit or 160-bit byte array into a String.
     *
     * @param binaryData Array containing the digest
     * @return Encoded hex string, or null if encoding failed
     */
    static String encode( byte[] binaryData )
    {
        int bitLength = binaryData.length * 8;
        if ( bitLength != 128 && bitLength != 160 )
        {
            throw new IllegalArgumentException(
                "Unrecognised length for binary data: " + bitLength + " bits" );
        }
        return String.format( "%0" + bitLength / 4 + "x", new BigInteger( 1, binaryData ) );
    }
}
