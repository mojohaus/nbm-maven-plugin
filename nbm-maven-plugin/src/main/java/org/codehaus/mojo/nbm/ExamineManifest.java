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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Tag examines the manifest of a jar file and retrieves netbeans specific information.
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 *
 */
public class ExamineManifest  {
    
    private File jarFile;
    private File manifestFile;
    // package private to simplify testing
    private boolean netbeansModule;
    private boolean localized;
    private String specVersion;
    private String implVersion;
    private String module;
    private String moduleDeps;
    private String locBundle;
    private String classpath;
    private boolean publicPackages;
    private boolean populateDependencies = false;
    private List dependencyTokens = Collections.EMPTY_LIST;
   
    
    public void checkFile() throws MojoExecutionException {
        
        resetExamination();
        
        Manifest mf = null;
        if (jarFile != null) {
            JarFile jar = null;
            try {
                jar = new JarFile(jarFile);
                mf = jar.getManifest();
            } catch (Exception exc) {
                throw new MojoExecutionException( exc.getMessage(), exc );
            } finally {
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (IOException io) {
                        throw new MojoExecutionException( io.getMessage(), io );
                    }
                }
            }
        } else if (manifestFile != null) {
            InputStream stream = null;
            try {
                stream = new FileInputStream(manifestFile);
                mf = new Manifest(stream);
            } catch (Exception exc) {
                throw new MojoExecutionException( exc.getMessage(), exc );
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException io) {
                        throw new MojoExecutionException( io.getMessage(), io );
                    }
                }
            }
        }
        if (mf != null) {
            processManifest(mf);
        } else {
            throw new MojoExecutionException("Cannot read jar file or manifest file");
        }
    }
    
    void resetExamination() {
        setNetbeansModule(false);
        setLocalized(false);
        setSpecVersion(null);
        setImplVersion(null);
        setModule(null);
        setModuleDeps(null);
        setLocBundle(null);
        setPublicPackages(false);
        setClasspath("");
    }
    
    void processManifest(Manifest mf) {
        Attributes attrs = mf.getMainAttributes();
        setModule(attrs.getValue("OpenIDE-Module"));
        setNetbeansModule(getModule() != null);
        if (isNetbeansModule()) {
            setLocBundle(attrs.getValue("OpenIDE-Module-Localizing-Bundle"));
            setLocalized((getLocBundle() == null ? false : true));
            setSpecVersion(attrs.getValue("OpenIDE-Module-Specification-Version"));
            setImplVersion(attrs.getValue("OpenIDE-Module-Implementation-Version"));
            setModuleDeps(attrs.getValue("OpenIDE-Module-Module-Dependencies"));
            setClasspath(attrs.getValue("Class-Path") == null ? "" : attrs.getValue("Class-Path"));
            String value = attrs.getValue("OpenIDE-Module-Public-Packages");
            if (attrs.getValue("OpenIDE-Module-Friends") != null || value == null || value.trim().equals("-")) {
                setPublicPackages(false);
            } else {
                setPublicPackages(true);
            }
            if (isPopulateDependencies()) {
                String deps = attrs.getValue("OpenIDE-Module-Module-Dependencies");
                if (deps != null) {
                    StringTokenizer tokens = new StringTokenizer(deps, ",");
                    List depList = new ArrayList();
                    while (tokens.hasMoreTokens()) {
                        String tok = tokens.nextToken();
                        //we are just interested in specification and loose dependencies.
                        int spec = tok.indexOf(">");
                        if (spec > 0 || (tok.indexOf("=") == -1 && spec == -1)) {
                            if (spec > 0) {
                                tok = tok.substring(0, spec - 1);
                            }
                            int slash = tok.indexOf("/");
                            if (slash > 0) {
                                tok = tok.substring(0, slash - 1);
                            }
                            depList.add(tok.trim());
                        }
                    }
                    setDependencyTokens(depList);
                }
            }
            
        } else {
            // for non-netbeans jars.
            setSpecVersion(attrs.getValue("Specification-Version"));
            setImplVersion(attrs.getValue("Implementation-Version"));
            setModule(attrs.getValue("Package"));
            setPublicPackages(false);
            setClasspath("");
        /*    if (module != null) {
                // now we have the package to make it a module definition, add the version there..
                module = module + "/1"; 
            }
         */
            if (getModule() == null) {
                // do we want to do that?
                setModule(attrs.getValue("Extension-Name"));
            }
        }
        
    }
    
    /**
     * Getter for property jarFile.
     * @return Value of property jarFile.
     */
    public java.io.File getJarFile() {
        return jarFile;
    }

    /**
     * The jar file to examine. It is exclusive with manifestFile.
     */
    public void setJarFile(java.io.File jarFileLoc) {
        jarFile = jarFileLoc;
    }

    
    /** Getter for property manifestFile.
     * @return Value of property manifestFile.
     *
     */
    public File getManifestFile()
    {
        return manifestFile;
    }    
    
    /** 
     * Manifest file to be examined. It is exclusing with jarFile.
     */
    public void setManifestFile(File manifestFileLoc)
    {
        manifestFile = manifestFileLoc;
    }
    
    public void setClasspath(String path) {
        classpath = path;
    }
    
    public String getClasspath() {
        return classpath;
    }

    public boolean isNetbeansModule() {
        return netbeansModule;
    }

    public void setNetbeansModule(boolean netbeansModule) {
        this.netbeansModule = netbeansModule;
    }

    public boolean isLocalized() {
        return localized;
    }

    public void setLocalized(boolean localized) {
        this.localized = localized;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }

    public String getImplVersion() {
        return implVersion;
    }

    public void setImplVersion(String implVersion) {
        this.implVersion = implVersion;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getModuleDeps() {
        return moduleDeps;
    }

    public void setModuleDeps(String moduleDeps) {
        this.moduleDeps = moduleDeps;
    }

    public String getLocBundle() {
        return locBundle;
    }

    public void setLocBundle(String locBundle) {
        this.locBundle = locBundle;
    }

    public boolean hasPublicPackages() {
        return publicPackages;
    }

    public void setPublicPackages(boolean publicPackages) {
        this.publicPackages = publicPackages;
    }

    public boolean isPopulateDependencies() {
        return populateDependencies;
    }

    public void setPopulateDependencies(boolean populateDependencies) {
        this.populateDependencies = populateDependencies;
    }

    public List getDependencyTokens() {
        return dependencyTokens;
    }

    public void setDependencyTokens(List dependencyTokens) {
        this.dependencyTokens = dependencyTokens;
    }
    
}
