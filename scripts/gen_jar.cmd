cd .\..\
javac -d ".\out\production\java-advanced-2018" -cp ".\artifacts\JarImplementorTest.jar;.\lib\*;" java\ru\ifmo\rain\kokorin\implementor\Implementor.java
cd .\out\production\java-advanced-2018\
jar cfm Implementor.jar .\..\..\..\scripts\Manifest.txt ru\ifmo\rain\kokorin\implementor\*.class
cd .\..\..\..\scripts\