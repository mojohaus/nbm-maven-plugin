/*
 * Copyright 2013 Codehaus.
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
package org.codehaus.mojo.nbm;

import edu.emory.mathcs.backport.java.util.Arrays;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.mojo.nbm.CreateClusterAppMojo.BundleTuple;
import org.codehaus.mojo.nbm.utils.ExamineManifest;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mkleint
 */
public class CreateClusterAppMojoTest
{
    
    public CreateClusterAppMojoTest()
    {
    }
    
    @Test
    public void computeClusterOrderingTest() throws Exception {
        HashMap<String, Set<String>> clusterDeps = new HashMap<String, Set<String>>();
        HashMap<String, Set<String>> clusterModules = new HashMap<String, Set<String>>();
        clusterModules.put( "platform", new HashSet<String>(Arrays.asList( new String[] {"pl-a", "pl-b", "pl-c"})));
        clusterModules.put( "ide", new HashSet<String>(Arrays.asList( new String[] {"i-a", "i-b", "i-c"})));
        clusterModules.put( "java", new HashSet<String>(Arrays.asList( new String[] {"j-a", "j-b", "j-c"})));
        
        clusterDeps.put( "java", new HashSet<String>(Arrays.asList( new String[] {"i-a", "pl-b", "pl-c"})));
        clusterDeps.put( "ide", new HashSet<String>(Arrays.asList( new String[] {"pl-b", "pl-c"})));
        Map<String, Set<String>> res = CreateClusterAppMojo.computeClusterOrdering( clusterDeps, clusterModules);
        assertNotNull( res );
        Set<String> resJava = res.get( "java");
        assertNotNull( resJava );
        assertEquals( resJava.size(), 2);
        assertTrue( resJava.contains( "ide"));
        assertTrue( resJava.contains( "platform"));
        
        Set<String> resIde = res.get( "ide");
        assertNotNull( resIde );
        assertEquals( resIde.size(), 1);
        assertTrue( resIde.contains( "platform"));
        
    }
    
    @Test
    public void assignClustersToBundles() throws Exception {
        ArrayList<BundleTuple> bundles = new ArrayList<BundleTuple>();
        BundleTuple tup1 = createBundleTuple("a.b.c", new File(getClass().getResource( "/osgimanifests" + File.separator + "a.b.c.MF").toURI()));
        bundles.add( tup1 );
        BundleTuple tup2 = createBundleTuple("b.c.d", new File(getClass().getResource( "/osgimanifests" + File.separator + "b.c.d.MF").toURI()));
        assertTrue(Arrays.toString( tup2.manifest.getOsgiImports().toArray()),  tup2.manifest.getOsgiImports().contains( "a.b.c"));
        bundles.add( tup2 );
        HashMap<String, Set<String>> clusterDeps = new HashMap<String, Set<String>>();
        clusterDeps.put( "java", new HashSet<String>(Arrays.asList( new String[] {"i-a", "pl-b", "pl-c"})));
        clusterDeps.put( "ide", new HashSet<String>(Arrays.asList( new String[] {"pl-b", "pl-c", "a.b.c"})));
        
        CreateClusterAppMojo.assignClustersToBundles(bundles, Collections.<String>emptySet(), clusterDeps, Collections.<String, Set<String>>emptyMap(), null);
        assertEquals( "ide", tup1.cluster);
        assertEquals( "ide", tup2.cluster);
        
        clusterDeps.clear();
        clusterDeps.put( "ide", new HashSet<String>(Arrays.asList( new String[] {"i-a", "pl-b", "pl-c"})));
        clusterDeps.put( "java", new HashSet<String>(Arrays.asList( new String[] {"pl-b", "pl-c", "b.c.d"})));
        tup2.cluster = null;
        tup1.cluster = null;
        
        CreateClusterAppMojo.assignClustersToBundles(bundles, Collections.<String>emptySet(), clusterDeps, Collections.<String, Set<String>>emptyMap(), null);
        assertEquals( "java", tup2.cluster);
        assertEquals( "java", tup1.cluster);
        
    }

    private BundleTuple createBundleTuple( String cnb, File file ) throws MojoExecutionException
    {
        assertTrue( file.exists());
        
        ExamineManifest em = new ExamineManifest( null );
        em.setManifestFile( file );
        em.setPopulateDependencies( true);
        em.checkFile();
        assertEquals( cnb, em.getModule());
        BundleTuple toRet = new BundleTuple( null, em);
                
        return toRet;
    }
}