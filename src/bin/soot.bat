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

"\usr\lib\jvm\java-8-oraclein\javaw"   "-Dptolemy.ptII.dir=%PTII%"    -classpath "/home/thierry/.ptolemyII:/home/thierry/.ptolemyII/*:%PTII%/contrib/contrib.jar:%PTII%:%PTII%/lib/sootclasses.jar::/usr/lib/jvm/java-8-oracle/jre/lib/rt.jar:/usr/lib/jvm/java-8-oracle/jre/lib/jce.jar" soot.Main   %params%
pause
