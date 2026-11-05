$ErrorActionPreference = 'Stop'
$labRoot = 'E:/codex-build/eco-preview-validation-20261106/server'
$classpath = @("$labRoot/versions/26.2/shiroha-26.2.jar")
$classpath += Get-ChildItem "$labRoot/libraries" -Recurse -Filter '*.jar' | ForEach-Object FullName
$classpath += Get-ChildItem "$labRoot/plugins" -File -Filter '*.jar' | ForEach-Object FullName
$classes = Join-Path $PSScriptRoot '../../build/preview-probe'
New-Item -ItemType Directory -Force $classes | Out-Null
$argsFile = Join-Path $PSScriptRoot '../../build/preview-probe.args'
$lines = @('--release','21','-encoding','UTF-8','-classpath',('"'+(($classpath -join ';') -replace '\\','/')+'"'),'-d',('"'+($classes -replace '\\','/')+'"'),('"'+((Join-Path $PSScriptRoot 'PreviewProbe.java') -replace '\\','/')+'"'))
[IO.File]::WriteAllLines($argsFile,$lines,[Text.UTF8Encoding]::new($false))
& 'D:/java/bin/javac.exe' "@$argsFile"
if($LASTEXITCODE -ne 0) {exit $LASTEXITCODE}
Copy-Item (Join-Path $PSScriptRoot 'plugin.yml') $classes
& 'D:/java/bin/jar.exe' --create --file "$labRoot/plugins/PreviewProbe.jar" -C $classes .
exit $LASTEXITCODE
