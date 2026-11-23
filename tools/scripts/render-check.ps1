<#
.SYNOPSIS
    Check the deployed HOPA backend and its Postgres, wake a sleeping service,
    and redeploy when main has moved.

.DESCRIPTION
    One command answers three questions: is the public gateway healthy, is its
    database reachable, and is the live commit the one on main?

    Health comes from GET <url>/v1/health. The Render API supplies the live
    deploy, the commit, the service state and the Postgres state; it needs
    RENDER_API_KEY in the environment, and the key is never printed.

    A free web service sleeps after ~15 minutes idle. The first probe wakes it
    and the script waits up to -WakeTimeoutSec; the report says whether the
    service was cold.

    -Redeploy triggers a deploy of the branch HEAD (or -Commit).
    -RedeployIfStale does that only when the live commit differs from the local
    HEAD. Both wait for the deploy and re-check health. -DryRun prints the
    request instead of sending it.

.PARAMETER Service
    Render service name or id. Default: hopa-backend-test.

.PARAMETER Database
    Render Postgres name or id. Default: hopa-backend-test-db.

.PARAMETER Url
    Public base URL. Defaults to the service URL from Render, then
    HOPA_BACKEND_URL, then https://hopa-backend-test.onrender.com.

.PARAMETER Redeploy
    Trigger a deploy now and wait for it.

.PARAMETER RedeployIfStale
    Trigger a deploy only when the live commit differs from the local HEAD.

.PARAMETER Commit
    Deploy this commit instead of the branch HEAD. Implies -Redeploy.

.PARAMETER ClearCache
    Ask Render to clear the build cache.

.PARAMETER WakeTimeoutSec
    How long to wait for a sleeping service to answer. Default: 150.

.PARAMETER DeployTimeoutSec
    How long to wait for a triggered deploy to go live. Default: 900.

.PARAMETER NoApi
    Health only. Does not read or write the Render API.

.PARAMETER DryRun
    Report what a deploy would send without calling Render.

.PARAMETER Json
    Print one JSON object instead of the human report.

.EXAMPLE
    .\tools\scripts\render-check.ps1

.EXAMPLE
    .\tools\scripts\render-check.ps1 -RedeployIfStale

.EXAMPLE
    .\tools\scripts\render-check.ps1 -Redeploy -ClearCache

.EXAMPLE
    .\tools\scripts\render-check.ps1 -NoApi -Url https://hopa-backend-test.onrender.com

.NOTES
    RENDER_API_KEY is read from the environment and never echoed.
#>
[CmdletBinding()]
param(
    [string]$Service = "hopa-backend-test",
    [string]$Database = "hopa-backend-test-db",
    [string]$Url,
    [switch]$Redeploy,
    [switch]$RedeployIfStale,
    [string]$Commit,
    [switch]$ClearCache,
    [int]$WakeTimeoutSec = 150,
    [int]$DeployTimeoutSec = 900,
    [switch]$NoApi,
    [switch]$DryRun,
    [switch]$Json
)

$ErrorActionPreference = "Stop"
$script:Name = "render-check"
$script:Api = "https://api.render.com/v1"
$script:ApiKey = $env:RENDER_API_KEY
$script:Problems = New-Object System.Collections.ArrayList
$script:Notes = New-Object System.Collections.ArrayList
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ($Commit) { $Redeploy = $true }

function Write-Status([string]$Text) {
    if ($Json) { [Console]::Error.WriteLine($Text) } else { Write-Host $Text }
}

function Add-Problem([string]$Text) {
    [void]$script:Problems.Add($Text)
    Write-Status "  ! $Text"
}

function Add-Note([string]$Text) {
    [void]$script:Notes.Add($Text)
}

function Stop-WithMessage([string]$Text, [int]$Code = 2) {
    [Console]::Error.WriteLine("$script:Name: $Text")
    exit $Code
}

function Get-ErrorBody($ErrorRecord) {
    try {
        $response = $ErrorRecord.Exception.Response
        if ($null -eq $response) { return "" }
        $stream = $response.GetResponseStream()
        if ($null -eq $stream) { return "" }
        $reader = New-Object System.IO.StreamReader($stream)
        $text = $reader.ReadToEnd()
        $reader.Close()
        return $text
    } catch {
        return ""
    }
}

