#!/usr/bin/env python3
"""
Converte JSON do ZAP para CSV agregado.
Uso: python zap_json_to_csv.py zap-report.json > zap_results.csv
"""

import sys
import json
import csv

def main():
    if len(sys.argv) < 2:
        print("Uso: python zap_json_to_csv.py zap-report.json", file=sys.stderr)
        sys.exit(1)

    with open(sys.argv[1], "r", encoding="utf-8") as f:
        data = json.load(f)

    writer = csv.writer(sys.stdout)
    writer.writerow(["risk", "confidence", "name", "instances", "cwe_id"])

    total = 0
    for site in data.get("site", []):
        for alert in site.get("alerts", []):
            instances = len(alert.get("instances", []))
            total += instances
            writer.writerow([
                alert.get("riskdesc", "").split(" ")[0],
                alert.get("confidence", ""),
                alert.get("alert", ""),
                instances,
                alert.get("cweid", "")
            ])

    writer.writerow(["TOTAL", "", "", total, ""])

if __name__ == "__main__":
    main()