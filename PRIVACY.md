# Privacy (high-level)

This application is delivered as **on‑premises software** deployed into the **customer’s infrastructure** (e.g. customer server, customer database).

This document is intentionally short and does not describe internal business logic.

## Roles (GDPR/RODO)
- The **Customer** is typically the **Data Controller (Administrator danych)** for any personal data processed using the application.
- The **Vendor** is the **software supplier**. The Vendor does not host the Customer’s environment by default.

## Where data is processed
- Personal data is processed and stored **within the Customer’s environment** (application server + customer database).
- The Customer is responsible for access control, retention, backups, and legal basis for processing.

## Data transfer outside the Customer environment
- By default, the application is intended to operate **without sending personal data to external vendor systems**.
- Any external connectivity depends on the Customer’s deployment and configuration (network access, proxies, monitoring, backups, etc.).

## Security & access
- The Customer should restrict network access to the application and database to authorized users only.
- The Customer should run the application under a dedicated service account with least-privilege permissions.

## Support
- If the Customer requests support that involves access to logs or data exports, the scope and method should be agreed in advance and limited to what is necessary.

