#!/usr/bin/env bash
# =============================================================================
#  Movie Backend - setup and run (macOS and Linux)
#
#  Run it from a terminal in the project folder:
#     ./setup-and-run.sh            shows a menu
#     ./setup-and-run.sh docker     setup + start with Docker (recommended)
#     ./setup-and-run.sh local      start without Docker (Java 17, H2 database)
#     ./setup-and-run.sh stop       stop the Docker containers
#     ./setup-and-run.sh logs       show the backend log
#     ./setup-and-run.sh open       open Swagger UI and the admin portal
#
#  Windows users: use setup-and-run.bat instead.
# =============================================================================

cd "$(dirname "$0")" || exit 1

say() { printf '%s\n' "$*"; }
err() { printf '\n[ERROR] %s\n' "$*" >&2; }

# ----------------------------------------------------------------------------
#  Helpers
# ----------------------------------------------------------------------------

# Docker must be installed and running.
check_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    err "Docker was not found. Install Docker Desktop from https://www.docker.com/products/docker-desktop and run this script again."
    return 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    err '"docker compose" is not available. Update Docker Desktop to a recent version.'
    return 1
  fi
  if ! docker info >/dev/null 2>&1; then
    err "Docker is installed but not running. Start Docker Desktop, wait until it is running, then run this script again."
    return 1
  fi
}

# Print the value of KEY from .env (empty if missing).
env_value() {
  grep -E "^$1=" .env 2>/dev/null | head -n 1 | cut -d= -f2- | tr -d '\r'
}

# Replace the line "KEY=..." in .env with "KEY=VALUE".
set_env_value() {
  local tmp
  tmp="$(mktemp)" || return 1
  K="$1" V="$2" awk 'BEGIN { k = ENVIRON["K"]; v = ENVIRON["V"] }
                     index($0, k "=") == 1 { print k "=" v; next }
                     { print }' .env > "$tmp" && cat "$tmp" > .env
  rm -f "$tmp"
}

# Create .env from .env.example the first time and ask for the TMDb key.
ensure_env() {
  [ -f .env ] && return 0
  cp .env.example .env || { err "Could not create .env from .env.example."; return 1; }
  say ""
  say "Created the .env settings file from .env.example."
  say "A TMDb API key gives you real movies. Press Enter to skip and use 25 sample movies."
  say "(How to get a key: see section 2 of README.md)"
  local key=""
  read -r -p "TMDb API key (v3): " key || true
  key="$(printf '%s' "$key" | tr -d '[:space:]')"
  if [ -n "$key" ]; then
    set_env_value TMDB_API_KEY "$key"
    say "TMDb key saved in .env"
  fi
}

