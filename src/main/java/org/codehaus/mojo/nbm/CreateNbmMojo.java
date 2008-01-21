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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.util.FileUtils;
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
        extends CreateNetbeansFileStructureMojo {
    
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
     * Used for attaching the artifact in the project
     *
     * @component
     */
    private MavenProjectHelper projectHelper;
    
    
    
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!"nbm".equals(project.getPackaging())) {
            getLog().debug("Skipping " + project.getId() + " skipped, not NBM packaging");
            return;
        }
        super.execute();
        
        // 3. generate nbm
        File nbmFile = new File( nbmBuildDir, finalName + ".nbm");
        MakeNBM nbmTask = (MakeNBM)antProject.createTask("makenbm");
        nbmTask.setFile(nbmFile);
        nbmTask.setProductDir(clusterDir);
        
        nbmTask.setModule(moduleLocation + File.separator + moduleJarName + ".jar");
        nbmTask.setNeedsrestart(Boolean.toString(module.isRequiresRestart()));
        String moduleAuthor = module.getAuthor();
        if (moduleAuthor == null) {
            moduleAuthor = project.getOrganization() != null ? project.getOrganization().getName() : null;
        }
        nbmTask.setModuleauthor(moduleAuthor);
        if (keystore != null && keystorealias != null && keystorepassword != null) {
            File ks = new File(keystore);
            if (!ks.exists()) {
                getLog().warn("Cannot find keystore file at " + ks.getAbsolutePath());
            } else {
                Signature sig = nbmTask.createSignature();
                sig.setKeystore(ks);
                sig.setAlias(keystorealias);
                sig.setStorepass(keystorepassword);
            }
        } else if (keystore != null || keystorepassword != null || keystorealias != null) {
            getLog().warn("If you want to sign the nbm file, you need to define all three keystore related parameters.");
        }
        String licenseName = module.getLicenseName();
        String licenseFile = module.getLicenseFile();
        if (licenseName != null && licenseFile != null) {
            File lf = new File(project.getBasedir(), licenseFile);
            if (!lf.exists() || !lf.isFile()) {
                getLog().warn("Cannot find license file at " + lf.getAbsolutePath());
            } else {
                Blurb lb = nbmTask.createLicense();
                lb.setFile(lf);
                lb.setName(licenseName);
            }
        } else if (licenseName != null || licenseFile != null) {
            getLog().warn("To add a license to the nbm, you need to specify both licenseName and licenseFile parameters");
        } else {
            Blurb lb = nbmTask.createLicense();
            lb.addText("<Here comes the license>");
            lb.setName("Unknown license agreement");
        }
        String homePageUrl = module.getHomepageUrl();
        if (homePageUrl == null) {
            homePageUrl = project.getUrl();
        }
        if (homePageUrl != null) {
            nbmTask.setHomepage(homePageUrl);
        }
        if (module.getDistributionUrl() != null) {
            nbmTask.setDistribution(module.getDistributionUrl() + (module.getDistributionUrl().endsWith("/") ? "" : "/") + nbmFile.getName());
        } else {
            getLog().warn("You don't define distribution URL in the netbeans module descriptor. That's ok for local installation but f you want to create an autoupdate site, you have to define this property.");
            nbmTask.setDistribution(project.getUrl() + (project.getUrl() != null && project.getUrl().endsWith("/") ? "" :"/") + nbmFile.getName());
            getLog().warn("  Using default value for distribution URL: " + nbmTask.getDescription());
        }
        try {
            nbmTask.execute();
        } catch (BuildException e) {
            getLog().error( "Cannot Generate nbm file" );
            throw new MojoExecutionException( e.getMessage(), e );
        }
        try {
            File nbmfile = new File(buildDir, nbmFile.getName());
            FileUtils.newFileUtils().copyFile(nbmFile, nbmfile);
            projectHelper.attachArtifact( project, "nbm-file", null, nbmfile);
            
        } catch (IOException ex) {
            getLog().error("Cannot copy nbm to build directory");
            throw new MojoExecutionException("Cannot copy nbm to build directory", ex);
        }
    }
}
