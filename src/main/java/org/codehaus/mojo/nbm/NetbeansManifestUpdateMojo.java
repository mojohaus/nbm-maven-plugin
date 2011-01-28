/* ==========================================================================
 * Copyright 2003-2007 Mevenide Team
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.text.BreakIterator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.jar.Attributes;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.mojo.nbm.model.Dependency;
import org.codehaus.mojo.nbm.model.NetbeansModule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.codehaus.plexus.util.IOUtil;

/**
 * Goal for generating NetBeans module system specific manifest entries, part of "nbm" lifecycle/packaging.
 *
 * In order to have the generated manifest picked up by the maven-jar-plugin,
 * one shall add the following configuration snippet to maven-jar-plugin.
 * <p/>
 * <code>
    &lt;plugin&gt;<br/>
        &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;<br/>
        &lt;artifactId&gt;maven-jar-plugin&lt;/artifactId&gt;<br/>
        &lt;version&gt;2.2&lt;/version&gt;<br/>
        &lt;configuration&gt;<br/>
            &lt;useDefaultManifestFile&gt;true&lt;/useDefaultManifestFile&gt;<br/>
        &lt;/configuration&gt;<br/>
    &lt;/plugin&gt;<br/>
 * </code>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal manifest
 * @phase process-classes
 * @requiresDependencyResolution runtime
 * @requiresProject
 */
