param(
    # Incision-Test 提供 Bukkit 命令与 JMH benchmark；脚本副本位于 TabooLib，故显式声明外部项目路径。
    [string]$IncisionTestRoot = "E:\Code\TabooLib\Incision-Test",
    [string]$ServerRoot = "E:\Minecraft-Server\incisionTest",
    [string]$OnlyServer = "",
    [ValidateSet("", "jvmti", "instrumentation")]
    [string]$OnlyBackend = "",
    [int]$TimeoutMinutes = 10
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path -LiteralPath $IncisionTestRoot).Path
$tabooLibRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..\..\..")).Path
$serverRoot = (Resolve-Path -LiteralPath $ServerRoot).Path
$pluginJar = Join-Path $projectRoot "build\libs\Incision-Test-1.0.0.jar"
$resultRoot = Join-Path $tabooLibRoot "module\incision\performance\jmh-2026-07-28"
$rawRoot = Join-Path $resultRoot "raw"
$consoleRoot = Join-Path $resultRoot "console"

# 每个节点使用此前功能矩阵验证过的 JVM；Backend 通过系统属性强制，禁止 auto 掩盖失败。
$matrix = @(
    [pscustomobject]@{ Name = "1.12.2-paper"; Directory = "run-1.12.2"; Jar = "paper-1.12.2-1620.jar"; Java = "D:\Java\zulu-8\bin\java.exe"; JavaMajor = 8 },
    [pscustomobject]@{ Name = "1.16.5-paper"; Directory = "run-1.16.5-paper"; Jar = "paper-1.16.5-794.jar"; Java = "D:\Java\zulu16.32.15-ca-jdk16.0.2-win_x64\bin\java.exe"; JavaMajor = 16 },
    [pscustomobject]@{ Name = "1.20.6-paper"; Directory = "run-1.20.6-paper"; Jar = "paper-1.20.6-151.jar"; Java = "D:\Java\azul-21.0.10\bin\java.exe"; JavaMajor = 21 },
    [pscustomobject]@{ Name = "1.21.11-paper"; Directory = "run-1.21.11-paper"; Jar = "paper-1.21.11-132.jar"; Java = "D:\Java\azul-21.0.10\bin\java.exe"; JavaMajor = 21 },
    [pscustomobject]@{ Name = "26.1.2-spigot"; Directory = "run-26.1.2-spigot"; Jar = "spigot-26.1.2.jar"; Java = "D:\Java\zulu25.32.21-ca-jdk25.0.2-win_x64\bin\java.exe"; JavaMajor = 25 },
    [pscustomobject]@{ Name = "26.2-paper"; Directory = "run-26.2-paper"; Jar = "paper-26.2-84.jar"; Java = "D:\Java\zulu25.32.21-ca-jdk25.0.2-win_x64\bin\java.exe"; JavaMajor = 25 }
)

$backends = if ($OnlyBackend) { @($OnlyBackend) } else { @("jvmti", "instrumentation") }
$nodes = if ($OnlyServer) { @($matrix | Where-Object Name -EQ $OnlyServer) } else { $matrix }
if ($nodes.Count -eq 0) {
    throw "Unknown server node: $OnlyServer"
}
if (-not (Test-Path -LiteralPath $pluginJar)) {
    throw "Plugin jar not found: $pluginJar"
}

New-Item -ItemType Directory -Force -Path $rawRoot, $consoleRoot | Out-Null

function Add-ProcessArgument {
    param([System.Diagnostics.ProcessStartInfo]$StartInfo, [string]$Value)
    $StartInfo.ArgumentList.Add($Value)
}

