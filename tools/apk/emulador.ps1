<#
  emulador.ps1 - Abre el emulador Android y (opcional) instala y abre el APK.
  Lo instala la skill "apk-ctrl-shift-b". Lee appId y la carpeta de la app de apk.config.json.

    powershell -ExecutionPolicy Bypass -File tools\apk\emulador.ps1               -> solo abre el emulador
    powershell -ExecutionPolicy Bypass -File tools\apk\emulador.ps1 -Instalar     -> instala el ultimo APK de <app>\apk y lo abre
    ... -Instalar -Apk C:\ruta\app.apk                                             -> instala ese APK
    ... -Avd Pixel_8                                                               -> usa ese dispositivo virtual
  Si hay un celular conectado por USB (depuracion USB activada), se usa ese.
  Requisito unico: un dispositivo virtual creado en Android Studio > Device Manager.
#>
param(
  [switch]$Instalar,
  [string]$Apk = '',
  [string]$Avd = ''
)
# Continue: en PowerShell 5 los avisos de adb por stderr se vuelven error con 'Stop'.
$ErrorActionPreference = 'Continue'

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$cfg = $null
$cfgRuta = Join-Path $repo 'apk.config.json'
if (Test-Path $cfgRuta) { $cfg = Get-Content $cfgRuta -Raw -Encoding UTF8 | ConvertFrom-Json }
$AppId = if ($cfg -and $cfg.appId) { $cfg.appId } else { '' }
$carpeta = if ($cfg -and $cfg.carpeta -and $cfg.carpeta -ne '.') { Join-Path $repo $cfg.carpeta } else { $repo }

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emu = Join-Path $sdk 'emulator\emulator.exe'
if (-not (Test-Path $adb)) { Write-Host "[ERROR] No encuentro adb en $adb. Instala 'Android SDK Platform-Tools' en Android Studio > SDK Manager." -ForegroundColor Red; exit 1 }

function Get-Dispositivos {
  (& $adb devices) | Select-String -Pattern '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value }
}

& $adb start-server 2> $null | Out-Null
$disp = @(Get-Dispositivos)

if ($disp.Count -eq 0) {
  if (-not (Test-Path $emu)) { Write-Host "[ERROR] No encuentro el emulador en $emu. Instala 'Android Emulator' en Android Studio > SDK Manager > SDK Tools." -ForegroundColor Red; exit 1 }
  $avds = @(& $emu -list-avds | Where-Object { $_ -and $_ -notmatch '^INFO' })
  if ($avds.Count -eq 0) {
    Write-Host "[ERROR] No hay dispositivos virtuales. Crea uno (una sola vez) en Android Studio > Device Manager > '+' (p. ej. Pixel 7, API 34)." -ForegroundColor Red
    exit 1
  }
  if (-not $Avd) { $Avd = $avds[0] }
  Write-Host "Dispositivos virtuales: $($avds -join ', ')"
  Write-Host "Abriendo emulador '$Avd'..." -ForegroundColor Cyan
  Start-Process -FilePath $emu -ArgumentList @('-avd', $Avd, '-netdelay', 'none', '-netspeed', 'full') | Out-Null
  Write-Host 'Esperando a que Android termine de arrancar (1-2 min la primera vez)...'
  & $adb wait-for-device
  $t = 0
  do {
    Start-Sleep -Seconds 3; $t += 3
    $boot = (& $adb shell getprop sys.boot_completed 2> $null) -join ''
  } while ($boot.Trim() -ne '1' -and $t -lt 240)
  if ($boot.Trim() -ne '1') { Write-Host '[ERROR] El emulador no termino de arrancar en 4 minutos.' -ForegroundColor Red; exit 1 }
  Write-Host 'Emulador listo.' -ForegroundColor Green
  $disp = @(Get-Dispositivos)
} else {
  Write-Host "Ya hay dispositivo conectado: $($disp -join ', ')"
}

if (-not $Instalar) { exit 0 }

$serial = $disp[0]
if ($disp.Count -gt 1) { Write-Host "Hay varios dispositivos; se usa $serial" -ForegroundColor Yellow }
if ($Apk) { $archivo = Get-Item $Apk -ErrorAction SilentlyContinue }
else { $archivo = Get-ChildItem (Join-Path $carpeta 'apk\*.apk') -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1 }
if (-not $archivo) { Write-Host '[ERROR] No hay APK para instalar. Genera uno con Ctrl+Shift+B.' -ForegroundColor Red; exit 1 }

Write-Host "Instalando $($archivo.Name) en $serial..." -ForegroundColor Cyan
$salida = (& $adb -s $serial install -r $archivo.FullName 2>&1) -join "`n"
Write-Host $salida
if ($salida -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' -and $AppId) {
  Write-Host 'La app instalada tiene otra firma (GitHub vs este PC, o release vs debug).' -ForegroundColor Yellow
  Write-Host 'Para instalar hay que desinstalarla: se BORRAN los datos de la app en ESTE dispositivo.' -ForegroundColor Yellow
  if ((Read-Host 'Desinstalar y reinstalar? (s/N)').Trim().ToLower() -ne 's') { exit 1 }
  & $adb -s $serial uninstall $AppId | Out-Null
  $salida = (& $adb -s $serial install $archivo.FullName 2>&1) -join "`n"
  Write-Host $salida
}
if ($salida -notmatch 'Success') { Write-Host '[ERROR] No se pudo instalar el APK.' -ForegroundColor Red; exit 1 }

if ($AppId) {
  & $adb -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
  Write-Host "App abierta ($AppId)." -ForegroundColor Green
} else {
  Write-Host 'Instalada. (Pon "appId" en apk.config.json para que se abra sola.)' -ForegroundColor Green
}
exit 0
