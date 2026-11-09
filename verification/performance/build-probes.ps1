param([string]$LabRoot = 'E:/EcoEnchants/audit/optimization-20261110/server')
$ErrorActionPreference = 'Stop'
$classpath = @("$LabRoot/versions/26.2/shiroha-26.2.jar")
$classpath += Get-ChildItem "$LabRoot/libraries" -Recurse -Filter '*.jar' | ForEach-Object FullName
$classpath += Get-ChildItem "$LabRoot/plugins" -File -Filter '*.jar' | ForEach-Object FullName
foreach ($probe in @(@('OptCompare', $PSScriptRoot), @('PreviewProbe', "$PSScriptRoot/../preview"))) {
    $classes = Join-Path $PSScriptRoot "../../build/$($probe[0])-classes"
    New-Item -ItemType Directory -Force $classes | Out-Null
    $argsFile = Join-Path $PSScriptRoot "../../build/$($probe[0]).args"
    $lines = @('--release','21','-encoding','UTF-8','-classpath',('"'+(($classpath -join ';') -replace '\\','/')+'"'),'-d',('"'+($classes -replace '\\','/')+'"'),('"'+((Join-Path $probe[1] "$($probe[0]).java") -replace '\\','/')+'"'))
    [IO.File]::WriteAllLines($argsFile,$lines,[Text.UTF8Encoding]::new($false))
    & 'D:/java/bin/javac.exe' "@$argsFile"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Copy-Item (Join-Path $probe[1] 'plugin.yml') $classes
    & 'D:/java/bin/jar.exe' --create --file "$LabRoot/plugins/$($probe[0]).jar" -C $classes .
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
