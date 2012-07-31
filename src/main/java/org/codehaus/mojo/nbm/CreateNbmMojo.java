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
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Organization;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
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
 * Create the NetBeans module artifact (nbm file), part of "nbm" lifecycle/packaging.
 * <p/>
 *
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 */
@Mojo(name="nbm", 
        requiresProject=true, 
        requiresDependencyResolution= ResolutionScope.RUNTIME, 
        defaultPhase= LifecyclePhase.PACKAGE )
public class CreateNbmMojo
        extends CreateNetBeansFileStructure
        implements Contextualizable
{

    /**
     * keystore location for signing the nbm file
     */
    @Parameter(property="keystore")
    private String keystore;
    /**
     * keystore password
     */
    @Parameter(property="keystorepass")
    private String keystorepassword;
    /**
     * keystore alias
     */
    @Parameter(property="keystorealias")
    private String keystorealias;

    /**
     * Boolean parameter denoting if creation of NBM file shall be skipped or not.
     * If skipped, just the expanded directory for cluster is created
     * @since 3.0
     */
    @Parameter(defaultValue="false", property="maven.nbm.skip")
    private boolean skipNbm;
    
    /**
     * if true, upon installing the NBM the platform app/IDE restart is requested. Not necessary in most cases.
     * @since 3.8
     */
    @Parameter(defaultValue="false")
    private boolean requiresRestart;
    
    /**
     * Get homepage URL of the module. Is accessible from NetBeans
     * UI upon installation, should point to place with additional
     * information about the functionality. 
     * @since 3.8
     */
    @Parameter(defaultValue="${project.url}")
    private String homePageUrl;

    // <editor-fold defaultstate="collapsed" desc="Component parameters">

    /**
     * Contextualized.
     */
    private PlexusContainer container;

    @Component
    private ArtifactFactory artifactFactory;
    /**
     * Used for attaching the artifact in the project
     */
    @Component
    private MavenProjectHelper projectHelper;

    // end of component params custom code folding
    // </editor-fold>

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skipNbm )
        {
            getLog().info( "Skipping generation of NBM file." );
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

        nbmTask.setModule( "modules" + File.separator + moduleJarName + ".jar" );
        boolean reqRestart = requiresRestart;
        if (!reqRestart && module.isRequiresRestart()) {
            reqRestart = module.isRequiresRestart();
            getLog().warn( "Module descriptor's requiresRestart field is deprecated, use plugin's configuration in pom.xml");
        }
        nbmTask.setNeedsrestart( Boolean.toString( reqRestart ) );
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
                getLog().warn( "Cannot find keystore file at " + ks.getAbsolutePath() );
            }
            else
            {
                Signature sig = nbmTask.createSignature();
                sig.setKeystore( ks );
                sig.setAlias( keystorealias );
                sig.setStorepass( keystorepassword );
                getLog().debug( "Setup the Ant task to sign the NBM file." );
            }
        }
        else if ( keystore != null || keystorepassword != null || keystorealias != null )
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
                getLog().warn( "Cannot find license file at " + lf.getAbsolutePath() );
            }
            else
            {
                Blurb lb = nbmTask.createLicense();
                lb.setFile( lf );
                lb.addText( licenseName );
            }
        }
        else if ( licenseName != null || licenseFile != null )
        {
            getLog().warn(
                    "To add a license to the nbm, you need to specify both licenseName and licenseFile parameters" );
        }
        else
        {
            Blurb lb = nbmTask.createLicense();
            lb.addText( createDefaultLicenseHeader() );
            lb.addText( createDefaultLicenseText() );
        }
        String hpUrl = homePageUrl;
        if (module.getHomepageUrl() != null) {
            getLog().warn( "Module descriptor's homePageUrl field is deprecated, use plugin's configuration in pom.xml");
            hpUrl = module.getHomepageUrl();
        }
        if ( hpUrl != null )
        {
            nbmTask.setHomepage( hpUrl );
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
                    dist =
                        module.getDistributionUrl() + ( module.getDistributionUrl().endsWith( "/" ) ? "" : "/" )
                            + nbmFile.getName();
                }
            }
            else
            {
                Artifact art = artifactFactory.createArtifact(
                        project.getGroupId(), project.getArtifactId(),
                        project.getVersion(), null, "nbm-file" );

                dist =
                    distRepository.getUrl() + ( distRepository.getUrl().endsWith( "/" ) ? "" : "/" )
                        + distRepository.pathOf( art );

            }
            nbmTask.setDistribution( dist );
        }
        else
        {
            nbmTask.setDistribution( nbmFile.getName() );
        }
        nbmTask.setTargetcluster( cluster );
        try
        {
            nbmTask.execute();
        }
        catch ( BuildException e )
        {
            throw new MojoExecutionException( "Cannot Generate nbm file:" + e.getMessage(), e );
        }
        try
        {
            File nbmfile = new File( buildDir, nbmFile.getName() );
            FileUtils.getFileUtils().copyFile( nbmFile, nbmfile );
            projectHelper.attachArtifact( project, "nbm-file", null, nbmfile );
        }
        catch ( IOException ex )
        {
            throw new MojoExecutionException( "Cannot copy nbm to build directory", ex );
        }
    }

    public void contextualize( Context context )
            throws ContextException
    {
        this.container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    private String createDefaultLicenseHeader()
    {
        String organization = "";
        Organization org = project.getOrganization();
        if (org != null) {
            organization = org.getName();
}
        if (organization == null) {
            List devs = project.getDevelopers();
            if (devs.size() > 0) {
                Iterator dvs = devs.iterator();
                String devsString = "";
                while (dvs.hasNext()) {
                    Developer d = ( Developer )dvs.next();
                    devsString = devsString + "," + d.getName() != null ? d.getName() : d.getId();
                }
                organization = devsString.substring( 1 );    
            }
        }
        if (organization == null) {
            organization = ""; //what's a good default value?
        }
        String date = "";
        if (project.getInceptionYear() != null) {
            date = project.getInceptionYear();
        }
        String year = Integer.toString( Calendar.getInstance().get( Calendar.YEAR ));
        if (!year.equals( date ) ) {
            date = date.length() == 0 ? year : date + "-" + year;
        }
        return "Copyright " + organization + " " + date;
    }
    
    private String createDefaultLicenseText() {
        String toRet = "License terms:\n";
        
        List licenses = project.getLicenses();
        if (licenses != null && licenses.size() > 0) {
            Iterator lic = licenses.iterator();
            while (lic.hasNext()) {
                License ll = ( License )lic.next();
                
                if (ll.getName() != null) {
                   toRet = toRet + ll.getName() + " - "; 
                }
                if (ll.getUrl() != null) {
                    toRet = toRet + ll.getUrl();
                }
                if (lic.hasNext()) {
                    toRet = toRet + ",\n";
                }
            }
        } else {
           toRet = toRet + "Unknown";
        }
        return toRet;
    }
}
