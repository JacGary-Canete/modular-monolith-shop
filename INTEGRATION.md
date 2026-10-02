# INTEGRATION.md: LegacySupply

## Product mapping

| My product | My name | SupplierSku | PackSize |
|---|---|---|---|
| P100 | Wireless Mouse | CTT-5425 | 24 |
| P200 | Mechanical Keyboard | CTT-2971 | 24 |
| P300 | USB-C Hub | CTT-2181 | 24 |

## Sessions

POST /auth/token returns a SessionToken, sent as X-LS-Session on every other call. The manual gives no lifetime, so I measured it: polling the catalog every 30 seconds, the session was still accepted at 90 seconds and rejected at 120. It lasts between 90 and 120 seconds. The adapter treats a session as stale after 60 seconds and signs in again before using it, and it also signs in again if LegacySupply answers 401.

## Errors seen

| Code | HTTP | What actually caused it |
|---|---|---|
| E-AUTH-01 | 401 | The API key was sent as an empty string because LS_API_KEY wasn't set in the terminal. |
| (session not valid) | 401 | The session expired between the catalog call and the order call. |
| E-SYS-50 | 503 | Intermittent server-side failure during the instructor's outage windows. It hit sign-in, order POSTs and status polls. |
| (timeout) | none | HttpTimeoutException after the 3-second client timeout while the service was slow. |

## Qty and Uom

Qty is a count in LegacySupply's unit of measure, not a count of items. The acknowledgement returns Uom = CS (cases). Example: I need 47 mice and the PackSize is 24, so I order ceil(47 / 24) = 2 cases, and 48 mice arrive. My units column stores 48, the units that actually arrive, and that is the amount I restock.

## Unexpected statuses

The manual defines only StatusCode 10 (Accepted), 20 (Picking), 30 (Shipped) and 40 (Delivered). PO-100298 came back as 90, which is undocumented. I don't guess a meaning for unknown codes. Any unrecognized code, or a 404 on a PO I placed, sets the order to NEEDS_REVIEW. Polling stops, nothing is restocked, and the row stays visible in supplier_orders for a person to look at. Stock that will never arrive is therefore not counted.

## Known gap

The manual test order TEST-001 (PO-100006) was placed with curl during contract discovery and had no X-Request-Id. That is why LegacySupply's check shows 11 of 12 order requests with the header. Every order sent by the adapter includes it.
