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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.codehaus.mojo.nbm.model.Dependency;
import org.codehaus.mojo.nbm.model.NetbeansModule;

/**
 *
 * @author mkleint
 */
public class AbstractNbmMojoTest extends TestCase {
    Log log = null;
    DependencyNode treeRoot = null;
    
    public AbstractNbmMojoTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        log = new SystemStreamLog();
        treeRoot = createNode("root", "root", "1.0", "jar", "", true, new ArrayList<Artifact>(), new HashMap<Artifact,ExamineManifest>());
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
        List<String> libraries = new ArrayList<String>();
        libraries.add("group:artifact");
        ExamineManifest depExaminator = createNonModule();
        boolean result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log, false);
        assertTrue("explicitly defined libraries in descriptor are included", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "provided");
        libraries = new ArrayList<String>();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log, false);
        assertFalse("provided artifacts are not included", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "system");
        libraries = new ArrayList<String>();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log, false);
        assertFalse("system artifacts are not included", result);

        artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        libraries = new ArrayList<String>();
        libraries.add("group:artifact");
        depExaminator = createModule();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log, false);
        assertTrue("netbeans modules are included if explicitly marked in descriptor", result);

        libraries = new ArrayList<String>();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log, false);
        assertFalse("netbeans modules are omitted", result);

        artifact = createArtifact("group", "artifact", "1.0", "nbm", "compile");
        libraries = new ArrayList<String>();
        result = AbstractNbmMojo.matchesLibrary(artifact, libraries, depExaminator, log, false);
        assertFalse("netbeans modules are omitted", result);

    }

    /**
     * Test of resolveNetbeansDependency method, of class AbstractNbmMojo.
     */
    public void testResolveNetbeansDependency() {
        Artifact artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        List<Dependency> deps = new ArrayList<Dependency>();
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


        artifact = createArtifact("group", "artifact", "1.0", "jar", "compile");
        deps = new ArrayList<Dependency>();
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

    /**
     * Module is not a library
     */
    public void testGetLibraryArtifacts1() throws Exception {
        System.out.println("getLibraryArtifacts1");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
        List<Artifact> runtimes = new ArrayList<Artifact>();
        DependencyNode module = createNode("gr1", "ar1", "1.0", "jar", "compile", true, runtimes, examinerCache);
        treeRoot.addChild( module );
        NetbeansModule mdl = new NetbeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(0, result.size());
    }

    /**
     * direct dependency is a library
     */
    public void testGetLibraryArtifact2() throws Exception {
        System.out.println("getLibraryArtifacts2");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
        List<Artifact> runtimes = new ArrayList<Artifact>();
        DependencyNode library = createNode("gr1", "ar1", "1.0", "jar", "compile", false, runtimes, examinerCache);
        treeRoot.addChild( library );
        NetbeansModule mdl = new NetbeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(1, result.size());
    }

    
    /**
     * transitive dependency gets included as well.
     */
    public void testGetLibraryArtifact3() throws Exception {
        System.out.println("getLibraryArtifacts3");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
        List<Artifact> runtimes = new ArrayList<Artifact>();
        DependencyNode library = createNode("gr1", "ar1", "1.0", "jar", "compile", false, runtimes, examinerCache);
        treeRoot.addChild( library );
        DependencyNode translibrary = createNode("gr2", "ar2", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        library.addChild(translibrary);
        NetbeansModule mdl = new NetbeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(2, result.size());
    }

    /**
     * transitive dependency of a module doesn't get included as library
     */
    public void testGetLibraryArtifact4() throws Exception {
        System.out.println("getLibraryArtifacts4");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
        List<Artifact> runtimes = new ArrayList<Artifact>();
        DependencyNode module = createNode("gr1", "ar1", "1.0", "jar", "compile", true, runtimes, examinerCache);
        treeRoot.addChild( module );
        DependencyNode translibrary = createNode("gr2", "ar2", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        module.addChild(translibrary);
        NetbeansModule mdl = new NetbeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(0, result.size());
    }

    /**
     * transitive dependency of a library is a duplicate of a transitive dependency of a module
     * ->doesn't get included.
     */
    public void testGetLibraryArtifact5() throws Exception {
        System.out.println("getLibraryArtifacts5");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<Artifact, ExamineManifest>();
        List<Artifact> runtimes = new ArrayList<Artifact>();
        DependencyNode module = createNode("gr1", "ar1", "1.0", "jar", "compile", true, runtimes, examinerCache);
        treeRoot.addChild( module );
        DependencyNode translibrary = createNode("gr2", "ar2", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        module.addChild(translibrary);

        DependencyNode library = createNode("gr3", "ar3", "1.0", "jar", "compile", false, runtimes, examinerCache);
        treeRoot.addChild( library );
        DependencyNode translibrary2 = createNode("gr4", "ar4", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        library.addChild(translibrary2);
        DependencyNode translibrary3 = createNode(translibrary.getArtifact(), DependencyNode.OMITTED_FOR_DUPLICATE);
        translibrary2.addChild(translibrary3);

        NetbeansModule mdl = new NetbeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(2, result.size());
        assertEquals(result.get(0).getId(), library.getArtifact().getId());
        assertEquals(result.get(1).getId(), translibrary2.getArtifact().getId());
    }

    private DependencyNode createNode(String gr, String art, String ver, String pack, String scope, boolean isModule, List<Artifact> runtimes, Map<Artifact, ExamineManifest> cache) {
        Artifact a = createArtifact(gr, art, ver, pack, scope);
        DependencyNode nd = new DependencyNode(a);
        ExamineManifest manifest = isModule ? createModule() : createNonModule();
        runtimes.add(a);
        cache.put(a, manifest);
        return nd;
    }

    private DependencyNode createNode(Artifact a, int state) {
        DependencyNode nd = new DependencyNode(a, state, a);
        return nd;
    }

    private Artifact createArtifact(String gr, String art, String ver, String pack, String scope) {
        VersionRange rng = VersionRange.createFromVersion(ver);
        Artifact a = new DefaultArtifact(gr, art, rng, scope, pack, "classifier", null);
        a.setDependencyTrail(Collections.EMPTY_LIST);
        a.setFile(new File(gr + File.separator + art + File.separator + art + "-"+ ver + ".jar"));
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
