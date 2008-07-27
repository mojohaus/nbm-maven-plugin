/* ==========================================================================
 * Copyright 2003-2004 Mevenide Team
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

/**
 *  will try to convert the maven version number to a Netbeans friendly version number.
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 *
 */
public class AdaptNbVersion
{

    public static final String TYPE_SPECIFICATION = "spec"; //NOI18N
    public static final String TYPE_IMPLEMENTATION = "impl"; //NOI18N
    private static final String SNAPSHOT = "SNAPSHOT"; //NOI18N

    public static String adaptVersion( String version, Object type )
    {
        StringTokenizer tok = new StringTokenizer( version, "." );
        if ( SNAPSHOT.equals( version ) && TYPE_IMPLEMENTATION.equals( type ) )
        {
            return "0.0.0." + generateSnapshotValue();
        }
        StringBuffer toReturn = new StringBuffer();
        while ( tok.hasMoreTokens() )
        {
            String token = tok.nextToken();
            if ( TYPE_IMPLEMENTATION.equals( type ) )
            {
                int snapshotIndex = token.indexOf( SNAPSHOT );
                if ( snapshotIndex > 0 )
                {
                    String repl = token.substring( 0, snapshotIndex ) + generateSnapshotValue();
                    if ( token.length() > snapshotIndex + SNAPSHOT.length() )
                    {
                        repl = token.substring(
                                snapshotIndex + SNAPSHOT.length() );
                    }
                    token = repl;
                }
            }
            if ( TYPE_SPECIFICATION.equals( type ) )
            {
                // strip the trailing -RC1, -BETA5, -SNAPSHOT
                if ( token.indexOf( '-' ) > 0 )
                {
                    token = token.substring( 0, token.indexOf( '-' ) );
                } else if ( token.indexOf( '_' ) > 0 )
                {
                    token = token.substring( 0, token.indexOf( '_' ) );
                }
                try
                {
                    Integer intValue = Integer.valueOf( token );
                    token = intValue.toString();
                } catch ( NumberFormatException exc )
                {
                    // ignore, will just not be added to the
                    token = "";
                }
            }
            if ( token.length() > 0 )
            {
                if ( toReturn.length() != 0 )
                {
                    toReturn.append( "." );
                }
                toReturn.append( token );
            }

        }
        if ( toReturn.length() == 0 )
        {
            toReturn.append( "0.0.0" );
        }
        return toReturn.toString();
    }

    private static String generateSnapshotValue()
    {
        return new SimpleDateFormat( "yyyyMMdd" ).format( new Date() );
    }
}