public class NetbeansManifestUpdateMojo
    extends AbstractNbmMojo
{

    /**
     * Netbeans module assembly build directory.
     * directory where the the netbeans jar and nbm file get constructed.
     * @parameter default-value="${project.build.directory}/nbm" expression="${maven.nbm.buildDir}"
     */
    protected File nbmBuildDir;

    /**
     * a netbeans module descriptor containing dependency information and more
     *
     * @parameter default-value="${basedir}/src/main/nbm/module.xml"
     */
    protected File descriptor;

    /**
     * maven project
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

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
     * Path to manifest file that will be used as base for the 
     *
     * @parameter default-value="${basedir}/src/main/nbm/manifest.mf"
     * @since 3.0
     * @required
     */
    private File sourceManifestFile;

    /**
     * Path to the generated MANIFEST file to use. It will be used by jar:jar plugin.
     *
     * @parameter expression="${project.build.outputDirectory}/META-INF/MANIFEST.MF"
     * @since 3.0
     * @readonly
     * @required
     */
    private File targetManifestFile;

    /**
     * Verify the runtime NetBeans module dependencies and Class-Path items
     * generated from Maven dependencies. The check is done by matcing classes used
     * in current project. Allowed values for the parameter are "fail", "warn" and "skip".
     * The default is "fail" in which case the validation failure results in a failed build,
     * in the vast majority of cases the module would fail at runtime anyway.
     *
     * @parameter expression="${maven.nbm.verify}" default-value="fail"
     * @since 3.0
     */
    private String verifyRuntime;
    
    private static final String FAIL = "fail";
    private static final String WARN = "warn";
    private static final String SKIP = "skip";

    /**
     * A list of module's public packages. If not defined, no packages are exported as public.
     * Allowed values are single package names
     * or package names ending with .* which represent the package and all subpackages.
     * <p/>
     * Eg. "org.kleint.milos.api" designates just the one package, while "org.kleint.milos.spi.*"
     * denotes the spi package an all it's subpackages.
     * @parameter
     * @since 3.0
     */
    private List<String> publicPackages;

    /**
     * When encountering an OSGi bundle among dependencies, the plugin will generate a direct dependency
     * on the bundle and will not include the bundle's jar into the nbm. Will only work with Netbeans 6.9+ runtime.
     * Therefore it is off by default.
     * WARNING: Additionally existing applications/modules need to check modules wrapping
     * external libraries for library jars that are also OSGi bundles. Such modules will no longer include the OSGi bundles
     * as part of the module but will include a modular dependency on the bundle. Modules depending on these old wrappers
     * shall depend directly on the bundle, eventually rendering the old library wrapper module obsolete.
     *
     * @parameter default-value="false"
     * @since 3.2
     *
     */

    private boolean useOSGiDependencies;

    // <editor-fold defaultstate="collapsed" desc="Component parameters">

    /**
     * The artifact repository to use.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * The artifact factory to use.
     *
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * The artifact metadata source to use.
     *
     * @component
     */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * The artifact collector to use.
     *
     * @component
     */
    private ArtifactCollector artifactCollector;

    /**
     * The dependency tree builder to use.
     *
     * @component
     */
    private DependencyTreeBuilder dependencyTreeBuilder;

// end of component params custom code folding
// </editor-fold> 

    /**
     * execute plugin
     * @throws org.apache.maven.plugin.MojoExecutionException
     * @throws org.apache.maven.plugin.MojoFailureException
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException

    {
        //need to do this to chekc for javahelp on CP.
        super.registerNbmAntTasks();
        NetbeansModule module;
        if ( descriptor != null && descriptor.exists() )
        {
            module = readModuleDescriptor( descriptor );
        }
        else
        {
            module = createDefaultDescriptor( project, false );
        }

        String moduleName = module.getCodeNameBase();
        if ( moduleName == null )
        {
            moduleName = project.getGroupId() + "." + project.getArtifactId();
            moduleName = moduleName.replaceAll( "-", "." );
        }
//<!-- if a netbeans specific manifest is defined, examine this one, otherwise the already included one.
// ignoring the case when some of the netbeans attributes are already defined in the jar and more is included.
        File specialManifest = sourceManifestFile;
        File nbmManifest = ( module.getManifest() != null ? new File(
            project.getBasedir(), module.getManifest() ) : null );
        if ( nbmManifest != null && nbmManifest.exists() )
        {
            //deprecated, but if actually defined, will use it.
            specialManifest = nbmManifest;
        }
        ExamineManifest examinator = new ExamineManifest( getLog() );
        if ( specialManifest != null && specialManifest.exists() )
        {
            examinator.setManifestFile( specialManifest );
            examinator.checkFile();
        }
        else
        {
//            examinator.setJarFile( jarFile );
        }

        getLog().info( "NBM Plugin generates manifest" );

        Manifest manifest = null;
        if ( specialManifest != null && specialManifest.exists() )
        {
            Reader reader = null;
            try
            {
                reader = new InputStreamReader( new FileInputStream(
                    specialManifest ) );
                manifest = new Manifest( reader );
            }
            catch ( IOException exc )
            {
                manifest = new Manifest();
                getLog().warn( "Error reading manifest at " + specialManifest, exc );
            }
            catch ( ManifestException ex )
            {
                getLog().warn( "Error reading manifest at " + specialManifest, ex );
                manifest = new Manifest();
            }
            finally
            {
                IOUtil.close( reader );
            }
        }
        else
        {
            manifest = new Manifest();
        }
        String specVersion = AdaptNbVersion.adaptVersion( project.getVersion(),
            AdaptNbVersion.TYPE_SPECIFICATION );
        String implVersion = AdaptNbVersion.adaptVersion( project.getVersion(),
            AdaptNbVersion.TYPE_IMPLEMENTATION );
        Manifest.Section mainSection = manifest.getMainSection();
        conditionallyAddAttribute( mainSection,
            "OpenIDE-Module-Specification-Version", specVersion );
        conditionallyAddAttribute( mainSection,
            "OpenIDE-Module-Implementation-Version", implVersion );
        final String timestamp = createTimestamp();
        conditionallyAddAttribute( mainSection, "OpenIDE-Module-Build-Version",
            timestamp );
        String projectCNB = conditionallyAddAttribute( mainSection, "OpenIDE-Module", moduleName );
        String packagesValue;
        if (publicPackages != null && publicPackages.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (String pub : publicPackages) {
                if (pub.endsWith( ".**")) {
                    // well, just sort of wrong value but accept
                    sb.append(pub);
                } else if (pub.endsWith(".*")) {
                    //multipackage value
                    sb.append(pub).append("*");
                } else {
                    sb.append(pub).append(".*");
                }
                sb.append(", ");
            }
            sb.setLength( sb.length() - 2); //cut the last 2 ", " characters
            packagesValue = sb.toString();
        } else {
            // no packages available
            packagesValue = "-";
        }
        conditionallyAddAttribute( mainSection, "OpenIDE-Module-Public-Packages", packagesValue );

        //See http://www.netbeans.org/download/dev/javadoc/org-openide-modules/apichanges.html#split-of-openide-jar
        conditionallyAddAttribute( mainSection, "OpenIDE-Module-Requires",
            "org.openide.modules.ModuleFormat1" );
//        conditionallyAddAttribute(mainSection, "OpenIDE-Module-IDE-Dependencies", "IDE/1 > 3.40");
        // localization items
        if ( !examinator.isLocalized() )
        {
            conditionallyAddAttribute( mainSection,
                "OpenIDE-Module-Display-Category", project.getGroupId() );
            conditionallyAddAttribute( mainSection, "OpenIDE-Module-Name",
                project.getName() );
            conditionallyAddAttribute( mainSection,
                "OpenIDE-Module-Short-Description", shorten( project.getDescription() ) );
            conditionallyAddAttribute( mainSection,
                "OpenIDE-Module-Long-Description", project.getDescription() );
        }
        getLog().debug( "module =" + module );
        if ( module != null )
        {
            DependencyNode treeroot = createDependencyTree( project, dependencyTreeBuilder, localRepository,
                artifactFactory, artifactMetadataSource, artifactCollector, "compile" );
            Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
            @SuppressWarnings( "unchecked" )
            List<Artifact> libArtifacts = getLibraryArtifacts( treeroot, module, project.getRuntimeArtifacts(),
                examinerCache, getLog(), useOSGiDependencies );
            List<ModuleWrapper> moduleArtifacts = getModuleDependencyArtifacts( treeroot, module, project, examinerCache,
                libArtifacts, getLog(), useOSGiDependencies );
            String classPath = "";
            String dependencies = "";
            String depSeparator = " ";

            for ( Artifact a : libArtifacts )
            {
                classPath = classPath + " ext/" + a.getGroupId() + "/" + a.getFile().getName();
            }

            for ( ModuleWrapper wr : moduleArtifacts )
            {
                if ( wr.transitive )
                {
                    continue;
                }
                Dependency dep = wr.dependency;
                Artifact artifact = wr.artifact;
                ExamineManifest depExaminator = examinerCache.get( artifact );
                String type = dep.getType();
                String depToken = dep.getExplicitValue();
                if ( depToken == null )
                {
                    if ( "loose".equals( type ) )
                    {
                        depToken = depExaminator.getModuleWithRelease();
                    }
                    else if ( "spec".equals( type ) )
                    {
                        depToken = depExaminator.getModuleWithRelease() + " > " +
                            ( depExaminator.isNetbeansModule() ? depExaminator.getSpecVersion() : AdaptNbVersion.adaptVersion(
                            depExaminator.getSpecVersion(),
                            AdaptNbVersion.TYPE_SPECIFICATION ) );
                    }
                    else if ( "impl".equals( type ) )
                    {
                        depToken = depExaminator.getModuleWithRelease() + " = " +
                            ( depExaminator.isNetbeansModule() ? depExaminator.getImplVersion() : AdaptNbVersion.adaptVersion(
                            depExaminator.getImplVersion(),
                            AdaptNbVersion.TYPE_IMPLEMENTATION ) );
                    }
                    else
                    {
                        throw new MojoExecutionException(
                            "Wrong type of Netbeans dependency: " + type + " Allowed values are: loose, spec, impl." );
                    }
                }
                if ( depToken == null )
                {
                    //TODO report
                    getLog().error(
                        "Cannot properly resolve the netbeans dependency for " + dep.getId() );
                }
                else
                {
                    dependencies = dependencies + depSeparator + depToken;
                    depSeparator = ", ";
                }
            }
            if ( !verifyRuntime.equalsIgnoreCase( SKIP ) )
            {
                try
                {
                    checkModuleClassPath( treeroot, libArtifacts, examinerCache, moduleArtifacts, projectCNB );
                }
                catch ( IOException ex )
                {
                    throw new MojoExecutionException( "Error while checking runtime dependencies", ex );
                }
            }

            if ( nbmJavahelpSource.exists() )
            {
                String moduleJarName = stripVersionFromCodebaseName( moduleName ).replace( ".", "-" );
                classPath = classPath + " docs/" + moduleJarName + ".jar";
            }

            if ( classPath.length() > 0 )
            {
                conditionallyAddAttribute( mainSection, Attributes.Name.CLASS_PATH.toString(),
                    classPath.trim() );
            }
            if ( dependencies.length() > 0 )
            {
                conditionallyAddAttribute( mainSection,
                    "OpenIDE-Module-Module-Dependencies", dependencies );
            }
//            if ( librList.size() > 0 )
//            {
//                String list = "";
//                for ( int i = 0; i < librList.size(); i++ )
//                {
//                    list = list + " " + librList.get( i );
//                }
//                getLog().warn(
//                        "Some libraries could not be found in the dependency chain: " + list );
//            }
        }
        PrintWriter writer = null;
        try
        {
            if ( !targetManifestFile.exists() )
            {
                targetManifestFile.getParentFile().mkdirs();
                targetManifestFile.createNewFile();
            }
            writer = new PrintWriter( targetManifestFile, "UTF-8" ); //TODO really UTF-8??
            manifest.write( writer );
        }
        catch ( IOException ex )
        {
            throw new MojoExecutionException( ex.getMessage(), ex );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

    /**
     * Create a timestamp for <code>OpenIDE-Module-Build-Version</code> manifest
     * entry.
     *
     * It's created from the current time and formatted using a UTC timezone
     * explicitly which makes created timestamp timezone-independent.
     *
     * @return timestamp represented as <code>201012292045</code>
     */
    private String createTimestamp()
    {
        final SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyyMMddHHmm" );
        dateFormat.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
        final String timestamp = dateFormat.format( new Date() );
        return timestamp;
    }

    private String stripVersionFromCodebaseName( String cnb )
    {
        // it can happen the moduleName is in format org.milos/1
        String base = cnb;
        int index = base.indexOf( '/' );
        if ( index > -1 )
        {
            base = base.substring( 0, index ).trim();
        }
        return base;
    }

    String conditionallyAddAttribute( Manifest.Section section, String key, String value )
    {
        Manifest.Attribute attr = section.getAttribute( key );
        if ( attr == null )
        {
            attr = new Manifest.Attribute();
            attr.setName( key );
            attr.setValue( value != null ? value.replaceAll("\\s+", " ").trim() : "<undefined>" );
            try
            {
                section.addConfiguredAttribute( attr );
            }
            catch ( ManifestException ex )
            {
                getLog().error( "Cannot update manifest (key=" + key + ")" );
                ex.printStackTrace();
            }
        }
        return attr.getValue();
    }

    /**
     * Pick out the first sentence of a paragraph.
     * @param paragraph some text (may be null)
     * @return the first sentence (may be null)
     */
    static String shorten( String paragraph )
    {
        if ( paragraph == null || paragraph.length() == 0 )
        {
            return null;
        }
        BreakIterator breaker = BreakIterator.getSentenceInstance();
        breaker.setText( paragraph );
        return paragraph.substring( 0, breaker.following( 0 ) ).trim();
    }

//----------------------------------------------------------------------------------
// classpat checking related.
//----------------------------------------------------------------------------------
    private void checkModuleClassPath( DependencyNode treeroot,
        List<Artifact> libArtifacts,
        Map<Artifact, ExamineManifest> examinerCache, List<ModuleWrapper> moduleArtifacts, String projectCodeNameBase )
        throws IOException, MojoExecutionException, MojoFailureException
    {
        Set<String> deps = buildProjectDependencyClasses( project, libArtifacts );
        deps.retainAll( allProjectClasses( project ) );

        Set<String> own = projectModuleOwnClasses( project, libArtifacts );
        deps.removeAll( own );
        CollectModuleLibrariesNodeVisitor visitor = new CollectModuleLibrariesNodeVisitor(
            project.getRuntimeArtifacts(), examinerCache, getLog(), treeroot, useOSGiDependencies );
        treeroot.accept( visitor );
        Map<String, List<Artifact>> modules = visitor.getDeclaredArtifacts();
        Map<Artifact, Set<String>> moduleAllClasses = new HashMap<Artifact, Set<String>>();

        for ( ModuleWrapper wr : moduleArtifacts )
        {
            if ( modules.containsKey( wr.artifact.getDependencyConflictId() ) )
            {
                ExamineManifest man = examinerCache.get( wr.artifact );
                List<Artifact> arts = modules.get( wr.artifact.getDependencyConflictId() );
                Set<String>[] classes = visibleModuleClasses( arts, man, wr.dependency, projectCodeNameBase );
                deps.removeAll( classes[0] );
                moduleAllClasses.put( wr.artifact, classes[1] );
            }
        }

        //now we have the classes that are not in public packages of declared modules,
        //but are being used
        if ( !deps.isEmpty() )
        {
            Map<String, List<Artifact>> transmodules = visitor.getTransitiveArtifacts();
            for ( ModuleWrapper wr : moduleArtifacts )
            {
                if ( transmodules.containsKey( wr.artifact.getDependencyConflictId() ) )
                {
                    ExamineManifest man = examinerCache.get( wr.artifact );
                    List<Artifact> arts = transmodules.get( wr.artifact.getDependencyConflictId() );
                    Set<String>[] classes = visibleModuleClasses( arts, man, wr.dependency, projectCodeNameBase );
                    classes[0].retainAll( deps );
                    if ( classes[0].size() > 0 )
                    {
                        getLog().error(
                            "Project uses classes from transitive module " + wr.artifact.getId() + " which will not be accessible at runtime." );
                        deps.removeAll( classes[0] );
                    }
                    classes[1].retainAll( deps );
                    if (classes[1].size() > 0)
                    {
                        getLog().info( "Private classes referenced in transitive module: " + Arrays.toString( classes[1].toArray() ) );
                        getLog().error(
                            "Project depends on packages not accessible at runtime in transitive module " + wr.artifact.getId() + " which will not be accessible at runtime." );
                        deps.removeAll( classes[1] );
                    }
                }
            }
            for ( Artifact a : moduleAllClasses.keySet() )
            {
                List<String> strs = new ArrayList<String>(deps);
                if ( deps.removeAll( moduleAllClasses.get( a ) ) )
                {
                    strs.retainAll( moduleAllClasses.get( a ) );
                    getLog().info( "Private classes referenced in module: " + Arrays.toString( strs.toArray() ) );
                    getLog().error( "Project depends on packages not accessible at runtime in module " + a.getId() );
                }
            }
            if ( verifyRuntime.equalsIgnoreCase( FAIL ) )
            {
                if ( !deps.isEmpty() )
                {
                    throw new MojoFailureException( "Uncategorized problems with NetBeans dependency verification (maybe MNBMODULE-102): " + deps );
                }
                else
                {
                    throw new MojoFailureException( "See above for failures in runtime NetBeans dependencies verification." );
                }
            }
        }
    }

    /**
     * The current projects's dependencies, includes classes used in teh module itself
     * and the classpath libraries as well.
     * @param project
     * @param libraries
     * @return
     * @throws java.io.IOException
     */
    private Set<String> buildProjectDependencyClasses( MavenProject project, List<Artifact> libraries )
        throws IOException
    {
        Set<String> dependencyClasses = new HashSet<String>();

        String outputDirectory = project.getBuild().getOutputDirectory();
        dependencyClasses.addAll( buildDependencyClasses( outputDirectory ) );

        for ( Artifact lib : libraries )
        {
            dependencyClasses.addAll( buildDependencyClasses( lib.getFile().getAbsolutePath() ) );
        }
        return dependencyClasses;
    }

    @SuppressWarnings( "unchecked" )
    private Set<String> projectModuleOwnClasses( MavenProject project, List<Artifact> libraries )
        throws IOException
    {
        Set<String> projectClasses = new HashSet<String>();
        DefaultClassAnalyzer analyzer = new DefaultClassAnalyzer();

        String outputDirectory = project.getBuild().getOutputDirectory();
        URL fl = new File( outputDirectory ).toURI().toURL();
        projectClasses.addAll( analyzer.analyze( fl ) );

        for ( Artifact lib : libraries )
        {
            URL url = lib.getFile().toURI().toURL();
            projectClasses.addAll( analyzer.analyze( url ) );
        }

        return projectClasses;
    }

    /**
     * complete list of classes on project runtime classpath (excluding
     * jdk bit)
     * @param project
     * @return
     * @throws java.io.IOException
     */
    @SuppressWarnings( "unchecked" )
    private Set<String> allProjectClasses( MavenProject project )
        throws IOException
    {
        Set<String> projectClasses = new HashSet<String>();
        DefaultClassAnalyzer analyzer = new DefaultClassAnalyzer();

        String outputDirectory = project.getBuild().getOutputDirectory();
        URL fl = new File( outputDirectory ).toURI().toURL();
        projectClasses.addAll( analyzer.analyze( fl ) );

        List<Artifact> libs = project.getRuntimeArtifacts();

        for ( Artifact lib : libs )
        {
            URL url = lib.getFile().toURI().toURL();
            projectClasses.addAll( analyzer.analyze( url ) );
        }

        return projectClasses;
    }

    private Set<String>[] visibleModuleClasses( List<Artifact> moduleLibraries,
        ExamineManifest manifest, Dependency dep, String projectCodeNameBase )
        throws IOException, MojoFailureException
    {
        Set<String> moduleClasses = new HashSet<String>();
        Set<String> visibleModuleClasses = new HashSet<String>();
        DefaultClassAnalyzer analyzer = new DefaultClassAnalyzer();
        String type = dep.getType();
        if ( dep.getExplicitValue() != null )
        {
            if ( dep.getExplicitValue().contains( "=" ) )
            {
                type = "impl";
            }
        }
        if ( type == null || "loose".equals( type ) )
        {
            type = "spec";
        }

        for ( Artifact lib : moduleLibraries )
        {
            URL url = lib.getFile().toURI().toURL();
            moduleClasses.addAll( analyzer.analyze( url ) );
        }

        if ( "spec".equals( type ) )
        {
            String cnb = stripVersionFromCodebaseName( projectCodeNameBase );
            if ( manifest.hasFriendPackages() && !manifest.getFriends().contains( cnb ) )
            {
                if ( verifyRuntime.equalsIgnoreCase( FAIL ) )
                {
                    throw new MojoFailureException(
                        "Module dependency has friend dependency on " + manifest.getModule() + "but is not listed as friend." );
                }
                else
                {
                    getLog().warn(
                        "Module dependency has friend dependency on " + manifest.getModule() + "but is not listed as friend." );
                }
            }
            List<Pattern> compiled = createCompiledPatternList( manifest.getPackages() );
            if ( useOSGiDependencies && manifest.isOsgiBundle() )
            {
                // TODO how to extract the public packages in osgi bundles easily..
                compiled = Collections.singletonList( Pattern.compile( "(.+)" ) );
            }
            for ( String clazz : moduleClasses )
            {
                for ( Pattern patt : compiled )
                {
                    if ( patt.matcher( clazz ).matches() ) 
                    {
                        visibleModuleClasses.add( clazz );
                        break;
                    }
                }
            }

        }
        else if ( "impl".equals( type ) )
        {
            visibleModuleClasses.addAll( moduleClasses );
        }
        else
        {
            //HUH?
            throw new MojoFailureException( "Wrong type of module dependency " + type );
        }

        return new Set[]
            {
                visibleModuleClasses,
                moduleClasses
            };
    }

    static List<Pattern> createCompiledPatternList( List<String> packages )
    {
        List<Pattern> toRet = new ArrayList<Pattern>();
        for ( String token : packages )
        {
            if ( token.endsWith( ".**" ) )
            {
                String patt = "^" + Pattern.quote( token.substring( 0, token.length() - 2 ) ) + "(.+)";
                toRet.add( 0, Pattern.compile( patt ) );
            }
            else
            {
                String patt = "^" + Pattern.quote( token.substring( 0, token.length() - 1 ) ) + "([^\\.]+)";
                toRet.add( Pattern.compile( patt ) );
            }
        }
        return toRet;
    }

    @SuppressWarnings( "unchecked" )
    private Set<String> buildDependencyClasses( String path )
        throws IOException
    {
        URL url = new File( path ).toURI().toURL();
        ASMDependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();
        return dependencyAnalyzer.analyze( url );
    }
}