function Invoke-RenderApi {
    param(
        [string]$Method,
        [string]$Path,
        $Body
    )
    if (-not $script:ApiKey) {
        throw "RENDER_API_KEY is not set; run with -NoApi for a health-only check"
    }
    $headers = @{
        Authorization = "Bearer $script:ApiKey"
        Accept        = "application/json"
        "User-Agent"  = "hopa-render-check/1.0"
    }
    $request = @{
        Uri        = "$script:Api$Path"
        Method     = $Method
        Headers    = $headers
        TimeoutSec = 60
    }
    if ($null -ne $Body) {
        $request.Body = ($Body | ConvertTo-Json -Compress)
        $request.ContentType = "application/json"
    }
    Invoke-RestMethod @request
}

function Resolve-RenderService([string]$NameOrId) {
    if ($NameOrId -match '^srv-') {
        return Invoke-RenderApi -Method GET -Path "/services/$NameOrId"
    }
    $list = Invoke-RenderApi -Method GET -Path "/services?name=$([uri]::EscapeDataString($NameOrId))&limit=20"
    $match = @($list | Where-Object { $_.service.name -eq $NameOrId }) |
        Select-Object -First 1
    if ($null -eq $match) {
        $match = @($list) | Select-Object -First 1
    }
    if ($null -eq $match) { return $null }
    return $match.service
}

function Resolve-RenderPostgres([string]$NameOrId) {
    if ($NameOrId -match '^dpg-') {
        return Invoke-RenderApi -Method GET -Path "/postgres/$NameOrId"
    }
    $list = Invoke-RenderApi -Method GET -Path "/postgres?limit=100"
    $match = @($list | Where-Object { $_.postgres.name -eq $NameOrId }) |
        Select-Object -First 1
    if ($null -eq $match) { return $null }
    return $match.postgres
}

function Get-DeployCommit($Deploy) {
    if ($null -ne $Deploy -and $null -ne $Deploy.commit -and $Deploy.commit.id) {
        return [string]$Deploy.commit.id
    }
    return $null
}

function Format-Age([string]$Iso) {
    if (-not $Iso) { return "" }
    try {
        $then = [datetime]::Parse($Iso).ToUniversalTime()
        $span = (Get-Date).ToUniversalTime() - $then
        if ($span.TotalMinutes -lt 1) { return "just now" }
        if ($span.TotalHours -lt 1) { return ("{0:N0}m ago" -f $span.TotalMinutes) }
        if ($span.TotalDays -lt 1) { return ("{0:N1}h ago" -f $span.TotalHours) }
        return ("{0:N0}d ago" -f $span.TotalDays)
    } catch {
        return $Iso
    }
}

