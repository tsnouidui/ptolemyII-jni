@echo off
rem This script was automatically generated by makebat.
rem DO NOT EDIT.  Instead, edit ptinvoke.in, and run make.

if "%PTII%" == "" set PTII=/mnt/hgfs/proj/cxb-jni/ptolemyII-jni/src
set params=
:start
if "%1" == "" goto stop
set params=%params% %1
shift
goto start
:stop

"\usr\lib\jvm\java-8-oraclein\javaw"   "-Djava.security.policy=${PTII}/bin/policy.all" "-Doutrigger.spacename=JavaSpaces" "-Dcom.sun.jini.lookup.groups=public" "-Djava.rmi.server.codebase=http://localhost:4444/" "-Dpython.home=%PTII%/lib" -Xmx1024M "-Dptolemy.ptII.dir=%PTII%"    -classpath "/home/thierry/.ptolemyII:/home/thierry/.ptolemyII/*:%PTII%/contrib/contrib.jar:%PTII%:%PTII%/lib/diva.jar:%PTII%/ptolemy/distributed/jini/jar/jini-core.jar:%PTII%/ptolemy/distributed/jini/jar/jini-ext.jar:%PTII%/ptolemy/distributed/jini/jar/sun-util.jar:%PTII%/lib/ptCal.jar:%PTII%/lib/java_cup.jar:%PTII%/lib/chic.jar:%PTII%/lib/ptcolt.jar:%PTII%/vendors/misc/rxtx/RXTXcomm.jar:%PTII%/vendors/oracle/dbxml.jar:%PTII%/vendors/oracle/db.jar:%PTII%/lib/g4ltl.jar:%PTII%/lib/org-openide-util.jar:%PTII%/lib/org-openide-util-lookup.jar:%PTII%/lib/org-netbeans-api-visual.jar:%PTII%/vendors/misc/itext/iText.jar:%PTII%/lib/sootclasses.jar::/usr/lib/jvm/java-8-oracle/jre/lib/rt.jar:/usr/lib/jvm/java-8-oracle/jre/lib/jce.jar:%PTII%/lib/ptjacl.jar:%PTII%/lib/javax.mail.jar:%PTII%/lib/js.jar:%PTII%/lib/org.apache.oltu.oauth2.client-1.0.1-SNAPSHOT.jar:%PTII%/lib/org.apache.oltu.oauth2.common-1.0.1-SNAPSHOT.jar:%PTII%/lib/socketio.jar:%PTII%/lib/jcerti.jar:%PTII%/lib/jna-4.0.0-variadic.jar:%PTII%/vendors/misc/joystick/Joystick.jar:%PTII%/lib/jsoup-1.7.3.jar:%PTII%/ptolemy/actor/lib/excel/jxl.jar:%PTII%/lib/jython.jar:%PTII%/lib/kieler.jar:%PTII%/lib/guava.jar:/gdk.jar:%PTII%/lib/org.apache.oltu.oauth2.client-1.0.1-SNAPSHOT.jar:%PTII%/lib/org.apache.oltu.oauth2.common-1.0.1-SNAPSHOT.jar:%PTII%/lib/socketio.jar:%PTII%/ptolemy/actor/lib/database/mysql-connector-java-5.1.6-bin.jar:%PTII%/ptolemy/actor/lib/database/ojdbc5.jar:%PTII%/lib/oscP5.jar:%PTII%/lib/PDFRenderer.jar:%PTII%/ptolemy/actor/ptalon/antlr/antlr.jar:%PTII%/lib/mapss.jar:%PTII%/lib/protobuf-java-2.4.1.jar:%PTII%/ptserver/lib/hessian-4.0.7.jar:%PTII%/lib/jetty-all-8.1.5-v20120716.jar:%PTII%/lib/javax.servlet-api-3.0.1.jar:%PTII%/ptserver/lib/wmqtt.jar:%PTII%/lib/jetty-all-8.1.5-v20120716.jar:%PTII%/lib/javax.servlet-api-3.0.1.jar:%PTII%/lib/smack.jar:%PTII%/lib/smackx.jar:%PTII%/lib/saxon8.jar:%PTII%/lib/saxon8-dom.jar:/usr/lib/jvm/java-8-oracle/jre/../lib/tools.jar:%PTII%/ptolemy/domains/ptinyos/lib/jdom.jar:%PTII%/ptolemy/domains/ptinyos/lib/nesc.jar:%PTII%/lib/hazelcast-3.2.3.jar:%PTII%/lib/jackson-annotations-2.2.2.jar:%PTII%/lib/jackson-core-2.2.2.jar:%PTII%/lib/jackson-databind-2.2.2.jar:%PTII%/lib/java-xmlhttprequest-20140923.jar:%PTII%/lib/netty-all-4.0.21.Final.jar:%PTII%/lib/vertx-core-2.1.4.jar:%PTII%/lib/vertx-hazelcast-2.1.4.jar:%PTII%/lib/vertx-platform-2.1.4.jar" ptolemy.actor.gui.PtExecuteApplication   %params%
pause
