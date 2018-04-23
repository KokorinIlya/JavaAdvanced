cd .\..\
javac -d ".\out\production\java-advanced-2018" -cp ".\artifacts\JarImplementorTest.jar;.\lib\*;" java\ru\ifmo\rain\kokorin\implementor\Implementor.java
cd .\out\production\java-advanced-2018\
jar xf .\..\..\..\artifacts\JarImplementorTest.jar info\kgeorgiy\java\advanced\implementor\Impler.class info\kgeorgiy\java\advanced\implementor\JarImpler.class info\kgeorgiy\java\advanced\implementor\ImplerException.class
jar cfm Implementor.jar .\..\..\..\scripts\IndependentManifest.txt ru\ifmo\rain\kokorin\implementor\*.class info\kgeorgiy\java\advanced\implementor\*.class
cd .\..\..\..\scripts\
rmdir .\..\out\production\java-advanced-2018\info /s