function Invoke-HealthProbe([string]$BaseUrl, [int]$TimeoutSec) {
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    $uri = "$($BaseUrl.TrimEnd('/'))/v1/health"
    try {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec $TimeoutSec `
            -Headers @{ "User-Agent" = "hopa-render-check/1.0" }
        $clock.Stop()
        return [pscustomobject]@{
            Status  = [int]$response.StatusCode
            Body    = [string]$response.Content
            Seconds = $clock.Elapsed.TotalSeconds
            Error   = $null
        }
    } catch {
        $clock.Stop()
        $status = 0
        $body = ""
        $response = $_.Exception.Response
        if ($null -ne $response) {
            try { $status = [int]$response.StatusCode } catch { $status = 0 }
            $body = Get-ErrorBody $_
        }
        return [pscustomobject]@{
            Status  = $status
            Body    = $body
            Seconds = $clock.Elapsed.TotalSeconds
            Error   = $_.Exception.Message
        }
    }
}

function Test-HealthAnswered($Probe) {
    if ($null -eq $Probe) { return $false }
    if ($Probe.Status -eq 200) { return $true }
    # A 503 is the app itself reporting a down database, not a sleeping proxy.
    return ($Probe.Status -eq 503 -and $Probe.Body -match '"db"')
}

function Wait-Health([string]$BaseUrl, [int]$TimeoutSec) {
    $started = Get-Date
    $deadline = $started.AddSeconds([math]::Max(5, $TimeoutSec))
    $probe = $null
    $attempt = 0
    while ($true) {
        $attempt++
        $remaining = [int][math]::Max(10, ($deadline - (Get-Date)).TotalSeconds)
        $probe = Invoke-HealthProbe -BaseUrl $BaseUrl -TimeoutSec ([math]::Min(120, $remaining))
        if ((Test-HealthAnswered $probe) -or ((Get-Date) -ge $deadline)) { break }
        Write-Status ("  waiting for the service to wake ({0} s) ..." -f [int]((Get-Date) - $started).TotalSeconds)
        Start-Sleep -Seconds 5
    }
    $woke = (Test-HealthAnswered $probe) -and (($attempt -gt 1) -or ($probe.Seconds -gt 5))
    [pscustomobject]@{ Probe = $probe; Woke = $woke; Waited = ((Get-Date) - $started).TotalSeconds }
}

function Wait-RenderDeploy([string]$ServiceId, [string]$DeployId, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds([math]::Max(30, $TimeoutSec))
    $last = $null
    while ($true) {
        $deploy = Invoke-RenderApi -Method GET -Path "/services/$ServiceId/deploys/$DeployId"
        if ($null -ne $deploy.deploy) { $deploy = $deploy.deploy }
        if ($deploy.status -ne $last) {
            Write-Status "  deploy $DeployId -> $($deploy.status)"
            $last = $deploy.status
        }
        if ($deploy.status -in @("live", "build_failed", "update_failed", "canceled", "deactivated", "pre_deploy_failed")) {
            return $deploy
        }
        if ((Get-Date) -ge $deadline) { return $deploy }
        Start-Sleep -Seconds 10
    }
}

$result = [ordered]@{
    checkedAt         = (Get-Date).ToUniversalTime().ToString("o")
    serviceName       = $null
    serviceId         = $null
    serviceSuspended  = $null
    autoDeploy        = $null
    branch            = $null
    url               = $null
    liveDeployId      = $null
    liveCommit        = $null
    liveDeployStatus  = $null
    liveDeployAge     = $null
    newestDeployId    = $null
    newestDeployStatus = $null
    localBranch       = $null
    localHead         = $null
    localAhead        = $null
    stale             = $null
    healthStatus      = $null
    healthOk          = $null
    healthBody        = $null
    healthVersion     = $null
    healthSeconds     = $null
    healthWaited      = $null
    woke              = $null
    appDb             = $null
    databaseName      = $null
    databaseId        = $null
    databaseStatus    = $null
    databaseVersion   = $null
    databaseExpiresAt = $null
    databaseUrlEnv    = $null
    redeployed        = $false
    deployId          = $null
    deployStatus      = $null
    verdict           = "unknown"
    problems          = @()
    notes             = @()
}

# --- health only -----------------------------------------------------------

if ($NoApi) {
    $baseUrl = if ($Url) { $Url } elseif ($env:HOPA_BACKEND_URL) { $env:HOPA_BACKEND_URL } else { "https://hopa-backend-test.onrender.com" }
} else {
    if (-not $script:ApiKey -and -not $Url) {
        Stop-WithMessage "RENDER_API_KEY is not set. Set it, pass -Url, or use -NoApi for a health-only check."
    }

    $svc = $null
    if (-not $script:ApiKey) {
        Add-Note "RENDER_API_KEY is not set; service, deploy and database checks were skipped"
    }
    if ($script:ApiKey) {
        try {
            $svc = Resolve-RenderService $Service
        } catch {
            Stop-WithMessage "Render API: $($_.Exception.Message)"
        }
        if ($null -eq $svc) { Stop-WithMessage "no Render service named or id-matching '$Service'" }
        $result.serviceName = $svc.name
        $result.serviceId = $svc.id
        $result.serviceSuspended = [string]$svc.suspended
        $result.autoDeploy = [string]$svc.autoDeploy
        $result.branch = [string]$svc.branch
    }

    if ($Url) {
        $baseUrl = $Url
    } elseif ($svc -and $svc.serviceDetails -and $svc.serviceDetails.url) {
        $baseUrl = [string]$svc.serviceDetails.url
    } elseif ($env:HOPA_BACKEND_URL) {
        $baseUrl = $env:HOPA_BACKEND_URL
    } else {
        $baseUrl = "https://hopa-backend-test.onrender.com"
    }
}

$result.url = $baseUrl
Write-Status "$script:Name  $baseUrl"

# --- Render service, deploy and database -----------------------------------

$live = $null
if (-not $NoApi -and $svc) {
    try {
        $deploys = @(Invoke-RenderApi -Method GET -Path "/services/$($svc.id)/deploys?limit=10")
        $all = @($deploys | ForEach-Object { $_.deploy })
        $live = @($all | Where-Object { $_.status -eq "live" }) | Select-Object -First 1
        $newest = @($all) | Select-Object -First 1
        if ($newest) {
            $result.newestDeployId = $newest.id
            $result.newestDeployStatus = $newest.status
        }
        if ($live) {
            $result.liveDeployId = $live.id
            $result.liveDeployStatus = $live.status
            $result.liveCommit = Get-DeployCommit $live
            $result.liveDeployAge = Format-Age ([string]$live.createdAt)
        } else {
            Add-Problem "no live deploy is recorded for the service"
        }
        if ($newest -and $newest.status -eq "build_failed") {
            Add-Problem "the newest deploy $($newest.id) failed to build"
        }
    } catch {
        Add-Problem "could not read deploys: $($_.Exception.Message)"
    }

    try {
        $envVars = @(Invoke-RenderApi -Method GET -Path "/services/$($svc.id)/env-vars?limit=100")
        $keys = @($envVars | ForEach-Object { $_.envVar.key })
        $result.databaseUrlEnv = ($keys -contains "DATABASE_URL")
        if (-not $result.databaseUrlEnv) {
            Add-Problem "DATABASE_URL is not set on the service; the gateway will report db=unconfigured"
        }
    } catch {
        Add-Note "env vars unavailable: $($_.Exception.Message)"
    }

    try {
        $pg = Resolve-RenderPostgres $Database
        if ($null -eq $pg) {
            Add-Problem "no Render Postgres named or id-matching '$Database'"
        } else {
            $result.databaseName = $pg.name
            $result.databaseId = $pg.id
            $result.databaseStatus = [string]$pg.status
            $result.databaseVersion = [string]$pg.version
            $result.databaseExpiresAt = [string]$pg.expiresAt
            if ($pg.status -ne "available") {
                Add-Problem "database $($pg.name) is $($pg.status), not available"
            }
        }
    } catch {
        Add-Problem "could not read the database: $($_.Exception.Message)"
    }
}

# --- local commit ----------------------------------------------------------

if (-not $NoApi) {
    try {
        $branch = (& git -C $root rev-parse --abbrev-ref HEAD 2>$null | Select-Object -First 1)
        $head = (& git -C $root rev-parse HEAD 2>$null | Select-Object -First 1)
        if ($branch -and $head) {
            $result.localBranch = [string]$branch
            $result.localHead = [string]$head
            if ($result.liveCommit) {
                $result.stale = ($result.liveCommit -ne $head)
                if ($result.stale) {
                    try {
                        $ahead = & git -C $root rev-list --count "$($result.liveCommit)..$head" 2>$null
                        if ($ahead -match '^\d+$') { $result.localAhead = [int]$ahead }
                    } catch { }
                }
            }
        }
    } catch {
        Add-Note "git not available; cannot compare the live commit with a local HEAD"
    }
}

# --- wake and health -------------------------------------------------------

Write-Status "  probing health (waiting up to $WakeTimeoutSec s if asleep) ..."
$health = Wait-Health -BaseUrl $baseUrl -TimeoutSec $WakeTimeoutSec
$probe = $health.Probe
$result.healthStatus = $probe.Status
$result.healthSeconds = [math]::Round($probe.Seconds, 1)
$result.healthWaited = [math]::Round($health.Waited, 1)
$result.woke = [bool]$health.Woke
$result.healthBody = $probe.Body

$healthJson = $null
if ($probe.Body) {
    try { $healthJson = $probe.Body | ConvertFrom-Json } catch { $healthJson = $null }
}

if ($null -eq $healthJson) {
    if ($probe.Status -eq 0) {
        $reason = if ($probe.Error) { ": $($probe.Error)" } else { "" }
        Add-Problem "the service did not answer within $WakeTimeoutSec s$reason"
    } else {
        Add-Problem "health returned $($probe.Status) without a health body"
    }
} else {
    $result.healthOk = [bool]$healthJson.ok
    $result.healthVersion = [string]$healthJson.version
    $result.appDb = [string]$healthJson.db
    if (-not $healthJson.ok) { Add-Problem "health says ok=false" }
    if ($result.appDb -eq "down") {
        Add-Problem "the gateway cannot reach Postgres (db=down)"
    } elseif ($result.appDb -eq "unconfigured") {
        Add-Problem "DATABASE_URL is not configured on the service (db=unconfigured)"
    } elseif ($healthJson.db -and $result.appDb -ne "ok") {
        Add-Problem "unexpected database state from health: db=$($result.appDb)"
    }
}

if ($probe.Status -eq 503 -and $result.appDb -ne "down") {
    Add-Problem "health returned 503"
}

# --- redeploy --------------------------------------------------------------

$wantDeploy = $false
if ($Redeploy) { $wantDeploy = $true }
if ($RedeployIfStale -and $result.stale -eq $true) { $wantDeploy = $true }

if ($RedeployIfStale -and $null -eq $result.stale) {
    Add-Problem "cannot tell whether the deploy is stale; pass -Commit or run inside the git checkout"
}

if ($wantDeploy) {
    if (-not $svc) { Stop-WithMessage "redeploy needs the Render API and a resolved service" }

    $payload = [ordered]@{
        clearCache = $(if ($ClearCache) { "clear" } else { "do_not_clear" })
    }
    if ($Commit) { $payload["commitId"] = $Commit }
    $target = if ($Commit) { $Commit } else { "branch HEAD" }

    if ($DryRun) {
        $result.redeployed = $false
        $result.deployStatus = "dry-run"
        Write-Status "  dry run: would POST /services/$($svc.id)/deploys for $target with $($payload | ConvertTo-Json -Compress)"
        Add-Note "dry run; no deploy was triggered"
    } else {
        Write-Status "  deploying $target ..."
        try {
            $created = Invoke-RenderApi -Method POST -Path "/services/$($svc.id)/deploys" -Body $payload
            if ($null -ne $created -and $null -ne $created.deploy) { $created = $created.deploy }
            $result.redeployed = $true
            $result.deployId = if ($created) { [string]$created.id } else { $null }
            $result.deployStatus = if ($created) { [string]$created.status } else { "queued" }
            if (-not $result.deployId) {
                # A 202 carries no body; the newest deploy in the list is ours.
                $recent = @(Invoke-RenderApi -Method GET -Path "/services/$($svc.id)/deploys?limit=5")
                $newest = @($recent | ForEach-Object { $_.deploy }) | Select-Object -First 1
                if ($newest) {
                    $result.deployId = [string]$newest.id
                    $result.deployStatus = [string]$newest.status
                }
            }
            if (-not $result.deployId) {
                Add-Problem "Render accepted the deploy but returned no deploy id"
            } else {
                Write-Status "  queued deploy $($result.deployId)"
                $finished = Wait-RenderDeploy -ServiceId $svc.id -DeployId $result.deployId -TimeoutSec $DeployTimeoutSec
                $result.deployStatus = [string]$finished.status
                $result.liveDeployId = [string]$finished.id
                $result.liveDeployStatus = [string]$finished.status
                $result.liveDeployAge = Format-Age ([string]$finished.createdAt)
                if ($finished.status -ne "live") {
                    Add-Problem "the deploy finished as $($finished.status), not live"
                }
            }
        } catch {
            Add-Problem "deploy failed: $($_.Exception.Message)"
        }

        if ($result.deployStatus -eq "live") {
            Write-Status "  deploy is live; re-checking health ..."
            $again = Wait-Health -BaseUrl $baseUrl -TimeoutSec $WakeTimeoutSec
            $probe = $again.Probe
            $result.healthStatus = $probe.Status
            $result.healthSeconds = [math]::Round($probe.Seconds, 1)
            $result.healthWaited = [math]::Round($again.Waited, 1)
            $result.woke = [bool]$again.Woke
            $result.healthBody = $probe.Body
            $healthJson = $null
            if ($probe.Body) { try { $healthJson = $probe.Body | ConvertFrom-Json } catch { } }
            if ($null -eq $healthJson) {
                Add-Problem "health did not answer after the deploy"
            } else {
                $result.healthOk = [bool]$healthJson.ok
                $result.healthVersion = [string]$healthJson.version
                $result.appDb = [string]$healthJson.db
                $result.liveCommit = Get-DeployCommit $finished
                if ($result.liveCommit -and $result.localHead) {
                    $result.stale = ($result.liveCommit -ne $result.localHead)
                } else {
                    $result.stale = $null
                }
                if (-not $healthJson.ok) { Add-Problem "health says ok=false after the deploy" }
                if ($result.appDb -ne "ok") {
                    Add-Problem "database state after the deploy is '$($result.appDb)', not ok"
                }
            }
        }
    }
}

# --- verdict ---------------------------------------------------------------

$result.problems = @($script:Problems)
$result.notes = @($script:Notes)

if ($script:Problems.Count -eq 0) {
    $result.verdict = "OK"
} else {
    $result.verdict = "FAIL"
}

if ($Json) {
    $result | ConvertTo-Json -Depth 6
} else {
    if ($result.serviceName) {
        Write-Host ("  service     {0} ({1})" -f $result.serviceName, $result.serviceId)
    }
    if ($result.serviceSuspended) {
        Write-Host ("  state       {0}, auto-deploy {1}, branch {2}" -f $result.serviceSuspended, $result.autoDeploy, $result.branch)
    }
    if ($result.liveDeployId) {
        $shortCommit = if ($result.liveCommit) { $result.liveCommit.Substring(0, [math]::Min(8, $result.liveCommit.Length)) } else { "no commit" }
        Write-Host ("  deploy      {0}  {1}  {2}  {3}" -f $result.liveDeployId, $result.liveDeployStatus, $shortCommit, $result.liveDeployAge)
    }
    if ($result.newestDeployStatus -and $result.newestDeployStatus -ne $result.liveDeployStatus) {
        Write-Host ("  newest      {0}  {1}" -f $result.newestDeployId, $result.newestDeployStatus)
    }
    if ($result.localHead) {
        $shortHead = $result.localHead.Substring(0, [math]::Min(8, $result.localHead.Length))
        $staleText = ""
        if ($result.stale -eq $true) {
            $staleText = "  live deploy is behind"
            if ($null -ne $result.localAhead) { $staleText = "  local has $($result.localAhead) commit(s) the live deploy does not" }
        } elseif ($result.stale -eq $false) {
            $staleText = "  current"
        }
        Write-Host ("  local       {0} {1}{2}" -f $result.localBranch, $shortHead, $staleText)
    }
    if ($null -ne $result.healthStatus) {
        if ($result.healthStatus -gt 0) {
            $warm = if ($result.woke) { "cold start, answered after $($result.healthWaited)s" } else { "warm, $($result.healthSeconds)s" }
            $dbText = if ($result.appDb) { " db=$($result.appDb)" } else { "" }
            $versionText = if ($result.healthVersion) { " version=$($result.healthVersion)" } else { "" }
            Write-Host ("  health      {0} ok={1}{2}{3}  {4}" -f $result.healthStatus, $result.healthOk, $dbText, $versionText, $warm)
        } else {
            Write-Host ("  health      no answer after {0} s" -f $WakeTimeoutSec)
        }
    }
    if ($result.databaseName) {
        $expiry = ""
        if ($result.databaseExpiresAt) {
            try {
                $days = [math]::Floor(([datetime]::Parse($result.databaseExpiresAt).ToUniversalTime() - (Get-Date).ToUniversalTime()).TotalDays)
                if ($days -le 30) { $expiry = "  expires in $days day(s)" }
            } catch { }
        }
        Write-Host ("  database    {0}  {1}  Postgres {2}{3}" -f $result.databaseName, $result.databaseStatus, $result.databaseVersion, $expiry)
    }
    if ($null -ne $result.databaseUrlEnv) {
        $envText = if ($result.databaseUrlEnv) { "present" } else { "MISSING" }
        Write-Host ("  env         DATABASE_URL {0}" -f $envText)
    }
    if ($result.redeployed) {
        Write-Host ("  deploy run  {0}  {1}" -f $result.deployId, $result.deployStatus)
    } elseif ($result.deployStatus -eq "dry-run") {
        Write-Host "  deploy run  dry run, nothing sent"
    }
    foreach ($note in $script:Notes) { Write-Host "  note        $note" }
    Write-Host ("  verdict     {0}" -f $result.verdict)
    if ($result.stale -eq $true -and -not $wantDeploy) {
        Write-Host "  hint        run -RedeployIfStale to move the live deploy to main"
    }
}

if ($result.verdict -eq "OK") { exit 0 }
exit 1
