/*
 *  Copyright 2010 mkleint.
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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.codehaus.plexus.util.FileUtils;

public class PopulateRepositoryMojoTest extends AbstractMojoTestCase {
    
    public void testStripClusterName()
    {
        assertEquals( "platform", PopulateRepositoryMojo.stripClusterName( "platform9" ) );
        assertEquals( "platform", PopulateRepositoryMojo.stripClusterName( "platform11" ) );
        assertEquals( "nb", PopulateRepositoryMojo.stripClusterName( "nb6.9" ) );
        assertEquals( "extra", PopulateRepositoryMojo.stripClusterName( "extra" ) );
    }

    public void testInstall() throws Exception
    {
        PopulateRepositoryMojo mojo = ( PopulateRepositoryMojo ) lookupMojo( "populate-repository", new File( getBasedir(), "src/test/resources/PopulateRepositoryMojoTest.xml" ) );
        File repo = new File( System.getProperty( "java.io.tmpdir" ), "PopulateRepositoryMojoTest" );
        FileUtils.deleteDirectory( repo );
        mojo.localRepository = new DefaultArtifactRepository( "test", repo.toURI().toString(), new DefaultRepositoryLayout() );
        Artifact art1 = mojo.createArtifact( "testarg", "1.0", "testgrp" );
        File f = File.createTempFile( "PopulateRepositoryMojoTest", ".nbm" );
        f.deleteOnExit();
        Artifact art2 = mojo.createAttachedArtifact( art1, f, "nbm-file", null );
        assertEquals( "nbm", art2.getArtifactHandler().getExtension() );
        mojo.install( f, art2 );
        assertTrue( new File( repo, "testgrp/testarg/1.0/testarg-1.0.nbm" ).isFile() );
        assertFalse( new File( repo, "testgrp/testarg/1.0/testarg-1.0.jar" ).isFile() );
    }

}
