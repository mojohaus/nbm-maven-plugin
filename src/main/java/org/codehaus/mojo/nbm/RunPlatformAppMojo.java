/* ==========================================================================
 * Copyright 2007 Mevenide Team
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Run a branded application on top of NetBeans Platform with additional custom module clusters, 
 * to be used in conjunction with nbm:cluster.
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal run-platform
 * @aggregator
 * @requiresDependencyResolution runtime
 *
 */
public class RunPlatformAppMojo extends AbstractMojo {
    /**
     * directory where the module(s)' netbeans cluster(s) are located.
     * is related to nbm:cluster goal.
     * @parameter default-value="${project.build.directory}/netbeans_clusters"
     * @required
     */
    protected String clusterBuildDir;

    /**
     * directory where the the NetBeans platform/IDE installation is,
     * denotes the root directory of netbeans installation.
     * @parameter expression="${netbeans.installation}"
     * @required
     */
    protected String netbeansInstallation;
    
    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;
    
    /**
     * netbeans user directory for the executed instance.
     * @parameter default-value="${project.build.directory}/userdir" expression="${netbeans.userdir}"
     * @required
     */
    protected String netbeansUserdir;
    
    /**
     * additional command line arguments. Eg. 
     * -J-Xdebug -J-Xnoagent -J-Xrunjdwp:transport=dt_socket,suspend=n,server=n,address=8888
     * can be used to debug the IDE.
     * @parameter expression="${netbeans.run.params}"
     */
    protected String additionalArguments;
    
    /**
     * List of enabled clusters. At least platform cluster needs to be
     * included.
     * @parameter
     * @required
     */
    protected List/*<String>*/ enabledClusters;
    
//    /**
//     * List of disabled modules in the enabled clusters. 
//     * Allows for fine-tuned configuration of the application.
//     * @parameter
//     */
//    protected List/*<String>*/ disabledModules;
//    
//   TODO no support for disabling individual modules,
//        should look something like this..   
//    
//        <createmodulexml xmldir="${cluster}/config/Modules">
//            <hidden dir="${netbeans.dest.dir}">
//                <custom classpath="${harness.dir}/tasks.jar" classname="org.netbeans.nbbuild.ModuleSelector">
//                    <param name="excludeModules" value="${disabled.modules}"/>
//                    <param name="excluded" value="true"/>
//                </custom>
//            </hidden>
//        </createmodulexml>
    
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     * @throws org.apache.maven.plugin.MojoFailureException 
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        File userDir = new File(netbeansUserdir);
        userDir.mkdirs();
        
        File clusterRoot = new File(clusterBuildDir);
        if (!clusterRoot.exists()) {
            throw new MojoExecutionException("There are no additional clusters in " + clusterBuildDir);
        }
        
        // make sure the final cluster list is numbered.
        Map matchers = new HashMap();
        if (enabledClusters == null) {
            // fallback default.
            enabledClusters = new ArrayList();
        }
        Iterator it = enabledClusters.iterator();
        while (it.hasNext()) {
            String clus = (String)it.next();
            // we don't want the plaform in the list of clusters..
            if (!clus.startsWith("platform")) {
                matchers.put(clus, Pattern.compile(clus + "(\\d)*"));
            }
        }
        
        /** list of files **/
        List realEnabledClusters = new ArrayList();
        File platform = null;
        // check installation..
        File[] fls = new File(netbeansInstallation).listFiles();
        if (fls == null) {
            //null means not existing folder
            throw new MojoExecutionException("Non-existing NetBeans installation at " + netbeansInstallation);
        }
        for (int i = 0; i < fls.length; i++) {
            if (fls[i].isDirectory()) {
                String folderName = fls[i].getName();
                if (folderName.startsWith("platform")) {
                    platform = fls[i];
                }
                Iterator it2 = matchers.entrySet().iterator();
                while (it2.hasNext()) {
                    Map.Entry en = (Map.Entry)it2.next();
                    Pattern match = (Pattern) en.getValue();
                    if (match.matcher(folderName).matches()) {
                        realEnabledClusters.add(fls[i]);
                        it2.remove();
                        break;
                    }
                }
            }
        }
        
        // check our module clusters.
        fls = clusterRoot.listFiles();
        for (int i = 0; i < fls.length; i++) {
            if (fls[i].isDirectory()) {
                String folderName = fls[i].getName();
                Iterator it2 = matchers.entrySet().iterator();
                while (it2.hasNext()) {
                    Map.Entry en = (Map.Entry)it2.next();
                    Pattern match = (Pattern) en.getValue();
                    if (match.matcher(folderName).matches()) {
                        // add our clusters at the start..
                        realEnabledClusters.add(0, fls[i]);
                        it2.remove();
                        break;
                    }
                }
            }
        }
        
        if (platform == null) {
            throw new MojoExecutionException("Cannot find platform* cluster within NetBeans installation at " + netbeansInstallation);
        }
        // add all we could not match in platform and cross fingers.
        if (matchers.size() > 0) {
            getLog().error("Cannot find following clusters, ignoring:");
            it = matchers.keySet().iterator();
            while (it.hasNext()) {
                getLog().error("      " + it.next());
            }
        }
        
        // create cluster list;
        it = realEnabledClusters.iterator();
        String clustersString = "";
        while (it.hasNext()) {
            clustersString = clustersString + ":" + ((File)it.next()).getAbsolutePath();
        }
        clustersString = clustersString.substring(1);
        
        boolean windows = Os.isFamily("windows");
        
        Commandline cmdLine = new Commandline();
        File exec = windows ? new File(platform, "lib\\nbexec.exe") : 
                              new File(platform, "lib/nbexec");
        cmdLine.setExecutable(exec.getAbsolutePath());
        
        try {
            String[] args = new String[] {
                //TODO --jdkhome
                "--userdir",
                Commandline.quoteArgument(userDir.getAbsolutePath()),
                "-J-Dnetbeans.logger.console=true",
                "-J-ea",
                "--branding",
                brandingToken,
                "--clusters",
                Commandline.quoteArgument(clustersString)
            };
            cmdLine.addArguments(args);
            cmdLine.addArguments(cmdLine.translateCommandline(additionalArguments));
            getLog().info("Executing: " + cmdLine.toString());
            StreamConsumer out = new StreamConsumer() {
                public void consumeLine(String line) {
                    getLog().info(line);
                }
            };
            CommandLineUtils.executeCommandLine(cmdLine, out, out);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed executing NetBeans", e);
        }
    }

}
