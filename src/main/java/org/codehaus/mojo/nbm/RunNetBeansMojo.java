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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Run NetBeans IDE with additional custom module clusters, 
 * to be used in conjunction with nbm:cluster.
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal run-ide
 * @aggregator
 * @requiresDependencyResolution runtime
 *
 */
public class RunNetBeansMojo extends AbstractMojo {
    /**
     * directory where the module(s)' netbeans cluster(s) are located.
     * is related to nbm:cluster goal.
     * @parameter default-value="${project.build.directory}/netbeans_clusters"
     * @required
     */
    protected String clusterBuildDir;

    /**
     * directory where the the netbeans platform/IDE installation is,
     * denotes the root directory of netbeans installation.
     * @parameter expression="${netbeans.installation}"
     * @required
     */
    protected String netbeansInstallation;
    
    
    /**
     * netbeans user directory for the executed instance.
     * @parameter default-value="${project.build.directory}/userdir"
     * @required
     */
    protected String netbeansUserdir;
    
    /**
     * additional command line arguments. Eg. 
     * -J-Xdebug -J-Xnoagent -J-Xrunjdwp:transport=dt_socket,suspend=n,server=n,address=8888
     * can be used to debug the IDE.
     * @parameter
     */
    protected String additionalArguments;
    
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
        List clusters = new ArrayList();
        File[] fls = clusterRoot.listFiles();
        for (int i = 0; i < fls.length; i++) {
            if (fls[i].isDirectory()) {
                clusters.add(fls[i]);
            }
        }
        
        // write netbeans.conf file with cluster information...
        File etc = new File(userDir, "etc");
        etc.mkdirs();
        StringBuffer buff = new StringBuffer();
        buff.append("netbeans_extraclusters=\"");
        Iterator it = clusters.iterator();
        while (it.hasNext()) {
            File cluster = (File)it.next();
            buff.append(cluster.getAbsolutePath());
            buff.append(":");
        }
        buff.deleteCharAt(buff.lastIndexOf(":"));
        buff.append("\"");
        StringReader sr = new StringReader(buff.toString());
        File confFile  = new File(etc, "netbeans.conf");
        FileOutputStream conf = null;
        try {
            conf = new FileOutputStream(confFile);
            IOUtil.copy(sr, conf);
        } catch (IOException ex) {
            throw new MojoExecutionException("Error writing " + confFile, ex);
        }
        finally {
            IOUtil.close(conf);
        }
        
        boolean windows = Os.isFamily("windows");
        Commandline cmdLine = new Commandline();
        File exec = windows ? new File(netbeansInstallation, "bin\\nb.exe") : 
                              new File(netbeansInstallation, "bin/netbeans");
        cmdLine.setExecutable(exec.getAbsolutePath());
        
        try {
            String[] args = new String[] {
                //TODO --jdkhome
                "--userdir",
                Commandline.quoteArgument(userDir.getAbsolutePath()),
                "-J-Dnetbeans.logger.console=true",
                "-J-ea",
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
