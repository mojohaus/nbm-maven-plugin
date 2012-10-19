/*
 * Copyright 2012 Codehaus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.codehaus.mojo.nbm.repository;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.index.NexusIndexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

/**
 * Goal for retrieving and expanding the lucene index of the given repository. That in turn is used by the <code>populate</code>
 * goal.
 * @author mkleint
 */
@Mojo(name="download", aggregator=true, requiresProject=false)
public class DownloadIndexMojo extends AbstractMojo implements Contextualizable {
    
    /**
     * url of the repository to download index from. Please note that if you already have
     * an existing index at <code>nexusIndexDirectory</code>, you should always use the same url for that directory.
     */
    @Parameter(required=true, property="repositoryUrl")
    private String repositoryUrl;
    
    /**
     * location on disk where the index should be created. either empty or with existing index from same repository. then only update check will
     * be performed.
     */
    @Parameter(required=true, property="nexusIndexDirectory")
    private File nexusIndexDirectory;
 
    @Component
    IndexUpdater remoteIndexUpdater;
    
    @Component
    NexusIndexer indexer;
    
    PlexusContainer container;
    
    
    @Component
    WagonManager wagonManager;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try        
        {
            List<IndexCreator> creators = new ArrayList<IndexCreator>();
            creators.addAll(container.lookupList(IndexCreator.class));
            String indexurl = repositoryUrl + (!repositoryUrl.endsWith( "/") ? "/" : "") + ".index";
            IndexingContext indexingContext = indexer.addIndexingContextForced(
                                    "central", // context id
                                    "central", // repository id
                                    null, // repository folder
                                    nexusIndexDirectory,
                                    repositoryUrl,// repositoryUrl
                                    indexurl,
                                    creators);

            String protocol = URI.create(repositoryUrl).getScheme();
            ProxyInfo wagonProxy = wagonManager.getProxy( protocol );
            TransferListener tr = new TransferListener() {

                @Override
                public void transferInitiated( TransferEvent transferEvent )
                {
                    getLog().info( "Initiated connection to " + repositoryUrl);
                }

                @Override
                public void transferStarted( TransferEvent transferEvent )
                {
                    getLog().info( "Started transfer of " + repositoryUrl + "/.index/" + transferEvent.getResource().toString());
                }

                @Override
                public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
                {
                }

                @Override
                public void transferCompleted( TransferEvent transferEvent )
                {
                    getLog().info( "Finished transfer of " + repositoryUrl + "/.index/" + transferEvent.getResource().toString());
                }

                @Override
                public void transferError( TransferEvent transferEvent )
                {
                    getLog().error( "Failed transfer of " + repositoryUrl + "/.index/" + transferEvent.getResource().toString(), transferEvent.getException());
                }

                @Override
                public void debug( String message )
                {
                }
            };
            // MINDEXER-42: cannot use WagonHelper.getWagonResourceFetcher
            Wagon wagon = container.lookup(Wagon.class, protocol);
            if (wagon instanceof HttpWagon) { //#216401
                HttpWagon httpwagon = (HttpWagon) wagon;
                //#215343
                Properties p = new Properties();
                p.setProperty("User-Agent", "mojo/nb-repository-plugin");
                httpwagon.setHttpHeaders(p);
            }

            ResourceFetcher fetcher = new WagonHelper.WagonFetcher(wagon, tr, null, wagonProxy);
            IndexUpdateRequest iur = new IndexUpdateRequest(indexingContext, fetcher);

            remoteIndexUpdater.fetchAndUpdateIndex(iur);
            indexer.removeIndexingContext(indexingContext, false);
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "Cannot download index", ex);
        }
    }

    @Override
    public void contextualize( Context context ) throws ContextException
    {
        this.container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    
}
