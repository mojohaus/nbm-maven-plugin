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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.nbm.model.Dependency;
import org.codehaus.mojo.nbm.model.NetbeansModule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.codehaus.plexus.util.IOUtil;

/**
 * Goal for updating the artifact jar with netbeans specific entries, part of "nbm" lifecycle/packaging.
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal manifest
 * @phase generate-resources
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
    protected String nbmBuildDir;
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
     * eg. if your codenamebase is "org.netbeans.modules.apisupport", the the actual docs
     * files shall go to ${basedir}/src/main/javahelp/org/netbeans/modules/apisupport/docs
     * <br/>

     * Additionally if you provide docs, you will need to place the JavaHelp jar on the classpath 
     * of the nbm-plugin for the project. The jar is to be found in the netbeans/harness directory 
     * of any NetBeans installation. <br/>
    <code>
    &lt;plugin&gt;<br/>
    &lt;groupId&gt;org.codehaus.mojo&lt;/groupId&gt;<br/>
    &lt;artifactId&gt;nbm-maven-plugin&lt;/artifactId&gt;<br/>
    &lt;extensions&gt;true&lt;/extensions&gt;<br/>
    &lt;dependencies&gt;<br/>
    &lt;dependency&gt;<br/>
    &lt;groupId&gt;javax.help&lt;/groupId&gt;<br/>
    &lt;artifactId&gt;search&lt;/artifactId&gt;<br/>
    &lt;version&gt;2.0&lt;/version&gt;<br/>
    &lt;!--scope&gt;system&lt;/scope&gt;<br/>
    &lt;systemPath&gt;/home/mkleint/netbeans/harness/jsearch-2.0_04.jar&lt;/systemPath--&gt;<br/>
    &lt;/dependency&gt;<br/>
    &lt;/dependencies&gt;<br/>
    &lt;/plugin&gt;<br/>
    <br/>
    </code>
     *
     * @parameter default-value="${basedir}/src/main/javahelp"
     * @since 2.7
     */
    protected File nbmJavahelpSource;
    
    /**
     * Path to manifest file that will be used as base for the 
     *
     * @parameter default-value="${basedir}/src/main/nbm/manifest.mf"
     * @required
     */
    private File sourceManifestFile;
    
    /**
     * Path to the generated MANIFEST file to use. It will be used by jar:jar plugin.
     *
     * @parameter expression="${project.build.outputDirectory}/META-INF/MANIFEST.MF"
     * @readonly
     * @required
     */
    private File targetManifestFile;

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
     * @required
     * @readonly
     */
    private ArtifactFactory artifactFactory;

    /**
     * The artifact metadata source to use.
     *
     * @component
     * @required
     * @readonly
     */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * The artifact collector to use.
     *
     * @component
     * @required
     * @readonly
     */
    private ArtifactCollector artifactCollector;

    /**
     * The dependency tree builder to use.
     *
     * @component
     * @required
     * @readonly
     */
    private DependencyTreeBuilder dependencyTreeBuilder;

    public void execute()
            throws MojoExecutionException
    {
        NetbeansModule module;
        if ( descriptor != null && descriptor.exists() )
        {
            module = readModuleDescriptor( descriptor );
        } else
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
        File nbmManifest = (module.getManifest() != null ? new File(
                project.getBasedir(), module.getManifest() ) : null);
        if ( nbmManifest != null && nbmManifest.exists() )
        {
            //deprecated, but if actually defined, will use it.
            specialManifest = nbmManifest;
        }
        ExamineManifest examinator = new ExamineManifest( getLog() );
        if ( specialManifest != null )
        {
            examinator.setManifestFile( specialManifest );
            examinator.checkFile();
        } else
        {
//            examinator.setJarFile( jarFile );
        }

        getLog().info( "NBM Plugin generates manifest" );

        Manifest manifest = null;
        if ( specialManifest != null )
        {
            Reader reader = null;
            try
            {
                reader = new InputStreamReader( new FileInputStream(
                        specialManifest ) );
                manifest = new Manifest( reader );
            } catch ( IOException exc )
            {
                manifest = new Manifest();
                getLog().warn( "Error reading manifest at " + specialManifest, exc );
            } catch ( ManifestException ex )
            {
                getLog().warn( "Error reading manifest at " + specialManifest, ex );
                manifest = new Manifest();
            } finally
            {
                IOUtil.close( reader );
            }
        } else
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
//     create a timestamp value for OpenIDE-Module-Build-Version: manifest entry
        String timestamp = new SimpleDateFormat( "yyyyMMddhhmm" ).format(
                new Date() );
        conditionallyAddAttribute( mainSection, "OpenIDE-Module-Build-Version",
                timestamp );
        conditionallyAddAttribute( mainSection, "OpenIDE-Module", moduleName );

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
                    "OpenIDE-Module-Short-Description", project.getDescription() );
            conditionallyAddAttribute( mainSection,
                    "OpenIDE-Module-Long-Description", project.getDescription() );
        }
        getLog().debug( "module =" + module );
        if ( module != null )
        {
            DependencyNode treeroot = createDependencyTree(project, dependencyTreeBuilder, localRepository, artifactFactory, artifactMetadataSource, artifactCollector, "compile");
            Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
            List<Artifact> libArtifacts = getLibraryArtifacts(treeroot, module, project.getRuntimeArtifacts(), examinerCache, getLog());
            List<ModuleWrapper> moduleArtifacts = getModuleDependencyArtifacts(treeroot, module, project, examinerCache, libArtifacts, getLog());
            String classPath = "";
            String dependencies = "";
            String depSeparator = " ";

            for (Artifact a : libArtifacts) {
                classPath = classPath + " ext/" + a.getFile().getName();
            }

            for (ModuleWrapper wr : moduleArtifacts) {
                Dependency dep = wr.dependency;
                Artifact artifact = wr.artifact;
                ExamineManifest depExaminator = examinerCache.get(artifact);
                String type = dep.getType();
                String depToken = dep.getExplicitValue();
                if ( depToken == null )
                {
                    if ( "loose".equals( type ) )
                    {
                        depToken = depExaminator.getModule();
                    } else if ( "spec".equals( type ) )
                    {
                        depToken = depExaminator.getModule() + " > " +
                                (depExaminator.isNetbeansModule() ? depExaminator.getSpecVersion() : AdaptNbVersion.adaptVersion(
                                depExaminator.getSpecVersion(),
                                AdaptNbVersion.TYPE_SPECIFICATION ));
                    } else if ( "impl".equals( type ) )
                    {
                        depToken = depExaminator.getModule() + " = " +
                                (depExaminator.isNetbeansModule() ? depExaminator.getImplVersion() : AdaptNbVersion.adaptVersion(
                                depExaminator.getImplVersion(),
                                AdaptNbVersion.TYPE_IMPLEMENTATION ));
                    } else
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
                } else
                {
                    dependencies = dependencies + depSeparator + depToken;
                    depSeparator = ", ";
                }
            }

            if ( hasJavaHelp && nbmJavahelpSource.exists() )
            {
                String moduleJarName = moduleName.replace( '.', '-' );
                // it can happen the moduleName is in format org.milos/1
                int index = moduleJarName.indexOf( '/' );
                if ( index > -1 )
                {
                    moduleJarName = moduleJarName.substring( 0, index ).trim();
                }
                classPath = classPath + " docs/" + moduleJarName + ".jar";
            }

            if ( classPath.length() > 0 )
            {
                conditionallyAddAttribute( mainSection, "Class-Path",
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
            writer = new PrintWriter( targetManifestFile, "UTF-8"); //TODO really UTF-8??
            manifest.write( writer );
        } catch ( IOException ex )
        {
            throw new MojoExecutionException( ex.getMessage(), ex );
        } finally {
            IOUtil.close( writer );
        }
    }

    private void conditionallyAddAttribute( Manifest.Section section, String key, String value )
    {
        Manifest.Attribute attr = section.getAttribute( key );
        if ( attr == null )
        {
            attr = new Manifest.Attribute();
            attr.setName( key );
            attr.setValue( value != null ? value : "To be set." );
            try
            {
                section.addConfiguredAttribute( attr );
            } catch ( ManifestException ex )
            {
                getLog().error( "Cannot update manifest (key=" + key + ")" );
                ex.printStackTrace();
            }
        }
    }
}
