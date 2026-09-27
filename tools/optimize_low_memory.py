"""Back up and apply KITTY 0.2 defaults without replacing a custom personality."""
import json
import shutil
import sqlite3
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'data'

def upgrade(data):
    config_path=data/'config.json'
    if not config_path.exists():raise ValueError('Run SETUP_KITTY.bat first.')
    stamp=datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup=data/('backup-before-0.2-'+stamp);backup.mkdir()
    for name in ('config.json','personality.txt','model.lock.json','model-fast.lock.json'):
        if (data/name).exists():shutil.copy2(data/name,backup/name)
    if (data/'kitty.sqlite3').exists():
        with sqlite3.connect(data/'kitty.sqlite3') as source,sqlite3.connect(backup/'kitty.sqlite3') as target:source.backup(target)
    config=json.loads(config_path.read_text(encoding='utf-8'))
    config.update(context_window=4096,max_tokens=160,detail_max_tokens=512,history_turns=3,history_days=0,memory_limit=6,document_limit=1)
    config.setdefault('owner_name','Virat')
    temporary=config_path.with_suffix('.new');temporary.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8');temporary.replace(config_path)
    personality=data/'personality.txt'
    if personality.exists():
        original=personality.read_text(encoding='utf-8')
        if 'Virat' not in original:
            identity="Project identity: Virat created KITTY AI as his personal assistant. Address him as Sir. Qwen supplies the underlying model weights.\n"
            personality.write_text(identity+original,encoding='utf-8')
    return backup

def main():
    print('Stop and restart both KITTY windows around this upgrade. Existing models, token, memories and custom personality are retained.')
    try:backup=upgrade(DATA)
    except ValueError as e:raise SystemExit(str(e))
    print('Backup:',backup)
    print('0.2 profile ready: full archive, three recent context turns, concise answers. Say "in detail" for longer replies.')
    print('Optional: DOWNLOAD_FAST_MODEL.bat, then START_MODEL_FAST.bat for the 2B model. Run only one model at a time.')
if __name__=='__main__':main()
