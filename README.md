#NetBeans Repository plugin

The plugin is capable of populating the local or remote maven repository with module jars and NBM files from a given NetBeans installation. Useful for
module development, modules with public APIs are separated from those without a public API.
See the `populate` goal and the {{{./repository.html}HOWTO document}} for details.

 To get access to a repository with NetBeans.org module artifacts and metadata, add [http://bits.netbeans.org/maven2/](http://bits.netbeans.org/maven2/) repository to your project POM
or the repository manager you are using. The repository hosts binaries of NetBeans 6.5 and later.

 Also see: [Maven NBM development FAQs](http://wiki.netbeans.org/NetBeansDeveloperFAQ#Mavenized_Builds)
 
## HOWTO: Upload NetBeans release binaries to a Maven repository

There is a `populate` goal that converts the NetBeans module information into Maven metadata
and is capable of uploading the module jar file, javadoc, sources and nbm files to local
and remote repositories. 

For a binary-only upload (jar files and nbm files only), it's possible to download the bits from official sites. See below for URLs.

For the complete upload, you will need to checkout the sources of the IDE/Platform you
want to upload. See this FAQ entry on [HowTo checkout sources from Hg](http://wiki.netbeans.org/DevFaqAccessSourcesUsingMercurial)


 The goal has multiple input parameters that link the goal to binaries prepared beforehand.

* `netbeansInstallDirectory` designates the base directory where resides the NetBeans installation
that shall be uploaded. It can be either [downloaded as zip](http://www.netbeans.org/downloads/index.html) or built from sources.
Run `ant` in your local hg clone to build the distribution at `nbbuild/netbeans` sundirectory.

* `netbeansNbmDirectory` designates the base directory where the nbm files are located.
Run `ant build-nbms` in your local `hg clone` to build the nbms at `nbbuild/nbms` directory or download it from the
[http://updates.netbeans.org/netbeans](http://updates.netbeans.org/netbeans) space eg.
final *6.5* version at [http://updates.netbeans.org/netbeans/*6.5*/final/uc/](http://updates.netbeans.org/netbeans/6.5/final/uc/).
Use a tool like `wget` to download the complete directory tree to a directory designated by the `netbeansNbmDirectory` parameter.

* `netbeansSourcesDirectory` designates the base directory containing zip files with module jar sources.
Run `ant build-source-zips` in your local hg clone to build the nbms at `nbbuild/build/source-zips` directory.

* `netbeansJavadocDirectory` designates the base directory containing zip files with javadoc zips for modules with public apis.
Run `ant build-javadoc` in your local hg clone to build the nbms at `nbbuild/build/javadoc` directory.

* To have external dependencies correctly identified by their real GAV, you will need to download the index from central (or other) repository using the `download` goal.



