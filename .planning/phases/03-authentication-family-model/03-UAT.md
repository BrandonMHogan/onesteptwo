---
status: complete
phase: 03-authentication-family-model
source: [03-01-SUMMARY.md, 03-02-SUMMARY.md, 03-03-SUMMARY.md, 03-04-SUMMARY.md, 03-05-SUMMARY.md, 03-06-SUMMARY.md]
started: 2026-06-29T23:45:00Z
updated: 2026-06-29T23:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. iOS Sign In (email + password)
expected: Open the iOS app in Simulator. You should see the Sign In screen with "Sign in" title, email field, password field with eye toggle, and a "Sign in" button. Enter valid credentials and tap Sign in. The button should show a spinner while in-flight, then route you to either the "Welcome / Signed in successfully" stub (single org) or the org picker (multi-org).
result: pass

### 2. iOS Sign In - email OTP flow (needsClientTrust)
expected: If Clerk requires email verification for a new device (needsClientTrust status), after tapping Sign in the screen should switch to a "Check your email" view showing your email address, a 6-digit code field, a Verify button, and a Resend code link. Enter the code and tap Verify to complete sign-in.
result: pass

### 3. iOS Single-org routing
expected: With a single-org account, after sign in you land on the post-auth stub showing "Welcome" and "Signed in successfully" text. The Sign out button is visible. No org picker appears.
result: pass

### 4. iOS Multi-org routing - OrgPickerView
expected: With a 2+ org account (no active org set), after sign in you see OrgPickerView listing your families. Selecting a family and tapping Continue activates that org and takes you to the post-auth stub.
result: pass

### 5. iOS Admin - Invite Caregiver
expected: Signed in as an org:admin, the post-auth stub shows an "Invite a caregiver" button. Tapping it opens InviteCaregiverView. Enter a valid email and send — you should see a success confirmation.
result: pass

### 6. iOS Caregiver - Invite action hidden
expected: Signed in as an org:caregiver (not admin), the post-auth stub does NOT show an "Invite a caregiver" button — only "Welcome" text and the Sign out button.
result: pass

### 7. iOS Sign Out
expected: Tapping "Sign out" on the post-auth stub signs you out and returns the app to the Sign In screen (clerk.session becomes nil → ContentView routes to SignInView).
result: pass

## Summary

total: 7
passed: 7
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
