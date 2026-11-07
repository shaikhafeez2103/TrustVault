@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-22
set MAVEN_HOME=C:\Program Files\Apache\Maven\apache-maven-3.9.11\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
set MAVEN_OPTS=-javaagent:%USERPROFILE%\.m2\repository\net\bytebuddy\byte-buddy-agent\1.14.19\byte-buddy-agent-1.14.19.jar -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true

call "%MAVEN_HOME%\bin\mvn.cmd" %*
