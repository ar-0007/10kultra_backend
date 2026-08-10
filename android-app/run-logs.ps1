# Apna TV — live log viewer. App launch karke uske saare logs stream karta hai.
$adb = "C:\Users\Invinvcible\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.apnatv.iptv"

Write-Host "Device ka wait kar rahe hain..." -ForegroundColor Cyan
& $adb wait-for-device

Write-Host "App (re)install + launch..." -ForegroundColor Cyan
& $adb install -r "E:\Apna TV applicaton\app\build\outputs\apk\debug\app-debug.apk" | Out-Null
& $adb shell am force-stop $pkg
& $adb logcat -c
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 3

$p = (& $adb shell pidof $pkg).Trim()
Write-Host ""
Write-Host "================ LIVE LOGS (har ek line) — pid $p ================" -ForegroundColor Green
Write-Host "Band karne ke liye is window me Ctrl + C dabayein." -ForegroundColor Yellow
Write-Host ""

# App ke process ki har single log line, live:
& $adb logcat --pid=$p
