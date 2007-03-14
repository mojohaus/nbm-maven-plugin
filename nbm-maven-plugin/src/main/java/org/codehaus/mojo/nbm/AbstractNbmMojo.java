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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.mojo.nbm.model.Dependency;
import org.codehaus.mojo.nbm.model.NetbeansModule;
import org.codehaus.mojo.nbm.model.io.xpp3.NetbeansModuleXpp3Reader;

public abstract class AbstractNbmMojo extends AbstractMojo {


    protected Project registerNbmAntTasks() {
        Project antProject = new Project();
        antProject.init();

        Taskdef taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname("org.netbeans.nbbuild.MakeListOfNBM" );
        taskdef.setName("genlist");
        taskdef.execute();
        
        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname("org.netbeans.nbbuild.MakeNBM" );
        taskdef.setName("makenbm");
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname("org.netbeans.nbbuild.MakeUpdateDesc" );
        taskdef.setName("updatedist");
        taskdef.execute();
        
        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname("org.netbeans.nbbuild.CreateModuleXML" );
        taskdef.setName("createmodulexml");
        taskdef.execute();
        
        return antProject;
    }
    
    protected boolean matchesLibrary(Artifact artifact, List libraries) {
// when we have classifier like jar-assembly this condition is not true..
// just take everything that is a dependecy, no matter of what type..        
//        if (!"jar".equals(artifact.getType())) {
//            // just jars make sense.
//            return false;
//        }
        String artId = artifact.getArtifactId();
        String grId = artifact.getGroupId();
        String id = grId + ":" + artId;
        return libraries.remove(id);
    }
    
    protected Dependency resolveNetbeansDependency(Artifact artifact, List deps) {
// when we have classifier like jar-assembly this condition is not true..
// just take everything that is a dependecy, no matter of what type..        
//        if (!"jar".equals(artifact.getType())) {
//            // just jars make sense.
//            return null;
//        }
        String artId = artifact.getArtifactId();
        String grId = artifact.getGroupId();
        String id = grId + ":" + artId;
        Iterator it = deps.iterator();
        while (it.hasNext()) {
            Dependency dep = (Dependency)it.next();
            if (id.equals(dep.getId())) {
                return dep;
            }
        }
        return null;
    }
    
    protected NetbeansModule readModuleDescriptor(File descriptor) throws MojoExecutionException {
        if (descriptor == null ||  !descriptor.exists()) {
            return null;
        }
        Reader r = null;
        try {
            r = new FileReader( descriptor );
            NetbeansModuleXpp3Reader reader = new NetbeansModuleXpp3Reader();
            NetbeansModule module = reader.read(r);
            return module;
        } catch (IOException exc) {
            getLog().error(exc);
        } catch (XmlPullParserException xml) {
            getLog().error(xml);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException e) {
                    getLog().error(e);
                }
            }
        }
        return null;
    }

}
