# Two real games, one hosting and one joining over a real connection, like a friend joining your world. The guest tries everything a non-op shouldn't be able to do.
#   gradlew build -Psmoke -x test   then   python tools/run_lan_test.py
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
TIMEOUT = 480


def main():
    host = subprocess.Popen([sys.executable, os.path.join(HERE, 'run_real_client.py'), 'lanhost'])
    time.sleep(20)
    guest = subprocess.Popen([sys.executable, os.path.join(HERE, 'run_real_client.py'), 'guest'])
    end = time.time() + TIMEOUT
    for p in (guest, host):
        try:
            p.wait(timeout=max(1, end - time.time()))
        except subprocess.TimeoutExpired:
            p.kill()
    print('done')
    return 0


if __name__ == '__main__':
    sys.exit(main())
