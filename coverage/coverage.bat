@echo off
setlocal

set cobertura=C:\tools\java\cobertura\cobertura-1.9.4.1
set coverage=%~dp0
set project=%coverage%..\

REM instrument
call %cobertura%\cobertura-instrument.bat --datafile %coverage%/cobertura.ser --destination %coverage%instrumented %project%build/main %project%build/test

REM run test
java -cp %cobertura%\cobertura.jar;%coverage%instrumented;%project%build/main;%project%build/test -Dnet.sourceforge.cobertura.datafile=%coverage%cobertura.ser T 1>log

REM report
call %cobertura%\cobertura-report.bat --datafile %coverage%cobertura.ser --destination %coverage%/report --format html %project%\src %project%\test

