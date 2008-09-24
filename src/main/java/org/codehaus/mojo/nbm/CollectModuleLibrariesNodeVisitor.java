/* ==========================================================================
 * Copyright 2008 mkleint
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;

/**
 * A dependency node visitor that collects visited nodes that are known libraries or are
 * children of known libraries
 * @author milos kleint
 */
public class CollectModuleLibrariesNodeVisitor
    implements DependencyNodeVisitor
{

    /**
     * The collected list of nodes.
     */
    private final Map<String, List<Artifact>> directNodes;

    private final Map<String, List<Artifact>> transitiveNodes;

    private Map<String, Artifact> artifacts;

    private Map<Artifact, ExamineManifest> examinerCache;

    private final Log log;

    private MojoExecutionException throwable;

    private DependencyNode root;

    private Stack<String> currentModule = new Stack<String>();

    /**
     * Creates a dependency node visitor that collects visited nodes for further processing.
     */
    public CollectModuleLibrariesNodeVisitor(
        List<Artifact> runtimeArtifacts, Map<Artifact, ExamineManifest> examinerCache,
        Log log, DependencyNode root )
    {
        directNodes = new HashMap<String, List<Artifact>>();
        transitiveNodes = new HashMap<String, List<Artifact>>();
        artifacts = new HashMap<String, Artifact>();
        for ( Artifact a : runtimeArtifacts )
        {
            artifacts.put( a.getDependencyConflictId(), a );
        }
        this.examinerCache = examinerCache;
        this.log = log;
        this.root = root;
    }

    /**
     * {@inheritDoc}
     */
    public boolean visit( DependencyNode node )
    {
        if ( throwable != null )
        {
            return false;
        }
        if ( root == node )
        {
            return true;
        }
        try
        {
            Artifact artifact = node.getArtifact();
            if ( !artifacts.containsKey( artifact.getDependencyConflictId() ) )
            {
                //ignore non-runtime stuff..
                return false;
            }
            if ( node.getState() != DependencyNode.INCLUDED )
            {
//                if (node.getState() == DependencyNode.OMITTED_FOR_DUPLICATE) {
//                    duplicates.add( artifact.getDependencyConflictId() );
//                }
//                if (node.getState() == DependencyNode.OMITTED_FOR_CONFLICT) {
//                    conflicts.add( artifact.getDependencyConflictId() );
//                }
                return true;
            }
            // somehow the transitive artifacts in the  tree are not always resolved?
            artifact = artifacts.get( artifact.getDependencyConflictId() );

            ExamineManifest depExaminator = examinerCache.get( artifact );
            if ( depExaminator == null )
            {
                depExaminator = new ExamineManifest( log );
                depExaminator.setJarFile( artifact.getFile() );
                depExaminator.checkFile();
                examinerCache.put( artifact, depExaminator );
            }
            if ( depExaminator.isNetbeansModule() )
            {
                currentModule.push( artifact.getDependencyConflictId() );
                ArrayList<Artifact> arts = new ArrayList<Artifact>();
                arts.add( artifact );
                if ( currentModule.size() == 1 )
                {
                    directNodes.put( currentModule.peek(), arts );
                }
                else
                {
                    transitiveNodes.put( currentModule.peek(), arts );
                }
                return true;
            }
            if ( currentModule.size() > 0 )
            {
                if ( AbstractNbmMojo.matchesLibrary( artifact, Collections.<String>emptyList(), depExaminator, log ) )
                {
                    if ( currentModule.size() == 1 )
                    {
                        directNodes.get( currentModule.peek() ).add( artifact );
                    }
                    else
                    {
                        transitiveNodes.get( currentModule.peek() ).add( artifact );
                    }
                    // if a library, iterate to it's child nodes.
                    return true;
                }
            }

        }
        catch ( MojoExecutionException mojoExecutionException )
        {
            throwable = mojoExecutionException;
        }
        //don't bother iterating to childs if the current node is not a library in module dependency.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean endVisit( DependencyNode node )
    {
        if ( throwable != null )
        {
            return false;
        }
        if ( !currentModule.empty() && currentModule.peek().equals( node.getArtifact().getDependencyConflictId() ) )
        {
            currentModule.pop();
        }
        return true;
    }

    /**
     * modules declared in the project's pom
     * @return a map of module artifact lists, key is the dependencyConflictId
     * @throws org.apache.maven.plugin.MojoExecutionException
     */
    public Map<String, List<Artifact>> getDeclaredArtifacts() throws MojoExecutionException
    {
        if ( throwable != null )
        {
            throw throwable;
        }
        return directNodes;
    }

    /**
     * modules that were picked up transitively
     * @return a map of module artifact lists, key is the dependencyConflictId
     * @throws org.apache.maven.plugin.MojoExecutionException
     */
    public Map<String, List<Artifact>> getTransitiveArtifacts() throws MojoExecutionException
    {
        if ( throwable != null )
        {
            throw throwable;
        }
        return transitiveNodes;
    }
}

