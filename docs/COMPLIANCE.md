# Compliance register

Last updated: 2026-11-20

Status: **Engineering notes only. This is not legal advice and is not a compliance certification.**

Every item below is marked **NEEDS LEGAL VERIFICATION** unless external counsel has signed it.
MediDrop must not claim to be “launch-ready”, “compliant” or legally cleared based on this file.

## 1. Register

| ID | Area | Requirement to verify | Current product state | Status |
| --- | --- | --- | --- | --- |
| C-01 | Online pharmacy / e-pharmacy rules | Whether the platform may facilitate online sale and delivery of medicines in West Bengal/India; registration, display and record-keeping duties | No legal review; no licence registration workflow beyond pharmacy documents | **NEEDS LEGAL VERIFICATION** |
| C-02 | Drugs and Cosmetics Act/Rules | Sale by licensed pharmacy, qualified person, premises, storage, record retention | Pharmacy licence field and manual admin approval; no qualified-person verification | **NEEDS LEGAL VERIFICATION** |
| C-03 | Schedule H / H1 / X | Prescription requirements, retention, restricted products, narcotics and prohibited online sale | Boolean `prescription_required`; no H/H1/X enforcement; no restricted list | **NEEDS LEGAL VERIFICATION** |
| C-04 | Registered pharmacist | Only a registered pharmacist may review/dispense Rx; record identity and registration | M1 adds verified pharmacist name, registration and council fields plus an admin verification endpoint; review records the registration. No separate `PHARMACIST` login role yet | **NEEDS LEGAL VERIFICATION / PARTIAL** |
| C-05 | Prescription validity and retention | What constitutes a valid prescription; retention period; audit access; no reuse | Private file storage, review row; no retention schedule or legal validity rules | **NEEDS LEGAL VERIFICATION** |
| C-06 | Substitution | Pharmacist-initiated substitution only, with recorded customer consent; no customer-initiated substitution | No substitution flow or consent record | **NEEDS LEGAL VERIFICATION / GAP** |
| C-07 | Restricted products | Enforce a legally reviewed blocked list (narcotics, Schedule X, products not legally online) | No blocked-product model; all active medicines can be listed | **NEEDS LEGAL VERIFICATION / GAP** |
| C-08 | Medical advice | No diagnosis, dosage recommendation or medical advice; safety disclaimers | Marketing copy says no advice; no prompt/chat feature; no automated dosage advice | **NEEDS LEGAL VERIFICATION** |
| C-09 | DPDP Act / privacy | Notice, consent, purpose limitation, access/correction/deletion, breach process, retention | Deletion request sets a flag and disables account; no deletion execution, export or consent ledger | **NEEDS LEGAL VERIFICATION / GAP** |
| C-10 | Sensitive health data | Lawful basis, encryption, access minimisation, vendor contracts | Private local files; no at-rest encryption evidence; no retention/erasure automation | **NEEDS LEGAL VERIFICATION** |
| C-11 | Consumer Protection (E-Commerce) Rules | Seller identity, grievance officer, complaints, refund/return policy, display | No published legal pages, grievance officer or formal dispute workflow | **NEEDS LEGAL VERIFICATION / GAP** |
| C-12 | Returns and refunds | Medicines generally non-returnable; damaged/wrong/near-expiry dispute rules | Cancellation/refund exists; no returns/dispute/near-expiry workflow | **NEEDS LEGAL VERIFICATION / GAP** |
| C-13 | Payments / PCI | Use a PCI-compliant gateway; do not store card data; webhook integrity | Razorpay seam; sandbox; no card data stored; live certification pending | **NEEDS LEGAL / PROVIDER VERIFICATION** |
| C-14 | COD | Permitted COD rules, reconciliation, refunds, failed-delivery handling | COD flows; no legal/operational COD policy | **NEEDS LEGAL VERIFICATION** |
| C-15 | GST invoicing | GSTIN, HSN/SAC, tax invoice sequence, pharmacy seller details, retention | Invoice has GSTIN/licence/tax; no HSN, invoice number series or server PDF | **NEEDS LEGAL VERIFICATION / GAP** |
| C-16 | Price control / MRP | Selling price must not exceed legal MRP; NPPA ceilings where applicable | Service rejects price > MRP; no DB constraint; no NPPA import | **NEEDS LEGAL VERIFICATION / GAP** |
| C-17 | Advertising and claims | No unverified medical claims, testimonials or guarantees | UI makes “licensed/verified” claims; some seed data is demo and not flagged on every entity | **NEEDS LEGAL VERIFICATION** |
| C-18 | Data localisation / cross-border | Where health/payment data may be stored/processed | Single local deployment; no vendor/data-flow map | **NEEDS LEGAL VERIFICATION** |
| C-19 | Record retention | Prescription, order, payment, audit and invoice retention periods | No retention schedule; append-only convention only | **NEEDS LEGAL VERIFICATION** |
| C-20 | Breach response | Detection, notification, affected-person communication | No incident process, monitoring or breach playbook | **NEEDS LEGAL VERIFICATION / GAP** |
| C-21 | Accessibility | WCAG AA / applicable accessibility expectations | 25 axe journeys and Lighthouse CI pass automated A/AA/accessibility checks; no manual WCAG, keyboard or screen-reader audit | **NEEDS PRODUCT/LEGAL REVIEW** |
| C-22 | Language | English/Hindi/Bengali availability and clear consent text | English-only UI | **NEEDS PRODUCT/LEGAL REVIEW** |
| C-23 | Telemedicine / remote care | Whether any future consultation feature triggers telemedicine rules | No consultation feature; do not add one without review | **NEEDS LEGAL VERIFICATION** |
| C-24 | Grievance / escalation | Named grievance officer, response timelines, escalation | No published officer or SLA | **NEEDS LEGAL VERIFICATION / GAP** |
| C-25 | Advertising and delivery-time claims | No unverified “10-minute” or guaranteed-time claims; drug advertising restrictions; counsel review of “Verified. Fast. Affordable.” | No 10-minute wording found; ETA is a point estimate, not a range; no counsel review | **NEEDS LEGAL VERIFICATION** |
| C-26 | Rider welfare and insurance | Gig-worker welfare, insurance, no punitive timers, safety pause, working-hour limits | No punitive timer found; no rider insurance, safety pause, shift cap or batching policy | **NEEDS LEGAL VERIFICATION / GAP** |
| C-27 | Cold-chain storage and transport | Temperature-controlled storage/transport for flagged products; audit trail | No `requires_cold_chain`, cold-chain node capability or insulated-bag flow | **NEEDS LEGAL VERIFICATION / GAP** |
| C-28 | Feature flags and compliance sign-off | RX/substitution/Express/cold-chain/vault cannot be enabled without recorded sign-off; kill switches | No feature flags, `ComplianceSignoff`, per-node/global kill switch or production refusal | **NEEDS LEGAL / ENGINEERING VERIFICATION** |
| C-29 | Staged pilot | Stage 0 demo, Stage 1 OTC-only closed beta, Stage 2 Rx/substitution only after pharmacist and legal sign-off | No DEMO_MODE, permanent banner or stage gate; RX flow is always available | **NEEDS LEGAL / OPERATIONAL VERIFICATION** |
| C-30 | Licensed fulfilment premises | Every node must be a licensed pharmacy premises; no unlicensed dark stores | Pharmacy licence field and manual approval; no node-capacity/cold-chain verification | **NEEDS LEGAL VERIFICATION** |

