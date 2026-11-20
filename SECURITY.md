# Security policy

MediDrop handles account, prescription and order data. Please report suspected
vulnerabilities privately rather than opening a public issue.

## Reporting

Email **officialarghya29@gmail.com** with:

- the affected endpoint or component;
- reproduction steps;
- impact and any preconditions;
- a safe contact address for follow-up.

Do not include real patient data, live credentials or third-party personal data.
Use test accounts and redacted screenshots. Please allow a reasonable window to
investigate and ship a fix before public disclosure.

## Scope

In scope: authentication and session handling, tenant isolation, prescription
file access, order and payment state transitions, inventory overselling, delivery
location exposure, and secrets committed to the repository.

Out of scope: denial-of-service volume tests against public infrastructure,
social engineering, and findings that require a compromised admin account.

## Supported version

Security fixes are applied to the latest published release. `0.1.x` is a
foundation line, not a production-certified medical service. There is no bug
bounty or guaranteed response SLA.
