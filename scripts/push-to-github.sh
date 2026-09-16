#!/usr/bin/env bash
#
# Creates a GitHub repository and pushes this project to it.
#
#   GITHUB_TOKEN=ghp_... ./scripts/push-to-github.sh cursoid
#   GITHUB_TOKEN=ghp_... ./scripts/push-to-github.sh cursoid --public
#
# The token needs the `repo` scope, plus `workflow` to push .github/workflows.
#
set -uo pipefail

visibility=private
name=""
land_on_main=1

usage() {
  cat <<'EOF'
Creates a GitHub repository and pushes this project to it.

  GITHUB_TOKEN=<token> ./scripts/push-to-github.sh <name> [--public|--private]

  <name>       repository name to create, e.g. cursoid
  --public     public repo, so the Releases page is reachable without a login
  --private    private repo (the default)
  --as-is      keep the current branch layout instead of landing the code on main

The token needs the `repo` scope, plus `workflow` to push .github/workflows.
An existing repository of that name is reused rather than recreated.

By default the code lands on main, which only happens when main can fast-forward
to the current branch, so nothing is rewritten or discarded.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --public)  visibility=public ;;
    --private) visibility=private ;;
    --as-is)   land_on_main=0 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    *) name="$1" ;;
  esac
  shift
done

cd "$(dirname "$0")/.."

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
err()  { printf '\033[31m%s\033[0m\n' "$*" >&2; }
step() { printf '  %s\n' "$*"; }

token="${GITHUB_TOKEN:-${GITHUB_PAT:-${GH_TOKEN:-}}}"

if [ -z "$token" ]; then
  err "No token found in GITHUB_TOKEN, GITHUB_PAT or GH_TOKEN."
  step "Create one at https://github.com/settings/tokens with the 'repo'"
  step "and 'workflow' scopes, then re-run:"
  step ""
  step "GITHUB_TOKEN=<token> $0 <name>"
  exit 1
fi

if [ -z "$name" ]; then
  err "No repository name given."
  usage
  exit 1
fi

api() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS -o /tmp/gh-body.json -w '%{http_code}'
    -X "$method"
    -H "Authorization: Bearer $token"
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}" "https://api.github.com$path"
}

# --- who are we ---------------------------------------------------------------

code="$(api GET /user)"
if [ "$code" != "200" ]; then
  err "GitHub rejected the token (HTTP $code)."
  python3 -c "import json;print('  '+json.load(open('/tmp/gh-body.json')).get('message','')) " 2>/dev/null
  step "Check it has not expired and carries the 'repo' scope."
  exit 1
fi
owner="$(python3 -c "import json;print(json.load(open('/tmp/gh-body.json'))['login'])")"
say "Authenticated as $owner"

# --- create or reuse the repository -------------------------------------------

code="$(api GET "/repos/$owner/$name")"
if [ "$code" = "200" ]; then
  step "Repository $owner/$name already exists — reusing it."
else
  private=true
  [ "$visibility" = "public" ] && private=false
  body="$(python3 -c "
import json,sys
print(json.dumps({
  'name': sys.argv[1],
  'private': sys.argv[2] == 'true',
  'description': 'Cursoid — a native Android client for Cursor Cloud Agents',
  'has_issues': True,
  'has_wiki': False,
  'has_projects': False,
}))" "$name" "$private")"
  code="$(api POST /user/repos "$body")"
  if [ "$code" != "201" ]; then
    err "Could not create the repository (HTTP $code)."
    python3 -c "import json;d=json.load(open('/tmp/gh-body.json'));print('  '+d.get('message',''));[print('  -',e.get('message','')) for e in d.get('errors',[])]" 2>/dev/null
    exit 1
  fi
  step "Created $visibility repository $owner/$name"
fi

# --- push ---------------------------------------------------------------------

remote_url="https://github.com/$owner/$name.git"

if git remote get-url github >/dev/null 2>&1; then
  git remote set-url github "$remote_url"
else
  git remote add github "$remote_url"
fi

# Feed the token through GIT_ASKPASS rather than the remote URL, so it stays out
# of argv, out of .git/config, and out of the reflog.
askpass="$(mktemp)"
trap 'rm -f "$askpass"' EXIT
printf '#!/bin/sh\nprintf %%s "$GIT_PUSH_TOKEN"\n' > "$askpass"
chmod 700 "$askpass"
export GIT_ASKPASS="$askpass" GIT_PUSH_TOKEN="$token"
push_url="https://x-access-token@github.com/$owner/$name.git"

# --- decide what the default branch should be ---------------------------------

current="$(git rev-parse --abbrev-ref HEAD)"
default_branch="$current"

if [ "$land_on_main" -eq 1 ] && [ "$current" != "main" ]; then
  if ! git rev-parse --verify --quiet main >/dev/null; then
    git branch main "$current"
    default_branch=main
    step "Created main at the tip of $current"
  elif git merge-base --is-ancestor main "$current"; then
    git branch -f main "$current"
    default_branch=main
    step "Fast-forwarded main to $current"
  else
    step "main has commits of its own, so the default branch stays $current."
    step "Pass --as-is to silence this, or merge main yourself first."
  fi
fi

say "Pushing every branch and tag"
if ! git push --all "$push_url" 2>&1 | sed 's/^/  /'; then
  err "Push failed. If it mentions 'workflow', the token lacks the 'workflow' scope."
  exit 1
fi
git push --tags "$push_url" 2>&1 | sed 's/^/  /'

code="$(api PATCH "/repos/$owner/$name" "$(python3 -c "
import json,sys;print(json.dumps({'default_branch': sys.argv[1]}))" "$default_branch")")"
if [ "$code" = "200" ]; then
  step "Default branch set to $default_branch"
else
  step "Could not set the default branch (HTTP $code); do it in repo settings."
fi

say "Done"
step "Repository:  https://github.com/$owner/$name"
step "CI runs:     https://github.com/$owner/$name/actions"
step "Clone it:    git clone $remote_url"
say "To get a Releases page with a tappable APK, add the signing secrets from"
say "docs/ci-signing.md and push a tag:"
step "git tag v0.1.0 && git push github v0.1.0"
