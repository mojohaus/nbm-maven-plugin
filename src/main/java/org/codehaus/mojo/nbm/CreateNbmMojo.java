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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.util.FileUtils;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.netbeans.nbbuild.MakeNBM;
import org.netbeans.nbbuild.MakeNBM.Blurb;
import org.netbeans.nbbuild.MakeNBM.Signature;

/**
 * Create the Netbeans module artifact (nbm file), part of "nbm" lifecycle/packaging.
 * <p/>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal nbm
 * @phase package
 * @requiresDependencyResolution runtime
 * @requiresProject
 *
 */
public class CreateNbmMojo
        extends CreateNetbeansFileStructure
        implements Contextualizable
{

    /**
     * keystore location for signing the nbm file
     * @parameter expression="${keystore}"
     */
    private String keystore;
    /**
     * keystore password
     * @parameter expression="${keystorepass}"
     */
    private String keystorepassword;
    /**
     * keystore alias
     * @parameter expression="${keystorealias}"
     */
    private String keystorealias;

    /**
     * Boolean parameter denoting if creation of NBM file shall be skipped or not.
     * If skipped, just the expanded directory for cluster is created
     * @parameter expression="${maven.nbm.skip}" default-value="false"
     * @since 3.0
     */
    private boolean skipNbm;

    // <editor-fold defaultstate="collapsed" desc="Component parameters">

    /**
     * Contextualized.
     */
    private PlexusContainer container;
    /**
     * @component
     * @readonly
     */
    private ArtifactFactory artifactFactory;
    /**
     * Used for attaching the artifact in the project
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    // end of component params custom code folding
    // </editor-fold>

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if ( skipNbm )
        {
            getLog().info( "Skipping generation of NBM file.");
            return;
        }

        if ( "pom".equals( project.getPackaging() ) )
        {
            getLog().info(
                    "Skipping " + project.getId() + ", no nbm:nbm execution for 'pom' packaging" );
            return;
        }
        super.execute();


        // 3. generate nbm
        File nbmFile = new File( nbmBuildDir, finalName + ".nbm" );
        MakeNBM nbmTask = (MakeNBM) antProject.createTask( "makenbm" );
        nbmTask.setFile( nbmFile );
        nbmTask.setProductDir( clusterDir );

        nbmTask.setModule(
                "modules" + File.separator + moduleJarName + ".jar" );
        nbmTask.setNeedsrestart( Boolean.toString( module.isRequiresRestart() ) );
        String moduleAuthor = module.getAuthor();
        if ( moduleAuthor == null )
        {
            moduleAuthor = project.getOrganization() != null ? project.getOrganization().getName() : null;
        }
        nbmTask.setModuleauthor( moduleAuthor );
        if ( keystore != null && keystorealias != null && keystorepassword != null )
        {
            File ks = new File( keystore );
            if ( !ks.exists() )
            {
                getLog().warn(
                        "Cannot find keystore file at " + ks.getAbsolutePath() );
            } else
            {
                Signature sig = nbmTask.createSignature();
                sig.setKeystore( ks );
                sig.setAlias( keystorealias );
                sig.setStorepass( keystorepassword );
                getLog().debug( "Setup the Ant task to sign the NBM file.");
            }
        } else if ( keystore != null || keystorepassword != null || keystorealias != null )
        {
            getLog().warn(
                    "If you want to sign the nbm file, you need to define all three keystore related parameters." );
        }
        String licenseName = module.getLicenseName();
        String licenseFile = module.getLicenseFile();
        if ( licenseName != null && licenseFile != null )
        {
            File lf = new File( project.getBasedir(), licenseFile );
            if ( !lf.exists() || !lf.isFile() )
            {
                getLog().warn(
                        "Cannot find license file at " + lf.getAbsolutePath() );
            } else
            {
                Blurb lb = nbmTask.createLicense();
                lb.setFile( lf );
                lb.addText( licenseName );
            }
        } else if ( licenseName != null || licenseFile != null )
        {
            getLog().warn(
                    "To add a license to the nbm, you need to specify both licenseName and licenseFile parameters" );
        } else
        {
            Blurb lb = nbmTask.createLicense();
            lb.addText( "<Here comes the license>" );
            lb.addText( "Unknown license agreement" );
        }
        String homePageUrl = module.getHomepageUrl();
        if ( homePageUrl == null )
        {
            homePageUrl = project.getUrl();
        }
        if ( homePageUrl != null )
        {
            nbmTask.setHomepage( homePageUrl );
        }
        if ( module.getDistributionUrl() != null )
        {
            ArtifactRepository distRepository = CreateUpdateSiteMojo.getDeploymentRepository(
                    module.getDistributionUrl(), container, getLog() );
            String dist = null;
            if ( distRepository == null )
            {
                if ( !module.getDistributionUrl().contains( "::" ) )
                {
                    dist = module.getDistributionUrl() + (module.getDistributionUrl().endsWith(
                            "/" ) ? "" : "/") + nbmFile.getName();
                }
            } else
            {
                Artifact art = artifactFactory.createArtifact(
                        project.getGroupId(), project.getArtifactId(),
                        project.getVersion(), null, "nbm-file" );

                dist = distRepository.getUrl() + (distRepository.getUrl().endsWith( 
                        "/" ) ? "" : "/") + distRepository.pathOf( art );

            }
            nbmTask.setDistribution( dist );
        } else
        {
            nbmTask.setDistribution(nbmFile.getName() );
        }
        nbmTask.setTargetcluster( cluster );
        try
        {
            nbmTask.execute();
        } catch ( BuildException e )
        {
            throw new MojoExecutionException( "Cannot Generate nbm file:" + e.getMessage(), e );
        }
        try
        {
            File nbmfile = new File( buildDir, nbmFile.getName() );
            FileUtils.getFileUtils().copyFile( nbmFile, nbmfile );
            projectHelper.attachArtifact( project, "nbm-file", null, nbmfile );

        } catch ( IOException ex )
        {
            throw new MojoExecutionException(
                    "Cannot copy nbm to build directory", ex );
        }
    }

    public void contextualize( Context context )
            throws ContextException
    {
        this.container = (PlexusContainer) context.get(
                PlexusConstants.PLEXUS_KEY );
    }
}
