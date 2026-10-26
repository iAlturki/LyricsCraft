[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }[0]
function Await-WinRT($winRtAsync, $returnType) {
    if (-not $winRtAsync) { return $null }
    try {
        $asTask = $asTaskGeneric.MakeGenericMethod($returnType)
        $task = $asTask.Invoke($null, @($winRtAsync))
        if ($task.Wait(1200)) { return $task.Result }
    } catch {}
    return $null
}
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType = WindowsRuntime] | Out-Null
$manager = Await-WinRT ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if (-not $manager) { exit 1 }
function Get-ActiveMediaSession($mgr) {
    try {
        $sessions = $mgr.GetSessions()
        foreach ($s in $sessions) {
            try {
                $pInfo = $s.GetPlaybackInfo()
                if ($pInfo -and $pInfo.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing) {
                    $m = Await-WinRT ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                    if ($m -and $m.Title -and $m.Title -notmatch 'TikTok|GDI\+|Default IME|MSCTFIME') {
                        return @($s, $pInfo, $m)
                    }
                }
            } catch {}
        }
        foreach ($s in $sessions) {
            try {
                $pInfo = $s.GetPlaybackInfo()
                if ($pInfo -and $pInfo.PlaybackStatus -ne [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Closed) {
                    $m = Await-WinRT ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                    if ($m -and $m.Title -and $m.Artist -and $m.Title -notmatch 'TikTok|GDI\+|Default IME|MSCTFIME') {
                        return @($s, $pInfo, $m)
                    }
                }
            } catch {}
        }
        $curr = $mgr.GetCurrentSession()
        if ($curr) {
            $pInfo = $curr.GetPlaybackInfo()
            $m = Await-WinRT ($curr.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            if ($m -and $m.Title -and $m.Title -notmatch 'TikTok|GDI\+|Default IME|MSCTFIME') {
                return @($curr, $pInfo, $m)
            }
        }
    } catch {}
    return $null
}
while ($true) {
    try {
        $active = Get-ActiveMediaSession $manager
        if ($active) {
            $session = $active[0]
            $info = $active[1]
            $media = $active[2]
            $tl = $session.GetTimelineProperties()
            $status = if ($info) { $info.PlaybackStatus.ToString() } else { 'Closed' }
            $pos = if ($tl) {
                if ($status -eq 'Playing' -and $tl.LastUpdatedTime) {
                    $elapsed = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds
                    $rate = if ($info.PlaybackRate) { [double]$info.PlaybackRate } else { 1.0 }
                    [long]($tl.Position.TotalMilliseconds + ($elapsed * $rate))
                } else {
                    [long]$tl.Position.TotalMilliseconds
                }
            } else { 0 }
            $dur = if ($tl) { [long]$tl.EndTime.TotalMilliseconds } else { 0 }
            $title = if ($media -and $media.Title) { $media.Title.Trim() } else { '' }
            $artist = if ($media -and $media.Artist) { $media.Artist.Trim() } else { '' }
            $album = if ($media -and $media.AlbumTitle) { $media.AlbumTitle.Trim() } else { '' }
            [Console]::Out.WriteLine("LP|$status|$pos|$dur|$artist|$title|$album")
            [Console]::Out.Flush()
        } else {
            [Console]::Out.WriteLine('LP|NONE|0|0|||')
            [Console]::Out.Flush()
        }
    } catch {}
    Start-Sleep -Milliseconds 150
}
