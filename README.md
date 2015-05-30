#NetBeans Repository plugin

The plugin is capable of populating the local or remote maven repository with module jars and NBM files from a given NetBeans installation. Useful for
module development, modules with public APIs are separated from those without a public API.
See the `populate` goal and the {{{./repository.html}HOWTO document}} for details.

 To get access to a repository with NetBeans.org module artifacts and metadata, add [http://bits.netbeans.org/maven2/](http://bits.netbeans.org/maven2/) repository to your project POM
or the repository manager you are using. The repository hosts binaries of NetBeans 6.5 and later.

 Also see: [Maven NBM development FAQs](http://wiki.netbeans.org/NetBeansDeveloperFAQ#Mavenized_Builds)