function Invoke-JmhNode {
    param($Node, [string]$Backend)

    $serverDirectory = Join-Path $serverRoot $Node.Directory
    $serverJar = Join-Path $serverDirectory $Node.Jar
    $pluginTarget = Join-Path $serverDirectory "plugins\Incision-Test-1.0.0.jar"
    $resultFile = Join-Path $rawRoot "$($Node.Name)-java$($Node.JavaMajor)-$Backend.json"
    $consoleFile = Join-Path $consoleRoot "$($Node.Name)-java$($Node.JavaMajor)-$Backend.log"
    if (-not (Test-Path -LiteralPath $serverJar)) { throw "Server jar not found: $serverJar" }
    if (-not (Test-Path -LiteralPath $Node.Java)) { throw "Java not found: $($Node.Java)" }

    # local-dev 坐标版本固定，必须清掉该模块的内容寻址副本，确保加载刚发布到 Maven Local 的 Incision。
    $incisionCache = Join-Path $serverDirectory "cache\taboolib\top.maplex.incisiontest"
    if (Test-Path -LiteralPath $incisionCache) {
        Get-ChildItem -LiteralPath $incisionCache -Filter "incision-6.3.0-local-dev-*.jar" -File |
            Remove-Item -Force
    }
    Copy-Item -LiteralPath $pluginJar -Destination $pluginTarget -Force
    if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile -Force }
    Set-Content -LiteralPath $consoleFile -Value "" -Encoding UTF8

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Node.Java
    $startInfo.WorkingDirectory = $serverDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    Add-ProcessArgument $startInfo "-Xms512M"
    Add-ProcessArgument $startInfo "-Xmx1536M"
    Add-ProcessArgument $startInfo "-Dfile.encoding=UTF-8"
    Add-ProcessArgument $startInfo "-Dtaboolib.incision.backend=$Backend"
    Add-ProcessArgument $startInfo "-Dincision.test.autoRun=false"
    Add-ProcessArgument $startInfo "-Djdk.attach.allowAttachSelf=true"
    Add-ProcessArgument $startInfo "-Dincision.jmh.output=$resultFile"
    Add-ProcessArgument $startInfo "-Dincision.jmh.warmupIterations=3"
    Add-ProcessArgument $startInfo "-Dincision.jmh.measurementIterations=7"
    Add-ProcessArgument $startInfo "-Dincision.jmh.iterationMillis=500"
    if ($Node.JavaMajor -ge 21) {
        Add-ProcessArgument $startInfo "-XX:+EnableDynamicAgentLoading"
    }
    Add-ProcessArgument $startInfo "-jar"
    Add-ProcessArgument $startInfo $serverJar
    Add-ProcessArgument $startInfo "nogui"

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $stdoutTask = $process.StandardOutput.ReadLineAsync()
    $stderrTask = $process.StandardError.ReadLineAsync()
    $startedAt = [DateTime]::UtcNow
    $commandSent = $false
    $stopSent = $false
    $succeeded = $false
    $failed = $false

    try {
        while (-not $process.HasExited) {
            foreach ($stream in @("stdout", "stderr")) {
                $task = if ($stream -eq "stdout") { $stdoutTask } else { $stderrTask }
                # Incision debug 模式会在启动时密集输出织入日志。一次只消费一行会让 Done/JMH-FAILED
                # 在管道中积压数分钟，因此每轮持续排空已经完成的读取任务，直到追上生产端。
                while ($task.IsCompleted) {
                    $line = $task.GetAwaiter().GetResult()
                    if ($stream -eq "stdout") {
                        $stdoutTask = $process.StandardOutput.ReadLineAsync()
                        $task = $stdoutTask
                    } else {
                        $stderrTask = $process.StandardError.ReadLineAsync()
                        $task = $stderrTask
                    }
                    if ($null -eq $line) { break }
                    Add-Content -LiteralPath $consoleFile -Value $line -Encoding UTF8
                    # 控制台只回显矩阵进度和基准协议，完整调试日志仍保存在 console 文件中。
                    # 这可避免十二节点运行时宿主终端被数万条逐方法织入日志淹没。
                    if ($line -match 'Running Java|Loading Paper|This server is running|Done \(|JMH-') {
                        Write-Host "[$($Node.Name)/$Backend] $line"
                    }

                    if (-not $commandSent -and ($line -match 'Done \(' -or $line -match 'For help, type')) {
                        $process.StandardInput.WriteLine("incisiontest jmh")
                        $process.StandardInput.Flush()
                        $commandSent = $true
                    }
                    if ($line -match 'JMH-END') { $succeeded = $true }
                    if ($line -match 'JMH-FAILED') { $failed = $true }
                    if (($succeeded -or $failed) -and -not $stopSent) {
                        $process.StandardInput.WriteLine("stop")
                        $process.StandardInput.Flush()
                        $stopSent = $true
                    }
                }
            }

            if (([DateTime]::UtcNow - $startedAt).TotalMinutes -gt $TimeoutMinutes) {
                throw "JMH node timed out after $TimeoutMinutes minutes"
            }
            Start-Sleep -Milliseconds 50
        }
        $process.WaitForExit()
    } finally {
        if (-not $process.HasExited) {
            if (-not $stopSent) { $process.StandardInput.WriteLine("stop") }
            if (-not $process.WaitForExit(15000)) { $process.Kill($true) }
        }
        $process.Dispose()
    }

    if ($failed) { throw "JMH reported failure; inspect $consoleFile" }
    if (-not $succeeded) { throw "JMH did not report completion; inspect $consoleFile" }
    if (-not (Test-Path -LiteralPath $resultFile)) { throw "JMH result missing: $resultFile" }
}

foreach ($node in $nodes) {
    foreach ($backend in $backends) {
        Write-Host "Starting $($node.Name) / Java $($node.JavaMajor) / $backend"
        Invoke-JmhNode -Node $node -Backend $backend
    }
}

Write-Host "JMH matrix complete: $resultRoot"
