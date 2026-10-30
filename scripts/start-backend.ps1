param([string]$EnvFile = "$PSScriptRoot\..\.env")
$ErrorActionPreference = 'Stop'
# Prefer the requested locally provisioned Java 21 runtime when present.
$localJdk = "$PSScriptRoot\..\.tools\jdk21\jdk-21.0.13.0.2+1"
if (Test-Path -LiteralPath "$localJdk\bin\java.exe") { $env:JAVA_HOME = (Resolve-Path -LiteralPath $localJdk).Path }
if (Test-Path -LiteralPath $EnvFile) {
  foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
  }
}
if ($env:PLAYBIT_WINDOWS_TCP_FALLBACK -eq '1') {
  # A nonexistent Unix socket directory makes Java's selector use its supported TCP fallback.
  # This is for hosts whose Windows AF_UNIX implementation fails on connect.
  $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' -Djdk.net.unixdomain.tmpdir=../.tools/no-unix-sockets').Trim()
}
$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($maven) { & $maven.Source -f "$PSScriptRoot\..\backend\pom.xml" spring-boot:run }
elseif (Test-Path -LiteralPath "$PSScriptRoot\..\.tools\apache-maven-3.9.11\bin\mvn.cmd") { & "$PSScriptRoot\..\.tools\apache-maven-3.9.11\bin\mvn.cmd" -f "$PSScriptRoot\..\backend\pom.xml" spring-boot:run }
else { throw 'Install Maven 3.9+ and Java 21, or run docker compose --env-file .env up --build.' }
