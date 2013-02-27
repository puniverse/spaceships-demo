cd dist
java -Djava.library.path=./lib/ -Dco.paralleluniverse.spacebase.license.file=../spacebase-lite.lic -Xmx2000m -verbose:gc -jar spaceships.jar -classpath lib/*.jar