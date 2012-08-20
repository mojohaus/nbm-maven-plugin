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
package org.codehaus.mojo.nbm;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

public class CreateNetBeansFileStructureTest
        extends AbstractMojoTestCase
{

    public void testWriteExternal()
            throws Exception
    {
        String localRepository = System.getProperty( "localRepository" );
        ArtifactFactory artifactFactory = (ArtifactFactory) lookup( ArtifactFactory.class.getName() );
        ArtifactResolver artifactResolver = (ArtifactResolver) lookup( ArtifactResolver.class.getName() );
        Artifact a = artifactFactory.createBuildArtifact( "asm", "asm", "3.0", "jar" );
        artifactResolver.resolve( a, Collections.emptyList(), new DefaultArtifactRepository( "local", new File(localRepository).toURI().toString(), new DefaultRepositoryLayout() ) );
        StringWriter w = new StringWriter();
        CreateNetBeansFileStructure.writeExternal( new PrintWriter( w ), a );
        assertEquals( "CRC:229904029\nSIZE:42710\nURL:m2:/asm:asm:3.0:jar\nURL:http://repo.maven.apache.org/maven2/asm/asm/3.0/asm-3.0.jar\n", w.toString() );
    }

}
