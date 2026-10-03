"""Cloud-only entry point; refuses to fall back to laptop pairing or local inference."""
import os
from pathlib import Path
from .kitty import Brain, Server, initialize


def validate_environment():
    if os.environ.get('FIREBASE_AUTH_EMULATOR_HOST'):
        raise ValueError('Firebase Auth emulator is forbidden in the hosted service')
    project = os.environ.get('FIREBASE_PROJECT_ID', '').strip()
    origin = os.environ.get('KITTY_PUBLIC_URL', '').strip()
    if not project or not origin.startswith('https://'):
        raise ValueError('Set FIREBASE_PROJECT_ID and KITTY_PUBLIC_URL before starting the cloud service')


def main():
    validate_environment()
    home = Path(os.environ.get('KITTY_DATA_DIR', '/data'))
    home.mkdir(parents=True,exist_ok=True)
    initialize(home)
    brain = Brain(home)
    if not brain.accounts.firebase_project:
        raise RuntimeError('Firebase authentication is mandatory in the hosted service')
    server = Server(('0.0.0.0', int(os.environ.get('PORT', '8765'))), brain)
    print('KITTY cloud service ready; provider keys are set through the verified admin account.', flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()


if __name__ == '__main__': main()