## 2. Current hard blockers

The following are engineering-visible blockers, independent of the final legal advice:

1. **No verified pharmacist identity.** The pharmacy owner account is treated as the approver.
2. **No restricted/blocked product list.** Any active catalogue item can be sold if stock exists.
3. **No H/H1/X enforcement.** A boolean is not a legal schedule model.
4. **No returns/damage/near-expiry dispute flow.**
5. **No executable data-deletion process.**
6. **No documented retention schedule or breach playbook.**
7. **No GST HSN/invoice sequence or server-generated invoice.**
8. **No published legal pages, grievance officer or consumer complaint route.**
9. **No consent ledger or notification quiet hours.**
10. **No live payment/provider certification.**
11. **No DEMO_MODE, feature flags or ComplianceSignoff.** RX/substitution/Express cannot be safely
    staged or killed in code.
12. **No rider welfare controls.** No insurance, safety pause, shift cap or batching policy.
13. **No cold-chain capability or temperature audit.**
14. **No licensed-node premise verification beyond a licence number field.**

## 3. Addendum v5 pilot gates

Before Stage 0/1, engineering must provide and counsel must review:

- `DEMO_MODE` with a permanent banner and refusal to start in the production profile.
- DB-backed `EXPRESS_ENABLED`, `RX_ENABLED`, `SUBSTITUTION_ENABLED`, `COLD_CHAIN_ENABLED` and
  `VAULT_ENABLED` flags, fail-closed by default.
- A `ComplianceSignoff` record (who, when, document reference) required before RX or substitution
  can be enabled.
- Per-node and global kill switches, with an audit trail.
- A Stage 1 scope limited to OTC/wellness/personal care, with RX and substitution off.

## 4. What must be reviewed by a lawyer before any real sale

- Online pharmacy licensing and permissible delivery model in West Bengal.
- Schedule H/H1/X handling, prescription retention and record formats.
- Registered pharmacist verification and dispensing authority.
- Restricted/prohibited products and any narcotics/psychotropic rules.
- DPDP notice, consent, deletion, breach and retention.
- Consumer-protection disclosures, grievance officer and refund/return policy.
- GST invoice content, HSN/SAC and numbering.
- Payment/COD terms and refund timelines.
- Any future telemedicine, AI advice or substitution feature.
- Advertising and delivery-time claims, including the absence of a “10-minute” guarantee.
- Rider welfare, insurance, shift caps and safety pause.
- Cold-chain storage/transport and temperature record retention.
- Licensed premises for every fulfilment node.

## 5. Engineering position

The correct engineering position is:

> “MediDrop is a working development platform with a tested core. It is not legally cleared,
> not production-certified, and not authorized to sell prescription medicines until the
> P0 clinical/security gaps and the legal review above are closed.”

No UI copy, README sentence or sales claim should contradict that position.
