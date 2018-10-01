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

import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mkleint
 */
public class BrandingMojoTest
{
    
    public BrandingMojoTest()
    {
    }

    /**
     * Test of destinationFileName method, of class BrandingMojo.
     */
    @Test
    public void testDestinationFileName()
    {
        assertEquals( "cut_brandingToken.gif", BrandingMojo.destinationFileName( "cut.gif", "brandingToken" ) );
        assertEquals( "cut_brandingToken", BrandingMojo.destinationFileName( "cut", "brandingToken" ) );
        assertEquals( "cut_pressed_brandingToken.gif", BrandingMojo.destinationFileName( "cut_pressed.gif", "brandingToken" ) );
        assertEquals( "path1" + File.separator + "path2" + File.separator + "cut_brandingToken", BrandingMojo.destinationFileName( "path1" + File.separator + "path2" + File.separator + "cut", "brandingToken" ) );
        assertEquals( "path.1" + File.separator + "path.2" + File.separator + "cut_brandingToken", BrandingMojo.destinationFileName( "path.1" + File.separator + "path.2" + File.separator + "cut", "brandingToken" ) );
        assertEquals( "path.1" + File.separator + "cut_pressed_brandingToken.gif", BrandingMojo.destinationFileName( "path.1" + File.separator + "cut_pressed.gif", "brandingToken" ) );
    }
    
    @Test
    public void testLocale() {
        assertEquals("en_us", BrandingMojo.getLocale( "aaa_en_us.properties")[1]);
        assertEquals("en_us_ca", BrandingMojo.getLocale( "aaa_en_us_ca.properties")[1]);
        assertEquals("en_us_ca", BrandingMojo.getLocale( "aa_en_us_ca.properties")[1]);
        assertEquals("en_us_ca", BrandingMojo.getLocale( "bb_aa_en_us_ca.properties")[1]);
        assertEquals("en", BrandingMojo.getLocale( "bb_aaa_en.properties")[1]);
        assertEquals(null, BrandingMojo.getLocale( "bb_aaa_end.properties")[1]);
        assertEquals(null, BrandingMojo.getLocale( "bb_aa_end.properties")[1]);
        assertEquals(null, BrandingMojo.getLocale( "bb.properties")[1]);
    }
}
