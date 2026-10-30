param(
  [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
  [int]$Port = 55432
)
$ErrorActionPreference = 'Stop'
# Only use with the isolated test fixture; no Supabase secrets are involved.
function Query([string]$Sql) {
  $result = & $Psql -h 127.0.0.1 -p $Port -U playbit_test -d playbit -v ON_ERROR_STOP=1 -At -c $Sql
  if ($LASTEXITCODE -ne 0) { throw 'RLS test query failed' }
  return ($result | Select-Object -Last 1)
}
$match = Query 'select id from matches where status=''completed'' limit 1'
if (-not $match) { throw 'Complete a test match before checking participant RLS.' }
$participant = Query "select user_id from match_players where match_id='$match' limit 1"
$stranger = [guid]::NewGuid().ToString()
$visible = Query "set role authenticated; set request.jwt.claim.sub='$participant'; select count(*) from matches where id='$match';"
if ($visible -ne '1') { throw 'Participant could not read own match' }
$hidden = Query "set role authenticated; set request.jwt.claim.sub='$stranger'; select count(*) from matches where id='$match';"
if ($hidden -ne '0') { throw 'Nonparticipant could read private match' }
$grants = Query "select count(*) from information_schema.role_table_grants where grantee in ('anon','authenticated') and table_schema='public' and table_name in ('questions','question_attempts','xp_events','reports');"
if ($grants -ne '0') { throw 'Sensitive tables have browser grants' }
$writeGrant = Query "select count(*) from information_schema.role_table_grants where grantee in ('anon','authenticated') and table_schema='public' and privilege_type in ('INSERT','UPDATE','DELETE');"
if ($writeGrant -ne '0') { throw 'Browser has authoritative write privileges' }
Write-Output 'PASS: participant read, nonparticipant isolation, answer secrecy, and no browser writes.'
