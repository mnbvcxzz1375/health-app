param(
  [switch]$SkipMedicationSidecar
)

$ErrorActionPreference = "Stop"

function Write-Step {
  param([string]$Message)
  Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
  param([string]$Message)
  Write-Host "OK  $Message" -ForegroundColor Green
}

function Get-ProjectRoot {
  return Split-Path -Parent $PSCommandPath
}

function Test-PortListening {
  param([int]$Port)

  try {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -First 1
    return $null -ne $listener
  } catch {
    return $false
  }
}

function Test-PortBindable {
  param([int]$Port)

  $listener = $null
  try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse("127.0.0.1"), $Port)
    $listener.Start()
    return $true
  } catch {
    return $false
  } finally {
    if ($listener -ne $null) {
      try {
        $listener.Stop()
      } catch {
      }
    }
  }
}

function Resolve-FrontendPort {
  $candidates = @(4173, 4273, 4373, 4473)
  foreach ($candidate in $candidates) {
    if (Test-PortListening -Port $candidate) {
      return $candidate
    }
    if (Test-PortBindable -Port $candidate) {
      return $candidate
    }
  }

  throw "Unable to resolve an available frontend port."
}

function Resolve-ServicePort {
  param(
    [int[]]$Candidates,
    [string]$Name
  )

  foreach ($candidate in $Candidates) {
    if (Test-PortListening -Port $candidate) {
      return $candidate
    }
  }

  foreach ($candidate in $Candidates) {
    if (Test-PortBindable -Port $candidate) {
      return $candidate
    }
  }

  throw "Unable to resolve an available port for $Name."
}

function Initialize-LogFile {
  param(
    [string]$Name,
    [string]$LogFile
  )

  try {
    Set-Content -Path $LogFile -Value ""
    return $LogFile
  } catch {
    $fallback = Join-Path (Split-Path -Parent $LogFile) ("{0}-{1}.log" -f $Name, (Get-Date -Format "yyyyMMdd-HHmmss"))
    Set-Content -Path $fallback -Value ""
    return $fallback
  }
}

function Stop-PortListeners {
  param([int]$Port)

  try {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
  } catch {
    return
  }

  $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($pidValue in $pids) {
    try {
      Stop-Process -Id $pidValue -Force -ErrorAction Stop
    } catch {
    }
  }
}

function Wait-HttpReady {
  param(
    [string]$Url,
    [int]$TimeoutSeconds = 90
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return $true
      }
    } catch {
    }
    Start-Sleep -Seconds 2
  }

  return $false
}

function Resolve-JavaHome {
  param(
    [string[]]$Candidates,
    [string]$Role
  )

  foreach ($candidate in $Candidates) {
    if ([string]::IsNullOrWhiteSpace($candidate)) {
      continue
    }
    $javaExe = Join-Path $candidate "bin\java.exe"
    if (Test-Path $javaExe) {
      return $candidate
    }
  }

  throw "Unable to resolve JAVA_HOME for $Role."
}

function Test-RedisReady {
  $redisCli = Get-Command redis-cli -ErrorAction SilentlyContinue
  if ($null -eq $redisCli) {
    return $false
  }

  try {
    $ping = & $redisCli.Source ping 2>$null
    return ($ping -join "").Trim() -eq "PONG"
  } catch {
    return $false
  }
}

function Ensure-Redis {
  Write-Step "Checking Redis"

  if (Test-RedisReady) {
    Write-Ok "Redis is ready"
    return
  }

  $redisServer = Get-Command redis-server -ErrorAction SilentlyContinue
  if ($null -eq $redisServer) {
    throw "Redis is not running and redis-server was not found on PATH."
  }

  Start-Process -FilePath $redisServer.Source -WindowStyle Hidden | Out-Null
  Start-Sleep -Seconds 2

  if (-not (Test-RedisReady)) {
    throw "Redis could not be started automatically."
  }

  Write-Ok "Redis started"
}

function Start-ManagedProcess {
  param(
    [string]$Name,
    [string]$ScriptBody,
    [string]$LogFile,
    [int]$Port,
    [string]$HealthUrl,
    [int]$TimeoutSeconds = 90
  )

  if (Test-PortListening -Port $Port) {
    if (Wait-HttpReady -Url $HealthUrl -TimeoutSeconds 8) {
      Write-Ok "$Name already listening on port $Port"
      return
    }
    Stop-PortListeners -Port $Port
    Start-Sleep -Seconds 1
  }

  $resolvedLogFile = Initialize-LogFile -Name $Name -LogFile $LogFile
  $effectiveScriptBody = if ($resolvedLogFile -eq $LogFile) {
    $ScriptBody
  } else {
    $ScriptBody.Replace($LogFile, $resolvedLogFile)
  }

  Start-Process powershell -ArgumentList @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-Command",
    $effectiveScriptBody
  ) -WindowStyle Hidden | Out-Null

  if (-not (Wait-HttpReady -Url $HealthUrl -TimeoutSeconds $TimeoutSeconds)) {
    if (Test-PortListening -Port $Port -and (Wait-HttpReady -Url $HealthUrl -TimeoutSeconds 12)) {
      Write-Ok "$Name is ready"
      return
    }
    throw "$Name failed to become ready. Check $resolvedLogFile"
  }

  Write-Ok "$Name is ready"
}

