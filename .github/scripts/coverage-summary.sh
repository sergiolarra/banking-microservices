#!/bin/sh
# Prints a Markdown coverage row for a service from its JaCoCo CSV report.
# Usage: coverage-summary.sh <service-dir>
set -eu

service="$1"
csv="$service/target/site/jacoco/jacoco.csv"

if [ ! -f "$csv" ]; then
  echo "| \`$service\` | no report | no report |"
  exit 0
fi

# CSV columns: 6 BRANCH_MISSED, 7 BRANCH_COVERED, 8 LINE_MISSED, 9 LINE_COVERED
awk -F, -v service="$service" '
  NR > 1 { bm += $6; bc += $7; lm += $8; lc += $9 }
  function pct(covered, missed) {
    total = covered + missed
    return total == 0 ? "n/a (no code yet)" : sprintf("%.1f %% (%d/%d)", 100 * covered / total, covered, total)
  }
  END { printf "| `%s` | %s | %s |\n", service, pct(lc, lm), pct(bc, bm) }
' "$csv"
