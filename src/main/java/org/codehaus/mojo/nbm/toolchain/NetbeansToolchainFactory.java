/*
 *  Copyright 2007 mkleint.
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
package org.codehaus.mojo.nbm.toolchain;

//import java.io.File;
//import org.apache.maven.context.BuildContext;
//import org.apache.maven.toolchain.MisconfiguredToolchainException;
//import org.apache.maven.toolchain.RequirementMatcherFactory;
//import org.apache.maven.toolchain.Toolchain;
//import org.apache.maven.toolchain.ToolchainFactory;
//import org.apache.maven.toolchain.ToolchainPrivate;
//import org.apache.maven.toolchain.model.ToolchainModel;
//import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 *
 * @author mkleint
 */
public class NetbeansToolchainFactory /*implements ToolchainFactory */{

//    public Toolchain createToolchain(BuildContext context) throws MisconfiguredToolchainException {
//        DefaultNetbeansToolchain jtc = new DefaultNetbeansToolchain();
//        boolean retrieve = context.retrieve(jtc);
//        if (retrieve) {
//            return jtc;
//        }
//        return null;
//    }
//
//    public ToolchainPrivate createToolchain(ToolchainModel model) throws MisconfiguredToolchainException {
//        DefaultNetbeansToolchain jtc = new DefaultNetbeansToolchain();
//        Xpp3Dom dom = (Xpp3Dom)model.getConfiguration();
//        Xpp3Dom javahome = dom.getChild(DefaultNetbeansToolchain.KEY_INSTALL_DIR);
//        if (javahome == null) {
//            throw new MisconfiguredToolchainException("NetBeans toolchain without the " + DefaultNetbeansToolchain.KEY_INSTALL_DIR + " configuration element.");
//        }
//        File normal = new File(javahome.getValue());
//        if (normal.exists()) {
//            jtc.setInstallDir(javahome.getValue());
//        } else {
//            throw new MisconfiguredToolchainException("Non-existing NetBeans installation configuration at " + normal.getAbsolutePath());
//        }
//        
//        //now populate the provides section.
//        //TODO possibly move at least parts to a utility method or abstract implementation.
//        dom = (Xpp3Dom)model.getProvides();
//        Xpp3Dom[] provides = dom.getChildren();
//        for (int i = 0; i < provides.length; i++) {
//            String key = provides[i].getName();
//            String value = provides[i].getValue();
//            if (value == null) {
//                throw new MisconfiguredToolchainException("Provides token '" + key + "' doesn't have any value configured.");
//            }
//            if ("version".equals(key)) {
//                jtc.addProvideToken(key, RequirementMatcherFactory.createVersionMatcher(value));
//            } else {
//                jtc.addProvideToken(key, RequirementMatcherFactory.createExactMatcher(value));
//            }
//        }
//        return jtc;
//    }
//
//    public ToolchainPrivate createDefaultToolchain() {
//        return null;
//    }

}
