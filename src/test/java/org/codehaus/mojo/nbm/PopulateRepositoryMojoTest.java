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

import junit.framework.TestCase;

/**
 *
 * @author mkleint
 */
public class PopulateRepositoryMojoTest extends TestCase {
    
    public PopulateRepositoryMojoTest(String testName) {
        super(testName);
    }



    /**
     * Test of stripClusterName method, of class PopulateRepositoryMojo.
     */
    public void testStripClusterName()
    {
        System.out.println( "stripClusterName" );
        assertEquals( "platform", PopulateRepositoryMojo.stripClusterName( "platform9" ) );
        assertEquals( "platform", PopulateRepositoryMojo.stripClusterName( "platform11" ) );
        assertEquals( "nb", PopulateRepositoryMojo.stripClusterName( "nb6.9" ) );
        assertEquals( "extra", PopulateRepositoryMojo.stripClusterName( "extra" ) );
    }

}
