/*
 *  Copyright 2008 Johan AndrÃ©n.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.codehaus.mojo.nbm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.maven.plugin.AbstractMojo;
import org.codehaus.plexus.util.FileUtils;

/**
 * Common functionality for the application bundle mojos.
 * 
 * @author <a href="mailto:johan.andren@databyran.se">Johan AndrÃ©n</a>
 */
public abstract class AbstractDistributionMojo
        extends AbstractMojo
{

    /**
     * directory where the module(s)' netbeans cluster(s) are located.
     * is related to nbm:cluster goal.
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    protected File outputDirectory;
    /**
     * directory where the module(s)' netbeans cluster(s) are located.
     * is related to nbm:cluster goal.
     * @parameter default-value="${project.build.directory}/netbeans_clusters"
     * @required
     */
    protected File clusterBuildDir;
    /**
     * directory where the the NetBeans platform/IDE installation is,
     * denotes the root directory of netbeans installation.
     * @parameter expression="${netbeans.installation}"
     * @required
     */
    protected File netbeansInstallation;
    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;
    /**
     * additional command line arguments that the application should always
     * be run with. Will be placed in the etc/{brandingToken}.conf file
     * Eg. 
     * -J-Dnetbeans.winsys.no_toolbars=true -J-Xdebug -J-Xnoagent -J-Xrunjdwp:transport=dt_socket,suspend=n,server=n,address=8888
     * @parameter expression="${netbeans.default.options}"
     */
    protected String defaultOptions;
    /**
     * List of enabled clusters. At least platform cluster needs to be
     * included.
     * @parameter
     * @required
     */
    protected List<String> enabledClusters;

    protected List<File> findClusterDirectories() throws IOException
    {


        if ( !clusterBuildDir.exists() )
        {
            throw new IOException(
                    "There are no additional clusters in " + clusterBuildDir );
        }

        // make sure the final cluster list is numbered. (???)
        Map<String, Pattern> matchers = new HashMap<String, Pattern>();
        if ( enabledClusters == null )
        {
            // fallback default.
            enabledClusters = new ArrayList<String>();
        }

        // a matcher for each cluster
        for ( String enabledCluster : enabledClusters )
        {
            matchers.put( enabledCluster, Pattern.compile(
                    enabledCluster + "(\\d)*" ) );
        }

        // all enabled clusters
        List<File> enabledClusterDirectories = new ArrayList<File>();

        // add all possible cluster directories to a large list
        File[] netbeansSubDirectories = netbeansInstallation.listFiles();
        File[] clusterDirectories = clusterBuildDir.listFiles();
        List<File> files = new ArrayList<File>(
                netbeansSubDirectories.length + clusterDirectories.length );
        files.addAll( Arrays.asList( netbeansSubDirectories ) );
        files.addAll( Arrays.asList( clusterDirectories ) );

        // safety check, null means not existing folder
        if ( netbeansSubDirectories == null )
        {
            throw new IOException(
                    "Non-existing NetBeans installation at " + netbeansInstallation );
        }

        // match directories and cluster names
        for ( File current : files )
        {
            if ( current.isDirectory() )
            {
                String folderName = current.getName();

                // match directory name against each enabled cluster
                for ( String clusterName : enabledClusters )
                {

                    // a cluster could be clustername + version
                    if ( folderName.matches( clusterName + "\\d*" ) )
                    {
                        enabledClusterDirectories.add( current );
                        continue;
                    }
                }
            }
        }

        return enabledClusterDirectories;
    }

    /**
     * Find the (first) platform cluster directory in a list of directories
     * 
     * @param clusterDirectories A list for cluster directories
     * @return <code>null</code> if there is no platform directory in the list
     */
    protected File findPlatformClusterDirectory( List<File> clusterDirectories )
    {
        for ( File clusterDirectory : clusterDirectories )
        {
            if ( clusterDirectory.getName().matches( "platform\\d" ) )
            {
                return clusterDirectory;
            }
        }

        return null;
    }

    /**
     * 
     * @param buildDir Directory where the platform bundle is built
     * @param harnessDir "harness" directory of the netbeans installation
     * @param enabledClusters The names of all enabled clusters
     * @param defaultOptions Options for the netbeans platform to be placed in config file
     * @param brandingToken 
     * 
     * @throws java.io.IOException
     */
    protected void createBundleEtcDir( File buildDir, File harnessDir, List<String> enabledClusters, String defaultOptions, String brandingToken )
            throws IOException
    {
        File etcDir = new File( buildDir + File.separator + "etc" );
        etcDir.mkdir();

        // create app.clusters which contains a list of clusters to include in the application

        File clusterConf = new File(
                etcDir + File.separator + brandingToken + ".clusters" );
        clusterConf.createNewFile();
        StringBuffer buffer = new StringBuffer();
        for ( String clusterName : enabledClusters )
        {
            buffer.append( clusterName );
            buffer.append( "\n" );
        }

        FileUtils.fileWrite( clusterConf.getAbsolutePath(), buffer.toString() );

        // app.conf contains default options and other settings
        File confFile = new File(
                harnessDir.getAbsolutePath() + File.separator + "etc" + File.separator + "app.conf" );
        File confDestFile = new File(
                etcDir.getAbsolutePath() + File.separator + brandingToken + ".conf" );

        FileUtils.copyFile( confFile, confDestFile );

        // add default options from pom-file to app.conf
        String contents = FileUtils.fileRead( confDestFile );
        contents = contents.replace( "default_options=\"",
                "default_options=\"" + defaultOptions + " " );
        FileUtils.fileWrite( confDestFile.getAbsolutePath(), contents );


    }

    /**
     * Copy all cluster directories to destination, skipping the disabled modules.
     */
    protected void copyClusters( File destination, List<File> clusterDirectories )
            throws IOException
    {

        for ( File clusterDir : clusterDirectories )
        {
            File buildClusterDir = new File(
                    destination + File.separator + clusterDir.getName() );
            FileUtils.copyDirectoryStructure( clusterDir, buildClusterDir );
        }
    }
}
