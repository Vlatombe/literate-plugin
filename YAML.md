# YAML Parser for Literate plugin

YAML can be used to describe the build model.

Here are some examples illustrating the different use cases you can cover with the Literate plugin.

## Simple project : Helloworld

    build: echo Hello world
    
## Several build commands in sequence

    build:
      - echo Hello world
      - echo Hello yourself
      
## Building on multiple environments (matrix projects)

    environments:
      - linux
      - windows
      - osx
    build: echo Hello world
     
## Using different commands on different environments

    environments:
      - linux
      - windows
      - osx
    build:
      linux: echo Hello Linux
      windows: echo Hello Windows
      osx: echo Hello Mac OS X
  
## Complex environments
 
 Just like in Markdown, you can describe complex environments by nesting declarations.
 
     environments:
       linux:
         java-1.6:
           - maven-2.2.1
           - maven-3.0.4
           - maven-3.1.0
         java-1.7:
           - maven-2.2.1
           - maven-3.0.4
           - maven-3.0.5
           - maven-3.1.0
       osx:
         java-1.6:
           - maven-3.0.4
           - maven-3.0.5
         java-1.7:
           - maven-3.0.5
           - maven-3.1.0
       windows:
         java-1.6:
           - maven-3.0.3
           - maven-3.0.4
         java-1.7:
           - maven-2.2.1
           - maven-3.1.0
     build:
       windows: call mvn.bat --version
       java-1.6: mvn --version
       java-1.7: mvn --version