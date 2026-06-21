#!/bin/bash
# =============================================================================
# sync-users-to-keycloak.sh
#
# Reads users from scraping_db (PostgreSQL) and creates them in Keycloak.
# The email is used as the link between the two systems.
#
# Prerequisites:
#   - jq installed (sudo apt install jq)
#   - PostgreSQL running on localhost:5432 with scraping_db
#   - Keycloak running on localhost:8180
#
# Usage:
#   chmod +x sync-users-to-keycloak.sh
#   ./sync-users-to-keycloak.sh
#
# Optional: set a default password for all synced users
#   DEFAULT_PASSWORD=changeme ./sync-users-to-keycloak.sh
# =============================================================================

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-scraping_db}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-postgres}"

KC_URL="${KC_URL:-http://localhost:8180}"
KC_REALM="${KC_REALM:-elyadata}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"

DEFAULT_PASSWORD="${DEFAULT_PASSWORD:-Welcome123!}"

# ── Colors ────────────────────────────────────────────────────────────────────

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Sync users: scraping_db → Keycloak                      ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""

# ── Step 1: Get Keycloak admin token ──────────────────────────────────────────

echo -e "${YELLOW}[1/4]${NC} Getting Keycloak admin token..."

TOKEN=$(curl -s -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=${KC_ADMIN_USER}" \
  -d "password=${KC_ADMIN_PASS}" \
  -d "grant_type=password" | jq -r '.access_token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo -e "${RED}✗ Failed to get Keycloak admin token. Is Keycloak running?${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Token obtained${NC}"

# ── Step 2: Read users from PostgreSQL ────────────────────────────────────────

echo -e "${YELLOW}[2/4]${NC} Reading users from scraping_db..."

USERS_JSON=$(PGPASSWORD="${DB_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -F'|' -c "
  SELECT id, username, email, role, enabled
  FROM users
  ORDER BY created_at;
")

if [ -z "$USERS_JSON" ]; then
  echo -e "${YELLOW}⚠ No users found in scraping_db${NC}"
  exit 0
fi

USER_COUNT=$(echo "$USERS_JSON" | wc -l)
echo -e "${GREEN}✓ Found ${USER_COUNT} user(s)${NC}"
echo ""

# ── Step 3: Get existing Keycloak users ───────────────────────────────────────

echo -e "${YELLOW}[3/4]${NC} Fetching existing Keycloak users..."

KC_USERS=$(curl -s -X GET "${KC_URL}/admin/realms/${KC_REALM}/users?max=1000" \
  -H "Authorization: Bearer ${TOKEN}")

KC_EMAILS=$(echo "$KC_USERS" | jq -r '.[].email // empty' | tr '[:upper:]' '[:lower:]')

echo -e "${GREEN}✓ Fetched existing users${NC}"
echo ""

# ── Step 4: Create missing users in Keycloak ──────────────────────────────────

echo -e "${YELLOW}[4/4]${NC} Syncing users..."
echo ""

CREATED=0
SKIPPED=0
FAILED=0

while IFS='|' read -r DB_ID DB_USERNAME DB_EMAIL DB_ROLE DB_ENABLED; do
  # Skip empty lines
  [ -z "$DB_EMAIL" ] && continue

  # Normalize email
  DB_EMAIL_LOWER=$(echo "$DB_EMAIL" | tr '[:upper:]' '[:lower:]')

  # Check if user already exists in Keycloak
  if echo "$KC_EMAILS" | grep -qi "^${DB_EMAIL_LOWER}$"; then
    echo -e "  ${YELLOW}⊘${NC} ${DB_USERNAME} (${DB_EMAIL}) — already in Keycloak, skipping"

    # Still assign role if needed
    KC_USER_ID=$(echo "$KC_USERS" | jq -r --arg email "$DB_EMAIL_LOWER" \
      '.[] | select(.email | ascii_downcase == $email) | .id')

    if [ -n "$KC_USER_ID" ] && [ -n "$DB_ROLE" ]; then
      assign_role "$KC_USER_ID" "$DB_ROLE" 2>/dev/null || true
    fi

    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  # Determine if user should be enabled
  KC_ENABLED="true"
  if [ "$DB_ENABLED" = "f" ] || [ "$DB_ENABLED" = "false" ]; then
    KC_ENABLED="false"
  fi

  # Create user in Keycloak
  HTTP_CODE=$(curl -s -o /tmp/kc_response.json -w "%{http_code}" \
    -X POST "${KC_URL}/admin/realms/${KC_REALM}/users" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${DB_USERNAME}\",
      \"email\": \"${DB_EMAIL}\",
      \"enabled\": ${KC_ENABLED},
      \"emailVerified\": true,
      \"credentials\": [{
        \"type\": \"password\",
        \"value\": \"${DEFAULT_PASSWORD}\",
        \"temporary\": false
      }]
    }")

  if [ "$HTTP_CODE" = "201" ]; then
    echo -e "  ${GREEN}✓${NC} ${DB_USERNAME} (${DB_EMAIL}) — created with role ${DB_ROLE:-READER}"

    # Get the new user's Keycloak ID
    NEW_KC_USER_ID=$(curl -s -X GET \
      "${KC_URL}/admin/realms/${KC_REALM}/users?email=${DB_EMAIL}&exact=true" \
      -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id')

    # Assign realm role
    if [ -n "$NEW_KC_USER_ID" ] && [ "$NEW_KC_USER_ID" != "null" ] && [ -n "$DB_ROLE" ]; then
      # Get the role representation
      ROLE_JSON=$(curl -s -X GET \
        "${KC_URL}/admin/realms/${KC_REALM}/roles/${DB_ROLE}" \
        -H "Authorization: Bearer ${TOKEN}")

      ROLE_ID=$(echo "$ROLE_JSON" | jq -r '.id')

      if [ -n "$ROLE_ID" ] && [ "$ROLE_ID" != "null" ]; then
        curl -s -X POST \
          "${KC_URL}/admin/realms/${KC_REALM}/users/${NEW_KC_USER_ID}/role-mappings/realm" \
          -H "Authorization: Bearer ${TOKEN}" \
          -H "Content-Type: application/json" \
          -d "[${ROLE_JSON}]" > /dev/null

        echo -e "       → Role ${BLUE}${DB_ROLE}${NC} assigned"
      fi
    fi

    CREATED=$((CREATED + 1))
  elif [ "$HTTP_CODE" = "409" ]; then
    echo -e "  ${YELLOW}⊘${NC} ${DB_USERNAME} (${DB_EMAIL}) — already exists (conflict)"
    SKIPPED=$((SKIPPED + 1))
  else
    echo -e "  ${RED}✗${NC} ${DB_USERNAME} (${DB_EMAIL}) — failed (HTTP ${HTTP_CODE})"
    cat /tmp/kc_response.json 2>/dev/null
    echo ""
    FAILED=$((FAILED + 1))
  fi

done <<< "$USERS_JSON"

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}Created:${NC} ${CREATED}"
echo -e "  ${YELLOW}Skipped:${NC} ${SKIPPED} (already in Keycloak)"
echo -e "  ${RED}Failed:${NC}  ${FAILED}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo ""

if [ "$CREATED" -gt 0 ]; then
  echo -e "${YELLOW}⚠ Default password for new users: ${DEFAULT_PASSWORD}${NC}"
  echo -e "  Users should change their password after first login."
fi

rm -f /tmp/kc_response.json