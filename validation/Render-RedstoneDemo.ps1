param([string]$Directory=(Join-Path $PSScriptRoot 'builder-redstone'))
$ErrorActionPreference='Stop'
$culture=[Globalization.CultureInfo]::InvariantCulture
$probe=Join-Path (Split-Path (Get-Command ffmpeg).Source) 'ffprobe.exe'
$info=(& $probe -v error -show_entries format=duration -of json (Join-Path $Directory 'builder-demo-raw.mp4'))|ConvertFrom-Json
$duration=[double]::Parse($info.format.duration,$culture)
$chapters=@(Import-Csv -Delimiter "`t" -LiteralPath (Join-Path $Directory 'spectre-tour-chapters.tsv'))
$proofText=Get-Content -LiteralPath (Join-Path $Directory 'builder-demo-pass.txt') -Raw
if($proofText -notmatch 'Imported actual \.schem: (\d+) blocks'){throw 'Recording proof file is missing its schematic count'}
$blockCount=$Matches[1]
function Stamp([double]$seconds){$t=[TimeSpan]::FromSeconds($seconds);return ('{0}:{1:00}:{2:00}.{3:00}' -f [int][Math]::Floor($t.TotalHours),$t.Minutes,$t.Seconds,[int][Math]::Floor($t.Milliseconds/10))}
$total=Stamp $duration
$lines=@(
'[Script Info]','ScriptType: v4.00+','PlayResX: 1280','PlayResY: 720','ScaledBorderAndShadow: yes','[V4+ Styles]',
'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
'Style: Main,Segoe UI,25,&H00F5F7FA,&H00FFFFFF,&H00101923,&H00101923,-1,0,0,0,100,100,0,0,1,0,0,7,24,24,16,1',
'Style: Small,Segoe UI,16,&H00C6CBD2,&H00FFFFFF,&H00101923,&H00101923,0,0,0,0,100,100,0,0,1,0,0,7,24,24,16,1',
'Style: Accent,Segoe UI,23,&H00CBDC56,&H00FFFFFF,&H00101923,&H00101923,-1,0,0,0,100,100,0,0,1,0,0,7,24,24,16,1',
'[Events]','Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text',
"Dialogue: 0,0:00:00.00,$total,Main,,0,0,0,,{\pos(24,10)}SPECTRE / SCHEMATIC BUILDER",
"Dialogue: 0,0:00:00.00,$total,Small,,0,0,0,,{\pos(24,44)}First-person / Creative single-player / 1x speed")
$labels=@{
'Import schematic'=@('01 / Import the schematic','Actual .schem file / Materials and block count')
'Schematic preview'=@('02 / Preview the build','Ghost preview / Empty build site')
'Construction'=@('03 / Build at normal speed','Uncut construction / Walking, aiming and pauses preserved')
'Verified layout'=@('04 / Layout and settings verified',"$blockCount exact block states checked on the server")
'Furnace and hoppers'=@('06 / Automated smelting','Input and fuel delivered / Smelted output collected')
'All tests passed'=@('All functional tests passed','Exact layout, settings, item flow and machine behavior checked')
'Power on'=@('05 / Switch it on','Real lever interaction / Repeater set to delay 4')
'Piston extended'=@('05 / Piston extended','Powered circuit tested successfully')
'Power off'=@('06 / Switch it off','Real lever interaction / Watch the piston retract')
'Piston retracted'=@('06 / Piston retracted','On/off behavior verified / Repeater setting preserved')
}
for($i=0;$i -lt $chapters.Count;$i++){
    $from=Stamp ([double]::Parse($chapters[$i].seconds,$culture))
    $until=if($i+1 -lt $chapters.Count){Stamp ([double]::Parse($chapters[$i+1].seconds,$culture))}else{$total}
    $label=$labels[$chapters[$i].scene]
    if($chapters[$i].scene.StartsWith('Supply: ')){$label=@('05 / Supply test inputs',$chapters[$i].scene.Substring(8)+' / Actual container GUI')}
    if($chapters[$i].scene.StartsWith('Test: ')){$label=@('06 / Functional test',$chapters[$i].scene.Substring(6)+' / Real lever interaction')}
    if($chapters[$i].scene.StartsWith('Passed: ')){$label=@('Functional test passed',$chapters[$i].scene.Substring(8)+' / Server result verified')}
    if($chapters[$i].scene.StartsWith('Inspect: ')){$label=@('07 / Inspect the output',$chapters[$i].scene.Substring(9))}
    if(!$label){throw "Unknown recording chapter: $($chapters[$i].scene)"}
    $lines+="Dialogue: 0,$from,$until,Accent,,0,0,0,,{\an9\pos(1256,10)}$($label[0])"
    $lines+="Dialogue: 0,$from,$until,Small,,0,0,0,,{\an9\pos(1256,44)}$($label[1])"
}
$lines|Set-Content -LiteralPath (Join-Path $Directory 'redstone-demo.ass') -Encoding utf8
Push-Location -LiteralPath $Directory
try{
    # No trimming, timestamp scaling, frame-rate conversion or construction cuts.
    & ffmpeg -hide_banner -loglevel warning -y -i builder-demo-raw.mp4 -vf 'drawbox=x=0:y=0:w=iw:h=66:color=0x0D141E@0.96:t=fill,ass=redstone-demo.ass' -an -c:v libx264 -preset medium -crf 22 -pix_fmt yuv420p -movflags +faststart spectre-redstone-realtime.mp4
    if($LASTEXITCODE -ne 0){throw 'Redstone demo export failed'}
    $t=($duration-3).ToString('0.000',$culture)
    & ffmpeg -hide_banner -loglevel error -y -ss $t -i spectre-redstone-realtime.mp4 -frames:v 1 -update 1 redstone-poster.png
    if($LASTEXITCODE -ne 0){throw 'Poster extraction failed'}
    Write-Output "Exported full continuous recording at 1x: $($duration.ToString('0.0',$culture)) seconds."
}finally{Pop-Location}