$root = Get-ProjectRoot
$frontendDir = Get-ChildItem -Path $root -Directory |
  Where-Object { Test-Path (Join-Path $_.FullName "package.json") } |
  Select-Object -First 1 -ExpandProperty FullName

if (-not $frontendDir) {
  throw "Could not locate frontend directory under $root"
}
$backendJavaDir = Join-Path $root "backend-java"
$postureBackendDir = Join-Path $root "posture-backend"
$postureInferenceDir = Join-Path $root "posture-inference-service"

$backendJavaLog = Join-Path $root "backend-java-run.log"
$postureBackendLog = Join-Path $root "posture-backend-run.log"
$postureInferenceLog = Join-Path $root "posture-inference-run.log"
$medicationSidecarLog = Join-Path $root "local-medication-api-run.log"
$frontendLog = Join-Path $root "frontend-run.log"
$frontendPort = Resolve-FrontendPort
$postureBackendPort = Resolve-ServicePort -Candidates @(8081, 8180, 8080) -Name "posture-backend"

$java21Home = Resolve-JavaHome -Role "backend-java" -Candidates @(
  $env:JAVA21_HOME,
  "C:\Users\12774\.vscode\extensions\redhat.java-1.53.0-win32-x64\jre\21.0.10-win32-x86_64",
  $env:JAVA_HOME
)

$java8Home = Resolve-JavaHome -Role "posture-backend" -Candidates @(
  $env:JAVA8_HOME,
  "E:\Java\jdk-1.8"
)

Ensure-Redis

Write-Step "Starting backend-java"
$backendJavaScript = @"
`$env:JAVA_HOME = '$java21Home'
`$env:Path = '$java21Home\bin;' + `$env:Path
`$serverEnvPath = Join-Path '$frontendDir' 'server\.env'
if (Test-Path `$serverEnvPath) {
  Get-Content `$serverEnvPath | ForEach-Object {
    if ([string]::IsNullOrWhiteSpace(`$_) -or `$_.Trim().StartsWith('#')) {
      return
    }
    `$pair = `$_ -split '=', 2
    if (`$pair.Length -eq 2) {
      [Environment]::SetEnvironmentVariable(`$pair[0].Trim(), `$pair[1].Trim(), 'Process')
    }
  }
}
Set-Location '$backendJavaDir'
.\mvnw.cmd clean spring-boot:run *> '$backendJavaLog'
"@
Start-ManagedProcess -Name "backend-java" -ScriptBody $backendJavaScript -LogFile $backendJavaLog -Port 3302 -HealthUrl "http://127.0.0.1:3302/api/health" -TimeoutSeconds 180

Write-Step "Starting posture-backend"
$postureBackendScript = @"
`$env:JAVA_HOME = '$java8Home'
`$env:Path = '$java8Home\bin;' + `$env:Path
`$env:SERVER_PORT = '$postureBackendPort'
Set-Location '$postureBackendDir'
.\mvnw.cmd spring-boot:run *> '$postureBackendLog'
"@
Start-ManagedProcess -Name "posture-backend" -ScriptBody $postureBackendScript -LogFile $postureBackendLog -Port $postureBackendPort -HealthUrl "http://127.0.0.1:$postureBackendPort/actuator/health"

Write-Step "Starting posture-inference-service"
$postureInferenceScript = @"
Set-Location '$postureInferenceDir'
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 *> '$postureInferenceLog'
"@
Start-ManagedProcess -Name "posture-inference-service" -ScriptBody $postureInferenceScript -LogFile $postureInferenceLog -Port 8000 -HealthUrl "http://127.0.0.1:8000/health"

if (-not $SkipMedicationSidecar) {
  $medicationSidecarDir = Join-Path $root "local_medication_api"
  Write-Step "Starting local_medication_api"
  $medicationSidecarScript = @"
Set-Location '$root'
python -m uvicorn local_medication_api.app:app --host 127.0.0.1 --port 8011 *> '$medicationSidecarLog'
"@
  Start-ManagedProcess -Name "local_medication_api" -ScriptBody $medicationSidecarScript -LogFile $medicationSidecarLog -Port 8011 -HealthUrl "http://127.0.0.1:8011/health"
}

Write-Step "Starting frontend"
$frontendScript = @"
Set-Location '$frontendDir'
`$env:VITE_DEV_PORT = '$frontendPort'
`$env:VITE_DEV_POSTURE_PROXY_TARGET = 'http://127.0.0.1:$postureBackendPort'
npm run dev *> '$frontendLog'
"@
Start-ManagedProcess -Name "frontend" -ScriptBody $frontendScript -LogFile $frontendLog -Port $frontendPort -HealthUrl "http://127.0.0.1:$frontendPort"

Write-Host ""
Write-Host "Stack ready:" -ForegroundColor Yellow
Write-Host "  frontend               http://127.0.0.1:$frontendPort"
Write-Host "  backend-java           http://127.0.0.1:3302"
Write-Host "  posture-backend        http://127.0.0.1:$postureBackendPort"
Write-Host "  posture-inference      http://127.0.0.1:8000"
if (-not $SkipMedicationSidecar) {
  Write-Host "  local medication api   http://127.0.0.1:8011"
}
Write-Host ""
Write-Host "Logs:"
Write-Host "  $backendJavaLog"
Write-Host "  $postureBackendLog"
Write-Host "  $postureInferenceLog"
if (-not $SkipMedicationSidecar) {
  Write-Host "  $medicationSidecarLog"
}
Write-Host "  $frontendLog"
