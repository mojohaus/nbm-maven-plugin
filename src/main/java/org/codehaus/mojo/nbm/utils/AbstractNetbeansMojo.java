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
package org.codehaus.mojo.nbm.utils;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Taskdef;

public abstract class AbstractNetbeansMojo
    extends AbstractMojo
{

    /**
     * Creates a project initialized with the same logger.
     */
    protected final Project antProject()
    {
        Project antProject = new Project();
        antProject.init();
        antProject.addBuildListener( new BuildListener()
        {
            @Override
            public void buildStarted( BuildEvent be )
            {
                getLog().debug( "Ant build started" );
            }
            @Override
            public void buildFinished( BuildEvent be )
            {
                if ( be.getException() != null )
                {
                    getLog().error( be.getMessage(), be.getException() );
                }
                else
                {
                    getLog().debug( "Ant build finished" );
                }
            }
            @Override
            public void targetStarted( BuildEvent be )
            {
                getLog().info( be.getTarget().getName() + ":" );
            }
            @Override
            public void targetFinished( BuildEvent be )
            {
                getLog().debug( be.getTarget().getName() + " finished" );
            }
            @Override
            public void taskStarted( BuildEvent be )
            {
                getLog().debug( be.getTask().getTaskName() + " started" );
            }
            @Override
            public void taskFinished( BuildEvent be )
            {
                getLog().debug( be.getTask().getTaskName() + " finished" );
            }
            @Override
            public void messageLogged( BuildEvent be )
            {
                switch ( be.getPriority() )
                {
                    case Project.MSG_ERR:
                        getLog().error( be.getMessage() );
                        break;
                    case Project.MSG_WARN:
                        getLog().warn( be.getMessage() );
                        break;
                    case Project.MSG_INFO:
                        getLog().info( be.getMessage() );
                        break;
                    default:
                        getLog().debug( be.getMessage() );
                }
            }
        } );
        return antProject;
    }

    protected final Project registerNbmAntTasks()
    {
        Project antProject = antProject();

        Taskdef taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.MakeListOfNBM" );
        taskdef.setName( "genlist" );
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.MakeNBM" );
        taskdef.setName( "makenbm" );
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.MakeUpdateDesc" );
        taskdef.setName( "updatedist" );
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.CreateModuleXML" );
        taskdef.setName( "createmodulexml" );
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask( "taskdef" );
        taskdef.setClassname( "org.netbeans.nbbuild.JHIndexer" );
        taskdef.setName( "jhindexer" );
        taskdef.execute();

        return antProject;
    }



}
