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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.codehaus.mojo.nbm.model.Dependency;
import org.codehaus.mojo.nbm.model.NetbeansModule;

/**
 *
 * @author mkleint
 */
public class AbstractNbmMojoTest extends TestCase {
    Log log = null;
    
    public AbstractNbmMojoTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        log = new SystemStreamLog();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of matchesLibrary method, of class AbstractNbmMojo.
     */
    public void testMatchesLibrary() {
        System.out.println("matchesLibrary");
        Artifact artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        List libraries = new ArrayList();
        libraries.add("group:artifact");
        ExamineManifest depExaminator = createNonModule();
        boolean result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertTrue("explicitly defined libraries in descriptor are included", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "provided");
        libraries = new ArrayList();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertFalse("provided artifacts are not included", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "system");
        libraries = new ArrayList();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertFalse("system artifacts are not included", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        libraries = new ArrayList();
        libraries.add("group:artifact");
        depExaminator = createModule();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertTrue("netbeans modules are included if explicitly marked in descriptor", result);

        libraries = new ArrayList();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertFalse("netbeans modules are omitted", result);

        artifact = createArtifact("group", "artifact", "1.0", "nbm", "compile");
        libraries = new ArrayList();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertFalse("netbeans modules are omitted", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        artifact.setDependencyTrail(Arrays.asList(new String[] {
            "1",
            "2",
            "3"
        })); //transitive artifact
        libraries = new ArrayList();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log);
        assertFalse("transitive dependencies are omitted", result);

    }

    /**
     * Test of resolveNetbeansDependency method, of class AbstractNbmMojo.
     */
    public void testResolveNetbeansDependency() {
        Artifact artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        List deps = new ArrayList();
        ExamineManifest manifest = createNonModule();
        Dependency result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertNull("not a netbeans module", result);

        manifest = createModule();
        result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertNotNull("is a netbeans module", result);

        artifact = createArtifact("group", "artifact", "1.0", "nbm", "compile");
        manifest = createNonModule();
        result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertNotNull("nbm type is a netbeans module", result);

        manifest = createModule();
        artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        artifact.setDependencyTrail(Arrays.asList(new String[] {
            "1",
            "2",
            "3"
        })); //transitive artifact
        result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertNull("is a netbeans module but transitive, skip", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        deps = new ArrayList();
        Dependency d = new Dependency();
        d.setId("group:artifact");
        deps.add(d);
        manifest = createNonModule();
        result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertNull("not a netbeans module, declared in deps but without explicit value", result);

        d.setExplicitValue("XXX > 1.0");
        result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertEquals("not a netbeans module but declared with explicit value", result, d);

        d.setExplicitValue(null);
        manifest = createModule();
        result = AbstractNbmMojo.resolveNetbeansDependency(artifact, deps, manifest, log);
        assertEquals("netbeans module defined in descriptor", result, d);
    }

//    /**
//     * Test of getLibraryArtifacts method, of class AbstractNbmMojo.
//     */
//    public void testGetLibraryArtifacts() throws Exception {
//        System.out.println("getLibraryArtifacts");
//        DependencyNode treeRoot = null;
//        NetbeansModule module = null;
//        MavenProject project = null;
//        Map<Artifact, ExamineManifest> examinerCache = null;
//        List<Artifact> expResult = null;
//        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(treeRoot, module, project, examinerCache, log);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of getModuleDependencyArtifacts method, of class AbstractNbmMojo.
//     */
//    public void testGetModuleDependencyArtifacts() throws Exception {
//        System.out.println("getModuleDependencyArtifacts");
//        DependencyNode treeRoot = null;
//        NetbeansModule module = null;
//        MavenProject project = null;
//        Map<Artifact, ExamineManifest> examinerCache = null;
//        List<Artifact> libraryArtifacts = null;
//        List<Artifact> expResult = null;
//        List<Artifact> result = AbstractNbmMojo.getModuleDependencyArtifacts(treeRoot, module, project, examinerCache, libraryArtifacts, log);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }


    private Artifact createArtifact(String gr, String art, String ver, String pack, String scope) {
        VersionRange rng = VersionRange.createFromVersion(ver);
        Artifact a = new DefaultArtifact(gr, art, rng, scope, pack, "dummy classifier to avoid having to declare ArtifactHandler", null);
        a.setDependencyTrail(Collections.EMPTY_LIST);
        return a;
    }

    private ExamineManifest createNonModule() {
        ExamineManifest manifest = new ExamineManifest(log);
        manifest.setNetbeansModule(false);
        return manifest;
    }

    private ExamineManifest createModule() {
        ExamineManifest manifest = new ExamineManifest(log);
        manifest.setNetbeansModule(true);
        return manifest;
    }
}