# Print the first Firebase service-account file in "secrets" (nothing if none).
first_firebase_file() {
  local f
  for f in secrets/*.json; do
    if [ -e "$f" ]; then
      printf '%s\n' "$f"
      return 0
    fi
  done
  return 0
}

# Docker: use the first Firebase file in "secrets" if the path in .env is empty.
set_firebase() {
  local file
  file="$(first_firebase_file)"
  if [ -z "$file" ]; then
    say 'No Firebase file in the "secrets" folder, so push notifications will only be logged.'
    return 0
  fi
  if [ -z "$(env_value FIREBASE_CREDENTIALS_PATH)" ]; then
    set_env_value FIREBASE_CREDENTIALS_PATH "/secrets/$(basename "$file")"
  fi
  say "Firebase file: $file (push notifications will be sent)"
}

# Print API_PORT from .env (default 8080).
api_port() {
  local port
  port="$(env_value API_PORT)"
  printf '%s\n' "${port:-8080}"
}

# Export every KEY=VALUE line of .env into this shell.
load_env() {
  local line key value
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in
      ''|'#'*) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      ''|*[!A-Za-z0-9_]*) continue ;;
    esac
    export "$key=$value"
  done < .env
}

# Open a URL in the browser (macOS: open, Linux: xdg-open).
open_url() {
  if command -v open >/dev/null 2>&1; then
    open "$1" >/dev/null 2>&1
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$1" >/dev/null 2>&1
  else
    say "Open this in your browser: $1"
  fi
}

# Wait until /actuator/health says UP (up to about 2 minutes).
wait_health() {
  say "Waiting for the backend to start..."
  local i=0
  while [ "$i" -lt 40 ]; do
    if curl -fsS "http://localhost:$1/actuator/health" 2>/dev/null | grep -q '"UP"'; then
      return 0
    fi
    i=$((i + 1))
    sleep 3
  done
  return 1
}

show_urls() {
  say ""
  say "===================== The backend is running ====================="
  say "  Swagger UI    http://localhost:$1/swagger-ui.html"
  say "  API           http://localhost:$1/api/movies"
  say "  Admin portal  http://localhost:$1/admin/movies   (admin / admin123)"
  say "  Android app   http://10.0.2.2:$1/api/   (from the emulator)"
  say ""
  say "  Stop it:  ./setup-and-run.sh stop"
  say "  Log:      ./setup-and-run.sh logs"
  say "=================================================================="
}

# ----------------------------------------------------------------------------
#  Option 1: Docker
# ----------------------------------------------------------------------------
run_docker() {
  check_docker || return 1
  ensure_env || return 1
  set_firebase
  local port
  port="$(api_port)"
  say ""
  say "Building and starting the backend. The first run downloads images and can take a few minutes..."
  if ! docker compose up --build -d; then
    err '"docker compose up" failed. Read the messages above.'
    say 'If you see "port is already allocated", set API_PORT or DB_PORT in the .env file (for example API_PORT=8081) and run this script again.'
    return 1
  fi
  if ! wait_health "$port"; then
    err "The backend did not become healthy in time. Run ./setup-and-run.sh logs to see why."
    return 1
  fi
  show_urls "$port"
  open_url "http://localhost:$port/swagger-ui.html"
}

# ----------------------------------------------------------------------------
#  Option 2: without Docker
# ----------------------------------------------------------------------------
run_local() {
  if ! command -v java >/dev/null 2>&1; then
    err "Java was not found. Install Java 17 (for example from https://adoptium.net) and run this script again."
    return 1
  fi
  ensure_env || return 1
  load_env

  # The Firebase path in .env is a path inside Docker, so use the real file here (or none).
  local file
  file="$(first_firebase_file)"
  if [ -n "$file" ]; then
    FIREBASE_CREDENTIALS_PATH="$PWD/$file"
  else
    FIREBASE_CREDENTIALS_PATH=""
  fi
  export FIREBASE_CREDENTIALS_PATH

  local port="${API_PORT:-8080}"
  say ""
  say "Starting the backend with the local profile on port $port. Press Ctrl+C to stop it."
  say "Swagger UI will open in your browser in about 40 seconds."
  ( sleep 40; open_url "http://localhost:$port/swagger-ui.html" ) &
  local opener=$!
  trap 'kill "$opener" 2>/dev/null' EXIT

  if [ -x ./gradlew ]; then
    ./gradlew bootRun --args="--spring.profiles.active=local --server.port=$port"
  else
    sh ./gradlew bootRun --args="--spring.profiles.active=local --server.port=$port"
  fi
}

# ----------------------------------------------------------------------------
#  Options 3, 4 and 5
# ----------------------------------------------------------------------------
run_stop() {
  check_docker || return 1
  docker compose down
}

run_logs() {
  check_docker || return 1
  docker compose logs -f api
}

run_open() {
  local port
  port="$(api_port)"
  open_url "http://localhost:$port/swagger-ui.html"
  open_url "http://localhost:$port/admin/movies"
}

# ----------------------------------------------------------------------------
#  Menu and entry point
# ----------------------------------------------------------------------------
menu() {
  while true; do
    say ""
    say " ===== Movie Backend ====="
    say "  1. Setup and start with Docker (recommended)"
    say "  2. Start without Docker (needs Java 17, uses an H2 file database)"
    say "  3. Stop the Docker containers"
    say "  4. Show the backend log"
    say "  5. Open Swagger UI and the admin portal in the browser"
    say "  6. Exit"
    say ""
    local choice=""
    read -r -p "Choose 1-6: " choice || return 0
    case "$choice" in
      1) run_docker; return $? ;;
      2) run_local;  return $? ;;
      3) run_stop;   return $? ;;
      4) run_logs;   return $? ;;
      5) run_open;   return $? ;;
      6) return 0 ;;
    esac
  done
}

main() {
  case "${1:-}" in
    docker) run_docker ;;
    local)  run_local ;;
    stop)   run_stop ;;
    logs)   run_logs ;;
    open)   run_open ;;
    "")     menu ;;
    *)      say "Unknown option: $1"; say "Use: docker, local, stop, logs or open (or no option for the menu)."; return 1 ;;
  esac
}

# Run main only when executed, not when this file is sourced (for tests).
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
