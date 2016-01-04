#/bin/sh

pwd >&2
ls -1 -R >&2
echo "-------------------------------" >&2
echo $1 >&2
ls -1 -R $1 >&2

cat <<EOF
[{
  "type": "nomad_parse_events_1_0",
  "mainFileUri": "",
  "parserInfo": {
  "version":"1.0",
  "name":"testParser1"
},
  "events": [],
  "parsingStatus": "SUCCESS"
}]
EOF
