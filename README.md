# parkease
Smart parking garage management system for attendants

## Rate card import

Authenticated attendants can import cleaned rate cards with `POST /api/rates/import`.
Send plain text with one CSV record per line in this format:

```text
spotType,firstHourRate,additionalHourRate,dailyCap
COMPACT,50,30,200
STANDARD,60,35,250
EV,70,40,300
```

Fields are trimmed and spot types are case-insensitive. Blank lines, comments beginning
with `#`, and the header are ignored. Each import replaces the active rate for each
spot type included; completed parking sessions retain the rates used at checkout.
