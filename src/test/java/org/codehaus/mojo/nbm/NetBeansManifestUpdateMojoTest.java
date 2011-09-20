/*
 *  Copyright 2008 mkleint.
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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import junit.framework.TestCase;
import org.apache.tools.ant.taskdefs.Manifest;

public class NetBeansManifestUpdateMojoTest extends TestCase {
    
    public NetBeansManifestUpdateMojoTest(String testName) {
        super(testName);
    }

    public void testCreateCompiledPatternList()
    {
        List<String> subpackages = Arrays.asList( new String[] {
                "org.milos.**",
                "org.tomas.**"
        });
        List<Pattern> result = NetBeansManifestUpdateMojo.createCompiledPatternList( subpackages );
        assertTrue( matches( "org.milos.Test", result));
        assertTrue( matches( "org.milos.pack.Test", result));
        assertTrue( matches( "org.tomas.pack.Test$Inside", result));
        assertFalse( matches( "org.milan", result));
        assertFalse( matches( "org.milosclass", result));

        List<String> packages = Arrays.asList( new String[] {
                "org.milos.*",
                "org.tomas.*"
        });
        result = NetBeansManifestUpdateMojo.createCompiledPatternList( packages );
        assertTrue( matches( "org.milos.Test", result));
        assertFalse( matches( "org.milos.pack.Test", result));
        assertFalse( matches( "org.tomas.pack.Test$Inside", result));
        assertTrue( matches( "org.tomas.Test$Inside", result));
        assertFalse( matches( "org.milan", result));
        assertFalse( matches( "org.milosclass", result));

    }

    private boolean matches(String className, List<Pattern> matchers) {
        for (Pattern patt : matchers) {
            if (patt.matcher( className ).matches()) {
                return true;
            }
        }
        return false;
    }

    public void testShorten()
    {
        Locale old = Locale.getDefault();
        Locale.setDefault( Locale.US );
        try
        {
            assertEquals( null, NetBeansManifestUpdateMojo.shorten ( null ) );
            assertEquals( null, NetBeansManifestUpdateMojo.shorten ( "" ) );
            assertEquals( "I typed some description here", NetBeansManifestUpdateMojo.shorten ( "I typed some description here" ) );
            assertEquals( "Now I'm trying to be serious.", NetBeansManifestUpdateMojo.shorten ( "Now I'm trying to be serious." ) );
            assertEquals( "A meaningful description.", NetBeansManifestUpdateMojo.shorten ( "A meaningful description. But will it work?" ) );
            assertEquals( "I have no idea what this module does, do you?", NetBeansManifestUpdateMojo.shorten ( "I have no idea what this module does, do you? No? Fine." ) );
        }
        finally
        {
            Locale.setDefault( old );
        }
    }

    public void testNewlines()
        throws Exception
    {
        Manifest m = new Manifest();
        Manifest.Section s = m.getMainSection();
        new NetBeansManifestUpdateMojo().conditionallyAddAttribute( s, "Desc", "Something.\n   Else.\n" );
        assertEquals( "Something. Else.", s.getAttributeValue( "Desc" ));
    }

}
