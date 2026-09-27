"""Measure actual first-text and total response time on your own running laptop."""
import argparse
import json
from pathlib import Path
import time
import uuid
from urllib.request import Request,urlopen
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--prompt',default='Give me three short tips for studying.');args=p.parse_args()
    config=json.loads((ROOT/'data/config.json').read_text());session='benchmark-'+uuid.uuid4().hex
    output=[]
    for run in range(2):
        body={'text':args.prompt,'session':session,'request_id':str(uuid.uuid4())};start=time.monotonic();first=None;done=None;kind=''
        req=Request('http://127.0.0.1:8765/v1/chat/stream',json.dumps(body).encode(),{'Content-Type':'application/json','Authorization':'Bearer '+config['token']})
        with urlopen(req,timeout=180) as response:
            for line in response:
                line=line.decode().strip()
                if line.startswith('event:'):kind=line[6:].strip()
                if line.startswith('data:'):
                    event=json.loads(line[5:])
                    if kind=='token' and first is None:first=round(time.monotonic()-start,3)
                    if kind=='done':done=event;break
        row={'run':run+1,'first_text_seconds':first,'total_seconds':round(time.monotonic()-start,3),'mode':(done or {}).get('mode'),'usage':(done or {}).get('usage')}
        output.append(row);print(json.dumps(row,indent=2))
    (ROOT/'data/benchmark-last.json').write_text(json.dumps(output,indent=2))
    print('Compare after changing model/threads, with other heavy apps closed. These two turns have different history; this is an end-to-end check, not a controlled CPU benchmark.')
if __name__=='__main__':main()
