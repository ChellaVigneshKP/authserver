"""Source identity captured before execution, including the actual dirty worktree."""
import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def source_provenance():
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=ROOT)
    files = sorted(set(git('ls-files', '-co', '--exclude-standard', '-z').decode().split('\0')) - {''})
    digest = hashlib.sha256()
    for filename in files:
        path = ROOT / filename
        if path.is_file():
            digest.update(filename.encode()+b'\0'+path.read_bytes()+b'\0')
    status = git('status', '--porcelain').decode()
    return dict(source_commit=git('rev-parse', 'HEAD').decode().strip(),
                source_tree_sha256=digest.hexdigest(), working_tree_dirty=bool(status),
                tracked_diff_sha256=hashlib.sha256(git('diff', 'HEAD', '--binary')).hexdigest())
